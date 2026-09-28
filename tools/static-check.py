#!/usr/bin/env python3
"""Static consistency checks for Settled Lands. Needs only Python, no JDK.

Covers what can be verified without a build:
  1. every @Mixin class is registered in settledlands.mixins.json (and vice versa);
  2. max_level is 2 in data/settledlands/enchantment/sanctity.json;
  3. version in gradle.properties matches CHANGELOG-<version>.md and README refs;
  4. every JSON under src/main/resources parses;
  5. markdown links to local files actually exist;
  6. smoke tests stay out of the release JAR (separate source set behind -PsanctitySmoke).

Exit code 0 = all green, 1 = at least one failure.
Run from the repository root:  python3 tools/static-check.py
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
failures = []


def ok(message):
    print(f"PASS: {message}")


def fail(message):
    print(f"FAIL: {message}")
    failures.append(message)


def check_mixins():
    mixin_dir = ROOT / "src/main/java/dev/settledlands/mixin"
    declared = {p.stem for p in mixin_dir.glob("*Mixin.java")}
    if not declared:
        fail("no *Mixin.java files found — wrong directory?")
        return
    config = json.loads((ROOT / "src/main/resources/settledlands.mixins.json").read_text(encoding="utf-8"))
    registered = set(config.get("mixins", [])) | set(config.get("client", []))
    for name in sorted(declared - registered):
        fail(f"mixin {name} exists but is not registered in settledlands.mixins.json")
    for name in sorted(registered - declared):
        fail(f"mixin {name} is registered but src/.../mixin/{name}.java is missing")
    if declared == registered:
        ok(f"mixins registered: {len(declared)} classes match settledlands.mixins.json")


def check_sanctity_levels():
    data = json.loads((ROOT / "src/main/resources/data/settledlands/enchantment/sanctity.json").read_text(encoding="utf-8"))
    if data.get("max_level") == 2:
        ok("sanctity.json max_level is 2")
    else:
        fail(f"sanctity.json max_level is {data.get('max_level')!r}, expected 2")


def read_version():
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith("mod_version="):
            return line.split("=", 1)[1].strip()
    fail("mod_version not found in gradle.properties")
    return None


def check_version_consistency():
    version = read_version()
    if version is None:
        return
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        fail(f"mod_version {version!r} is not MAJOR.MINOR.PATCH")
        return
    changelog = ROOT / f"CHANGELOG-{version}.md"
    if changelog.is_file():
        ok(f"CHANGELOG-{version}.md exists")
    else:
        fail(f"CHANGELOG-{version}.md is missing for mod_version={version}")
    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    if version in readme and f"settledlands-{version}.jar" in readme:
        ok(f"README references version {version} and settledlands-{version}.jar")
    else:
        fail(f"README does not reference version {version} / settledlands-{version}.jar")
    jars = sorted(p.name for p in (ROOT / "dist").glob("*.jar")) if (ROOT / "dist").is_dir() else []
    if f"settledlands-{version}.jar" in jars:
        ok(f"dist/settledlands-{version}.jar is built")
    else:
        print(f"NOTE: dist/settledlands-{version}.jar not built yet (dist has: {', '.join(jars) or 'nothing'})")


def check_json():
    files = sorted((ROOT / "src/main/resources").rglob("*.json"))
    bad = 0
    for path in files:
        try:
            json.loads(path.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, UnicodeDecodeError) as e:
            fail(f"{path.relative_to(ROOT)} is not valid JSON: {e}")
            bad += 1
    if bad == 0:
        ok(f"all {len(files)} resource JSON files parse")


def check_doc_links():
    link_re = re.compile(r"\[[^\]]*\]\(([^)\s]+)\)")
    checked, missing = 0, 0
    for doc in sorted(ROOT.rglob("*.md")):
        if ".git" in doc.parts:
            continue
        for target in link_re.findall(doc.read_text(encoding="utf-8")):
            if re.match(r"(?:[a-zA-Z][a-zA-Z0-9+.-]*:|#)", target):
                continue  # external URL, anchor-only, mailto:, etc.
            path = (doc.parent / target.split("#", 1)[0]).resolve()
            checked += 1
            try:
                path.relative_to(ROOT)
            except ValueError:
                fail(f"{doc.relative_to(ROOT)} links outside the repo: {target}")
                missing += 1
                continue
            if not path.exists():
                fail(f"{doc.relative_to(ROOT)} links a missing file: {target}")
                missing += 1
    if missing == 0:
        ok(f"all {checked} local doc links exist")


def check_smoke_separation():
    build = (ROOT / "build.gradle").read_text(encoding="utf-8")
    gate = "providers.gradleProperty('sanctitySmoke')"
    if gate not in build:
        fail("build.gradle lost the -PsanctitySmoke gate for the smoke source set")
        return
    # Heuristic: every mention of the smoke source set must sit after the gate line.
    before_gate = build.split(gate, 1)[0]
    if "sourceSets.smoke" in before_gate or "src/smoke" in before_gate:
        fail("smoke source set is referenced before the -PsanctitySmoke gate")
    else:
        ok("smoke tests live behind the -PsanctitySmoke gate (not in the release JAR)")


def main():
    check_mixins()
    check_sanctity_levels()
    check_version_consistency()
    check_json()
    check_doc_links()
    check_smoke_separation()
    print()
    if failures:
        print(f"STATIC CHECK FAILED: {len(failures)} problem(s)")
        return 1
    print("STATIC CHECK PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
