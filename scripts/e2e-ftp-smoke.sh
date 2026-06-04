#!/usr/bin/env bash
#
# End-to-End-Runtime-Smoke: lädt das App-Image in einen Container, fährt eine echte
# MariaDB-11-DB daneben hoch (dieselbe Engine wie docker-compose.prod.yml → "getestet ==
# deployed"), startet den DWD-FTP-Import und prüft, dass die DB befüllt wird.
#
# DECKT GENAU DIE LÜCKE AB, die die H2-Unit-Tests strukturell nicht sehen
# (Flyway-Modul/Dialekt/Treiber, gepacktes Image, non-root-Schreibpfade, Batch-Reader,
# async-JobLauncher) — siehe agent-flow upgrade-subsystem §16.
#
# Bewusst KEIN per-PR-CI-Job: hängt an opendata.dwd.de (extern) + lädt hunderte Files
# (~Minuten). Gedacht für on-demand (workflow_dispatch) / nightly.
#
# Verwendung:
#   scripts/e2e-ftp-smoke.sh [IMAGE_TAG]
# Env:
#   BUILD=1        Image vor dem Test bauen (default: 1; 0 = vorhandenes IMAGE_TAG nutzen)
#   WITHFTP=true   echten DWD-FTP-Download fahren (default true). false = nur Pipeline ohne Daten.
#   JOB_TIMEOUT=600  max. Sekunden bis Job COMPLETED
#   ASYNC_MAX=10   max. Sekunden, die der Trigger-Request dauern darf (Async-Launcher-Check)
set -euo pipefail

IMAGE="${1:-climatedataanalyser:e2e}"
BUILD="${BUILD:-1}"
WITHFTP="${WITHFTP:-true}"
JOB_TIMEOUT="${JOB_TIMEOUT:-600}"
ASYNC_MAX="${ASYNC_MAX:-10}"

NET=e2e-cda-net
DB=e2e-cda-mariadb
APP=e2e-cda-app
DB_NAME=CLIMATE
DB_USER=climateRUN
DB_PASS=e2e-pass
DB_ROOT=e2e-root
HOST_PORT="${HOST_PORT:-8099}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

log()  { printf '\n\033[1;34m== %s\033[0m\n' "$*"; }
fail() { printf '\n\033[1;31mE2E FAIL: %s\033[0m\n' "$*" >&2; exit 1; }

cleanup() {
  docker rm -f "$APP" "$DB" >/dev/null 2>&1 || true
  docker network rm "$NET" >/dev/null 2>&1 || true
}
trap cleanup EXIT
cleanup

if [ "$BUILD" = "1" ]; then
  log "Image bauen: $IMAGE"
  docker build -t "$IMAGE" "$ROOT"
fi

log "Netz + MariaDB 11 (echte Engine wie prod, kein H2)"
docker network create "$NET" >/dev/null
docker run -d --name "$DB" --network "$NET" --network-alias db \
  -e MARIADB_ROOT_PASSWORD="$DB_ROOT" -e MARIADB_DATABASE="$DB_NAME" \
  -e MARIADB_USER="$DB_USER" -e MARIADB_PASSWORD="$DB_PASS" \
  mariadb:11 >/dev/null
log "warte auf MariaDB ready (init-restart-sicher)"
# MariaDB macht beim Erst-Init ein internes Bootstrap; `mariadb-admin ping` antwortet
# schon währenddessen, der echte Server lehnt aber kurz Verbindungen ab → App-Flyway
# bekäme "Connection refused". healthcheck.sh --connect --innodb_initialized (bringt das
# MariaDB-Image mit, identisch zur docker-compose.prod.yml) signalisiert erst, wenn die
# Engine wirklich startklar ist.
for i in $(seq 1 60); do
  if docker exec "$DB" healthcheck.sh --connect --innodb_initialized >/dev/null 2>&1; then
    break
  fi
  sleep 2; [ "$i" = 60 ] && fail "MariaDB nicht ready"
done

log "App-Container starten ($IMAGE, mem=${APP_MEM:-2g})"
# Memory: der Voll-Import (745k+ Records, Climate-Berechnung) ist heap-intensiv und wurde
# unter SB4/Hibernate 7 OOM-gekillt. Container-Limit + MaxRAMPercentage halten den JVM-Heap
# im Limit (statt unbegrenzt zu wachsen → OOMKilled durch den Host).
docker run -d --name "$APP" --network "$NET" \
  --memory "${APP_MEM:-2g}" --memory-swap "${APP_MEM:-2g}" \
  -e JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 ${JAVA_EXTRA_OPTS:-}" \
  -e SPRING_DATASOURCE_URL="jdbc:mysql://db:3306/${DB_NAME}?zeroDateTimeBehavior=CONVERT_TO_NULL&serverTimezone=Europe/Berlin" \
  -e SPRING_DATASOURCE_USERNAME="$DB_USER" \
  -e SPRING_DATASOURCE_PASSWORD="$DB_PASS" \
  -p "${HOST_PORT}:8092" "$IMAGE" >/dev/null

log "warte auf App-Boot (Flyway-Migration + /->200)"
for i in $(seq 1 40); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${HOST_PORT}/" 2>/dev/null || echo 000)
  [ "$code" = 200 ] && break
  if docker logs "$APP" 2>&1 | grep -q 'Application run failed'; then
    docker logs "$APP" 2>&1 | grep -iE 'Unsupported Database|Communications link|Caused by' | tail -5
    fail "App-Boot fehlgeschlagen (siehe Flyway/DB-Fehler oben)"
  fi
  sleep 3; [ "$i" = 40 ] && fail "App nicht erreichbar (/ != 200)"
done
docker logs "$APP" 2>&1 | grep -iE 'Successfully applied .* migrations|Migrating schema' | tail -2 || true

# '|| true': bei leerem Resultat liefert 'grep -v' exit 1 → würde unter 'set -e' das Skript
# killen. Tritt beim async-Launcher auf, wenn beim 1. Poll noch keine Job-Zeile existiert.
q() { docker exec "$DB" mariadb -uroot -p"$DB_ROOT" -N "$DB_NAME" -e "$1" 2>/dev/null | grep -v Warning || true; }

log "FTP-Import triggern (withFTP=$WITHFTP) + Async-Launcher-Check (<= ${ASYNC_MAX}s)"
t=$(curl -s -m "$ASYNC_MAX" -o /dev/null -w '%{time_total}' \
     "http://localhost:${HOST_PORT}/api/database/batchImportStart?withFTP=${WITHFTP}" 2>/dev/null) \
  || fail "Trigger-Request blockierte > ${ASYNC_MAX}s → JobLauncher ist NICHT async"
printf 'Trigger kehrte nach %ss zurück (async ok)\n' "$t"

log "Job-Ende abwarten (max ${JOB_TIMEOUT}s)"
deadline=$(( SECONDS + JOB_TIMEOUT ))
while :; do
  st=$(q "SELECT STATUS FROM BATCH_JOB_EXECUTION ORDER BY JOB_EXECUTION_ID DESC LIMIT 1;")
  cur=$(q "SELECT CONCAT(STEP_NAME,':',STATUS,' w=',IFNULL(WRITE_COUNT,0)) FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID=(SELECT MAX(JOB_EXECUTION_ID) FROM BATCH_JOB_EXECUTION) ORDER BY STEP_EXECUTION_ID DESC LIMIT 1;")
  printf '  job=%s | %s\n' "${st:-?}" "${cur:-?}"
  [ "$st" = COMPLETED ] && break
  [ "$st" = FAILED ] && { docker logs "$APP" 2>&1 | grep -iE 'Encountered an error executing step|Caused by|at ch.studer' | tail -8; fail "Batch-Job FAILED"; }
  [ "$SECONDS" -ge "$deadline" ] && fail "Job nicht fertig nach ${JOB_TIMEOUT}s (Status=$st)"
  sleep 5
done

log "DB-Befüllung prüfen"
rows=$(q "SELECT CONCAT((SELECT COUNT(*) FROM STATION),' ',(SELECT COUNT(*) FROM MONTH_),' ',(SELECT COUNT(*) FROM WEATHER),' ',(SELECT COUNT(*) FROM CLIMATE));")
read -r STATION MONTHS WEATHER CLIMATE <<< "$rows"
printf 'STATION=%s MONTH_=%s WEATHER=%s CLIMATE=%s\n' "$STATION" "$MONTHS" "$WEATHER" "$CLIMATE"
if [ "$WITHFTP" = true ]; then
  [ "${STATION:-0}" -gt 0 ] && [ "${MONTHS:-0}" -gt 0 ] && [ "${CLIMATE:-0}" -gt 0 ] \
    || fail "DB nicht befüllt (Import lieferte 0 Zeilen)"
fi

printf '\n\033[1;32mE2E PASS — Pipeline durchgelaufen, DB befüllt.\033[0m\n'
