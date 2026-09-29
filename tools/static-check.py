#!/usr/bin/env python3
"""Проверки без JDK: работают там, где нет Java и Gradle.

Скрипт не заменяет тесты. Он ловит класс ошибок, который не требует запуска игры:
рассинхрон версий, незарегистрированные миксины, неверные данные зачарования,
битые JSON, ссылки на несуществующие файлы, попадание тестового кода в мод.

Запуск:  python3 tools/static-check.py
Код возврата: 0 — всё в порядке, 1 — есть ошибки.
"""
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
problems = []
notes = []


def fail(message):
    problems.append(message)


def guard(section, action):
    """Выполняет раздел проверок, превращая неожиданную ошибку в понятное сообщение."""
    try:
        action()
    except Exception as exc:  # noqa: BLE001 — намеренно: сообщение важнее стека
        fail("раздел %s упал с ошибкой: %r (это баг самой проверки, а не проекта)" % (section, exc))


def ok(message):
    print("  OK   " + message)


def note(message):
    notes.append(message)
    print("  ИНФО " + message)


def read(path):
    """Читает файл, возвращая пустую строку, если его нет.

    Так проверка сообщает понятную ошибку вместо трейсбека, когда файл ещё не
    добавлен в репозиторий (типичная ситуация при добавлении файлов по одному).
    """
    try:
        return (ROOT / path).read_text(encoding="utf-8", errors="replace")
    except FileNotFoundError:
        return ""


# ---------------------------------------------------------------- 1. Версии
print("[1] Согласованность версий")
props_text = read("gradle.properties")
if not props_text:
    fail("в репозитории нет gradle.properties")
props = dict(
    line.split("=", 1)
    for line in props_text.splitlines()
    if "=" in line and not line.strip().startswith("#")
)
version = props.get("mod_version", "").strip()
mod_id = props.get("mod_id", "").strip()
if not version:
    fail("в gradle.properties нет mod_version")
else:
    ok("gradle.properties: %s = %s" % (mod_id, version))

changelogs = sorted(ROOT.glob("CHANGELOG-*.md"))
if changelogs:
    latest = re.search(r"CHANGELOG-(\d+\.\d+\.\d+)\.md", changelogs[-1].name)
    if latest and latest.group(1) != version:
        fail("последний CHANGELOG %s не совпадает с версией %s" % (latest.group(1), version))
    else:
        ok("есть CHANGELOG для версии %s" % version)

jar_dirs = [ROOT / name for name in ("dist", "release")]
jars = [j for folder in jar_dirs if folder.is_dir() for j in sorted(folder.glob("*.jar"))]
if not jars:
    note("папки с собранным JAR нет в этой копии (в релизном архиве она есть: dist/ или release/)")
else:
    for jar in jars:
        if jar.name != "settledlands-%s.jar" % version:
            fail("имя %s не совпадает с версией %s" % (jar.name, version))
        else:
            ok("%s" % jar.relative_to(ROOT))
        with zipfile.ZipFile(jar) as z:
            names = z.namelist()
            toml = z.read("META-INF/neoforge.mods.toml").decode()
            found = re.search(r'version="([^"]+)"', toml)
            if not found or found.group(1) != version:
                fail("внутри %s версия %s, ожидалась %s" % (jar.name, found and found.group(1), version))
            else:
                ok("внутри JAR версия %s" % found.group(1))
            leaked = [n for n in names if "/smoke/" in n or n.endswith("Test.class")]
            if leaked:
                fail("в релизном JAR есть тестовый код: %s" % leaked[:3])
            else:
                ok("тестовый код в JAR отсутствует")

# ---------------------------------------------------------------- 2. Миксины
print("[2] Регистрация миксинов")
mixins_file = ROOT / "src/main/resources/settledlands.mixins.json"
if not mixins_file.is_file():
    fail("нет settledlands.mixins.json")
else:
    config = json.loads(mixins_file.read_text(encoding="utf-8"))
    declared = set(config.get("mixins", [])) | set(config.get("client", []))
    java_files = {p.stem: p for p in (ROOT / "src/main/java").rglob("*.java")}
    for entry in sorted(declared):
        if entry not in java_files:
            fail("в конфиге указан миксин %s, но файла нет" % entry)
    actual = set()
    for path, stem in ((p, p.stem) for p in (ROOT / "src/main/java").rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        if re.search(r"@Mixin\s*\(", text):
            actual.add(stem)
    missing = sorted(actual - declared)
    if missing:
        fail("миксины не внесены в settledlands.mixins.json: %s" % missing)
    else:
        ok("все %d миксинов зарегистрированы" % len(actual))
    for name, section in (("client", config.get("client", [])),):
        for entry in section:
            text = java_files[entry].read_text(encoding="utf-8") if entry in java_files else ""
            if text and "Dist.CLIENT" not in text and "client." not in text and "model" not in text:
                note("клиентский миксин %s не выглядит клиентским, проверьте" % entry)

# ------------------------------------------------- 3. Данные зачарования
print("[3] Данные зачарования Святости")
ench_path = ROOT / "src/main/resources/data/settledlands/enchantment/sanctity.json"
if not ench_path.is_file():
    fail("нет sanctity.json")
else:
    ench = json.loads(ench_path.read_text(encoding="utf-8"))
    if ench.get("max_level") != 2:
        fail("max_level = %s, а мод рассчитан на 2 уровня" % ench.get("max_level"))
    else:
        ok("max_level = 2 (третьего уровня нет)")
    supported = ench.get("supported_items")
    if supported != "#minecraft:banners":
        fail("supported_items = %s, ожидалось #minecraft:banners" % supported)
    else:
        ok("чары доступны только баннерам")
    min_cost = ench.get("min_cost", {})
    base = min_cost.get("base")
    per = min_cost.get("per_level_above_first")
    if not isinstance(base, int) or not isinstance(per, int):
        fail("min_cost должен содержать base и per_level_above_first")
    else:
        level_two = base + per
        note("сила для уровня I = %d, для уровня II = %d" % (base, level_two))
        if base > 10:
            fail("порог уровня I (%d) выше силы нижних строк стола (2-10): "
                 "чары исчезнут из слабых столов" % base)
        else:
            ok("порог уровня I ниже силы нижних строк стола")
        if level_two > 30:
            fail("порог уровня II (%d) выше максимальной силы верхней строки (30)" % level_two)
        elif level_two < 20:
            note("порог уровня II равен %d: его сможет выдать и средняя библиотека" % level_two)
        else:
            ok("порог уровня II %d достижим верхней строкой полного стола" % level_two)

# ---------------------------------------------------------------- 4. JSON
print("[4] Все ресурсы-json разбираются")
bad = []
for path in (ROOT / "src/main/resources").rglob("*.json"):
    try:
        json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        bad.append("%s: %s" % (path.relative_to(ROOT), exc))
if bad:
    fail("битые JSON: %s" % bad)
else:
    ok("все json корректны")

langs = sorted((ROOT / "src/main/resources/assets").rglob("lang/*.json"))
if len(langs) >= 2:
    keys = {p.name: set(json.loads(p.read_text(encoding="utf-8"))) for p in langs}
    first = next(iter(keys.values()))
    for name, lang_keys in keys.items():
        if lang_keys != first:
            fail("в %s набор ключей отличается от других языков" % name)
    if all(v == first for v in keys.values()):
        ok("языковые файлы совпадают по ключам")

# ---------------------------------------------------------------- 5. Ссылки
print("[5] Ссылки в документации")
for doc in ["README.md", "AI-HANDOFF.md", "GITHUB.md", "VERIFICATION.md"]:
    path = ROOT / doc
    if not path.is_file():
        fail("нет %s" % doc)
        continue
    for target in re.findall(r"\]\(([^)]+)\)", path.read_text(encoding="utf-8")):
        if target.startswith(("http://", "https://", "#", "mailto:")):
            continue
        clean = target.split("#")[0]
        if not clean:
            continue
        # Папку с готовым модом можно называть dist/ или release/ — в живом репозитории
        # используется dist/, локально может быть release/. Считаем их равноценными.
        if clean.startswith(("dist/", "release/")):
            name = clean.split("/", 1)[1]
            if not any((ROOT / folder / name).exists() for folder in ("dist", "release")):
                fail("%s ссылается на отсутствующий %s" % (doc, clean))
            continue
        if not (ROOT / clean).exists():
            fail("%s ссылается на отсутствующий %s" % (doc, clean))
if not problems:
    ok("все внутренние ссылки ведут на существующие файлы")

# ---------------------------------------------------------------- 6. Гигиена
print("[6] Гигиена сборки")
main_sources = "\n".join(p.read_text(encoding="utf-8") for p in (ROOT / "src/main/java").rglob("*.java"))
for forbidden in ["dev.settledlands.smoke", "dev.settledlands.test"]:
    if forbidden in main_sources:
        fail("основной код ссылается на тестовый пакет %s" % forbidden)
build_gradle = read("build.gradle")
if not build_gradle:
    fail("в репозитории нет build.gradle")
for required in ["sanctity-smoke-result.txt", "PASSED"]:
    if required not in build_gradle:
        fail("в сборке нет обязательного элемента: %s" % required)
if "org.gradle.jvmargs" not in read("gradle.properties"):
    note("в gradle.properties не задан org.gradle.jvmargs: память Gradle остаётся по умолчанию")
ignore = read(".gitignore")
if not ignore:
    fail("нет .gitignore: создайте файл, иначе в репозиторий попадут build/, .gradle/ и тестовый мир")
else:
    for pattern, why in (("run-smoke/", "тестовый мир"), (".gradle", "кэш Gradle"), ("build/", "сборка")):
        if pattern not in ignore:
            fail("в .gitignore нет %s (%s)" % (pattern, why))
if "release/" not in ignore:
    ok("папка release/ попадает в репозиторий, как и требуется")
if not (ROOT / ".github/workflows/build.yml").is_file():
    fail("нет .github/workflows/build.yml: автоматические проверки на GitHub не запустятся")
if not (ROOT / ".github/workflows/smoke.yml").is_file():
    note("нет .github/workflows/smoke.yml: серверный набор (117 проверок) не запустится на GitHub")
for script in ["gradlew", "scripts/setup-toolchain.sh", "scripts/verify-all.sh"]:
    path = ROOT / script
    if not path.is_file():
        fail("нет %s" % script)
    elif not (path.stat().st_mode & 0o111):
        note("%s без флага исполняемости: при загрузке через веб-интерфейс GitHub его теряют, "
             "поэтому в workflow есть шаг chmod +x gradlew" % script)
if not problems:
    ok("структура сборки в порядке")

# ---------------------------------------------------------------- Итог
print()
if problems:
    print("СТАТИЧЕСКАЯ ПРОВЕРКА НЕ ПРОЙДЕНА, проблем: %d" % len(problems))
    for problem in problems:
        print("  ОШИБКА " + problem)
    sys.exit(1)
print("СТАТИЧЕСКАЯ ПРОВЕРКА ПРОЙДЕНА. Замечаний: %d." % len(notes))
print("Это не замена тестам: поведение в игре так не проверяется.")
