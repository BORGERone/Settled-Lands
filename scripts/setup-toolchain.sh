#!/usr/bin/env bash
# Ставит JDK 21 туда, где Java нет вообще (без sudo), и печатает следующие шаги.
# Использовать так:  source scripts/setup-toolchain.sh
set -euo pipefail

JDK_DIR="${JDK_DIR:-/tmp/tools/jdk21}"
URL="https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"

if [ ! -x "$JDK_DIR/bin/java" ]; then
    echo "JDK не найден, скачиваю Temurin 21 в $JDK_DIR ..."
    mkdir -p "$(dirname "$JDK_DIR")"
    tmp="$(mktemp -d)"
    if ! curl -fsSL -o "$tmp/jdk.tar.gz" "$URL"; then
        echo "Не удалось скачать JDK: нет сети или нет доступа к api.adoptium.net." >&2
        echo "Без JDK 21 сборку и интеграционный набор запустить нельзя." >&2
        rm -rf "$tmp"
        return 1 2>/dev/null || exit 1
    fi
    tar -xzf "$tmp/jdk.tar.gz" -C "$tmp"
    rm -rf "$JDK_DIR"
    mv "$tmp"/jdk-21* "$JDK_DIR"
    rm -rf "$tmp"
fi

export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
# Глобальный _JAVA_OPTIONS передаётся дочерним JVM шагов сборки и валит их на слабых машинах.
unset _JAVA_OPTIONS || true

echo "JAVA_HOME=$JAVA_HOME"
java -version
echo
echo "Готово. Дальше:"
echo "  chmod +x gradlew"
echo "  CI=true ./gradlew build --no-daemon            # сборка + unit-тесты"
echo "  scripts/verify-all.sh                          # всё сразу, включая серверный набор"
