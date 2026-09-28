# Как залить это на GitHub

## Вариант 1: через сайт (без командной строки)

1. Распакуйте архив `Settled-Lands-repo.zip` в пустую папку.
2. На github.com: **New repository** → имя, например `settled-lands` → Public или Private
   → **Create repository** (без README, он у нас уже есть).
3. На странице репозитория: **uploading an existing file** → перетащите **содержимое**
   папки (все файлы и папки, включая скрытую `.github`) → **Commit changes**.
4. При перетаскивании папок сайт сохранит структуру. Если браузер не берёт скрытую
   `.github`, создайте файл `.github/workflows/build.yml` вручную через **Add file**.

## Вариант 2: через git (удобнее для дальнейших правок)

```bash
cd путь/к/распакованной/папке
git init
git add .
git commit -m "Settled Lands 0.4.3"
git branch -M main
git remote add origin https://github.com/ВАШ_ЛОГИН/settled-lands.git
git push -u origin main
```

## Что уже готово в архиве

- `README.md` — полное описание мода, установка, механика, команды, ограничения.
- `CHANGELOG-0.2.0.md … CHANGELOG-0.4.3.md` — история версий.
- `VERIFICATION.md`, `TESTING.md`, `*-TEST-RESULTS.txt` — что и как проверено.
- `AI-HANDOFF.md` — контекст для следующего ИИ-диалога (и краткая карта кода).
- `docs/ARCHITECTURE-RU.md`, `docs/PLAYTEST-RU.md` — объяснение решений и чек-листы.
- `src/` — исходники, включая `src/test` (unit) и `src/smoke` (интеграционный набор).
- `dist/settledlands-0.4.3.jar` — готовый файл мода.
- `animation-source/defiler.bbmodel` — исходная модель с анимациями.
- `build.gradle`, `gradle.properties`, `gradlew`, `.github/workflows/build.yml` — сборка
  и автоматическая проверка на GitHub Actions при каждом push.

## Совет по релизам

Держите `dist/` в репозитории (он маленький), а на странице **Releases** прикладывайте
тот же JAR и короткое описание изменений — так игрокам удобнее скачивать.

## Продолжение работы в новом ИИ-диалоге

Скопируйте такой текст:

> Продолжаем мод Settled Lands (Minecraft 1.21.1, NeoForge 21.1.252, Java 21).
> Репозиторий приложен, начни с `AI-HANDOFF.md` — там состояние, карта кода и решения.
> Собери проект (`CI=true ./gradlew build --no-daemon`) и при необходимости прогони
> интеграционные проверки (`CI=true ./gradlew runServer -PsanctitySmoke --no-daemon`).
> Дальше моя задача: <опишите, что нужно сделать>.

Приложите к сообщению архив репозитория или файлы `AI-HANDOFF.md` и `src/`.
Сборка требует **JDK 21**; в конфиге уже стоит ограничение памяти под слабые машины.
