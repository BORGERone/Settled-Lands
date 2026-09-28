#!/usr/bin/env bash
# Полная проверка проекта: статический анализ, сборка с unit-тестами, интеграционный набор.
# Переменные: SKIP_SMOKE=1 — пропустить серверный набор, JAVA_HOME — свой JDK 21.
cd "$(dirname "$0")/.." || exit 1
fail=0

echo "=== 1/3. Статические проверки (JDK не нужен) ==="
if command -v python3 >/dev/null; then
    python3 tools/static-check.py || fail=1
else
    echo "python3 не найден, шаг пропущен"
fi

echo
echo "=== 2/3. Сборка и unit-тесты ==="
if [ -x "${JAVA_HOME:-/nonexistent}/bin/java" ] || command -v java >/dev/null; then
    chmod +x gradlew
    ( unset _JAVA_OPTIONS; CI=true ./gradlew build --no-daemon ) || fail=1
else
    echo "JDK 21 не найден. Выполните 'source scripts/setup-toolchain.sh' и повторите." >&2
    fail=1
fi

echo
echo "=== 3/3. Интеграционный набор на выделенном сервере ==="
if [ "${SKIP_SMOKE:-0}" = "1" ]; then
    echo "Пропущен (SKIP_SMOKE=1)"
elif [ -x "${JAVA_HOME:-/nonexistent}/bin/java" ] || command -v java >/dev/null; then
    mkdir -p run-smoke
    [ -f run-smoke/server.properties ] || cp smoke-server/server.properties run-smoke/server.properties
    [ -f run-smoke/eula.txt ] || echo "eula=true" > run-smoke/eula.txt
    ( unset _JAVA_OPTIONS; CI=true ./gradlew runServer -PsanctitySmoke --no-daemon ) || fail=1
    if [ -f run-smoke/sanctity-smoke-result.txt ]; then
        echo "Маркер: $(head -c 120 run-smoke/sanctity-smoke-result.txt)"
    else
        echo "Маркер результата не создан — набор не дошёл до конца." >&2
        fail=1
    fi
else
    echo "JDK 21 не найден, шаг пропущен" >&2
    fail=1
fi

echo
if [ "$fail" -eq 0 ]; then
    echo "ИТОГ: все проверки пройдены"
else
    echo "ИТОГ: есть ошибки, смотрите вывод выше" >&2
fi
exit "$fail"
