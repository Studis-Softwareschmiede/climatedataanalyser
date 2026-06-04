#!/usr/bin/env bash
#
# DB-Backup für climatedataanalyser (VPS + Compose).
# Dumpt die laufende MariaDB des prod-Stacks via mariadb-dump, gzippt und rotiert.
#
# Die DB ist das einzige Wertvolle: die App ist wegwerfbar (Re-Pull aus ghcr), die Daten
# sind zwar via DWD-FTP reproduzierbar, aber der Vollimport dauert Minuten (745k Records).
# Ein nightly Dump spart im Ernstfall Stunden.
#
# Verwendung (aus dem Repo-Verzeichnis, wo .env.db + docker-compose.prod.yml liegen):
#   scripts/db-backup.sh
# Env:
#   BACKUP_DIR   Zielverzeichnis (default: ./backups)
#   RETAIN_DAYS  Aufbewahrung in Tagen (default: 14)
#   COMPOSE_FILE Compose-Datei (default: docker-compose.prod.yml)
#
# Cron (täglich 03:30, Log anhängen) — `crontab -e` auf dem VPS:
#   30 3 * * * cd /opt/climatedataanalyser && BACKUP_DIR=/var/backups/climate scripts/db-backup.sh >> /var/log/climate-backup.log 2>&1
#
# Restore (manuell):
#   gunzip -c backups/climate-YYYYmmdd-HHMMSS.sql.gz \
#     | docker compose -f docker-compose.prod.yml exec -T db \
#         mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

BACKUP_DIR="${BACKUP_DIR:-$ROOT/backups}"
RETAIN_DAYS="${RETAIN_DAYS:-14}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"

[ -f .env.db ] || { echo "FEHLER: .env.db nicht gefunden (Creds-Quelle)" >&2; exit 1; }
# .env.db laden (MARIADB_DATABASE, MARIADB_ROOT_PASSWORD)
set -a; . ./.env.db; set +a
: "${MARIADB_DATABASE:?MARIADB_DATABASE fehlt in .env.db}"
: "${MARIADB_ROOT_PASSWORD:?MARIADB_ROOT_PASSWORD fehlt in .env.db}"

mkdir -p "$BACKUP_DIR"
# Zeitstempel ohne Sekundenbruchteile; -u für Reproduzierbarkeit egal — lokale Zeit ist ok.
TS="$(date +%Y%m%d-%H%M%S)"
OUT="$BACKUP_DIR/climate-$TS.sql.gz"
TMP="$OUT.partial"

echo "== Dump $MARIADB_DATABASE → $OUT"
# --single-transaction: konsistenter Snapshot ohne Tabellen-Locks (InnoDB).
# -T (exec ohne TTY) → sauberer Binär-/Textstrom in die Pipe.
docker compose -f "$COMPOSE_FILE" exec -T db \
  mariadb-dump --single-transaction --quick --routines --events \
    -uroot -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE" \
  | gzip -c > "$TMP"

# Erst nach Erfolg final umbenennen (kein halbes Backup mit gültigem Namen).
mv "$TMP" "$OUT"
SIZE="$(du -h "$OUT" | cut -f1)"
echo "== OK ($SIZE)"

echo "== Rotation: lösche Dumps älter als ${RETAIN_DAYS} Tage"
find "$BACKUP_DIR" -name 'climate-*.sql.gz' -type f -mtime "+${RETAIN_DAYS}" -print -delete || true

echo "== Fertig. Vorhandene Backups:"
ls -1t "$BACKUP_DIR"/climate-*.sql.gz 2>/dev/null | head -5
