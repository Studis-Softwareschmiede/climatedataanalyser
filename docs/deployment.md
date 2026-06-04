# Deployment

## Voraussetzungen

Erstelle `.env.db` im Projektverzeichnis (nie committen — steht in `.gitignore`):

```
MARIADB_ROOT_PASSWORD=<geheim>
MARIADB_DATABASE=CLIMATE
MARIADB_USER=climate_user
MARIADB_PASSWORD=<geheim>
```

## Entwicklung (Dev)

```bash
docker compose up -d
```

`docker-compose.yml` — startet db (Port `${DB_PORT:-3306}` auf dem Host erreichbar zum Debuggen) und app. Schema-Migrationen laufen via Flyway beim App-Boot (kein separater migrations-Service, siehe Abschnitt „Schema-Migrationen").

## Produktion (VPS + Compose)

Empfohlenes Ziel: **ein einzelner kleiner VPS** mit Docker + Compose. Die App ist ein
einziges Image (Spring Boot + eingebettetes Angular + API), die DB läuft als MariaDB-
Container daneben. Die App ist zustandslos/wegwerfbar (jederzeit neu aus ghcr), das
**DB-Volume ist das Wertvolle** → persistenter Speicher + Backup (s. u.).

> **Single-Replica:** Der Batch-Import schreibt lokal nach `/app/data` und ist heap-intensiv —
> die App ist bewusst **nicht** horizontal skalierbar. Genau eine App-Instanz, dafür mit
> Memory-Limit (Default 2g, via `APP_MEM_LIMIT`).

```bash
export APP_VERSION=1.2.3            # NIE latest in prod — Pflicht, sonst bricht `up` ab
docker compose -f docker-compose.prod.yml up -d
```

`docker-compose.prod.yml`:
- App läuft auf einem **fixen** `APP_VERSION`-Tag (kein `:latest`, kein `build:`) → kein
  versehentliches Floaten bei `docker compose pull`.
- db hat **keinen** Host-Port (`expose: ["3306"]`, nur compose-intern, Spec §15-R7).
- **Memory-Limits** (`APP_MEM_LIMIT`/`DB_MEM_LIMIT`) + `MaxRAMPercentage=75` → kein OOM-Kill
  beim Vollimport.
- **Healthcheck** auf 8092; **named Volumes** `db_data` (DB) + `app_data` (FTP-Arbeitsdir).

### VPS-Erstinstallation (Runbook)

```bash
# 1) Docker + Compose-Plugin (Debian/Ubuntu)
curl -fsSL https://get.docker.com | sh

# 2) Code + Secrets
sudo mkdir -p /opt/climatedataanalyser && cd /opt/climatedataanalyser
git clone https://github.com/Studis-Softwareschmiede/climatedataanalyser.git .
cp .env.db.example .env.db && chmod 600 .env.db   # dann mit echten Secrets füllen

# 3) Firewall: nur SSH + 80/443 offen, 8092/3306 NICHT öffentlich
sudo ufw allow OpenSSH && sudo ufw allow 80,443/tcp && sudo ufw enable

# 4) Start mit TLS (siehe nächster Abschnitt) oder ohne:
export APP_VERSION=1.2.3
docker compose -f docker-compose.prod.yml up -d
```

**Update auf neue Version:** `export APP_VERSION=1.2.4 && docker compose -f docker-compose.prod.yml pull app && docker compose -f docker-compose.prod.yml up -d`.

## TLS-Ingress (Caddy, automatisches HTTPS)

Die App spricht nur HTTP:8092. Für eine öffentliche Domain terminiert **Caddy** TLS
(Let's Encrypt, auto-renew) und proxyt intern zu `app:8092` — die App wird dann nur noch
auf Loopback gebunden (kein öffentlicher 8092-Port).

```bash
export APP_VERSION=1.2.3 APP_DOMAIN=climate.example.org   # DNS-A-Record auf die VPS-IP!
docker compose -f docker-compose.prod.yml -f docker-compose.caddy.yml up -d
```

Voraussetzungen: DNS-A/AAAA für `$APP_DOMAIN` → VPS-IP, Ports 80+443 offen. Config liegt
in `deploy/Caddyfile` (inkl. HSTS/Hardening-Header). Zertifikate persistieren im Volume
`caddy_data` (sonst LE-Rate-Limit bei jedem Recreate).

*Alternative ohne offene Inbound-Ports:* Cloudflare Tunnel (`cloudflared`) — der agent-flow
`/preview`-VPS-Pfad nutzt das bereits; dann entfällt der Caddy-Overlay und 80/443 müssen nicht
offen sein.

## Schema-Migrationen

Es gibt **keinen** separaten Migrations-Service. Flyway läuft **im App-Container beim Boot**
(`spring.flyway.enabled=true`; `V1__init` → `V2__…` unter
`climatedataanalyser-api/src/main/resources/db/migration`). Neue Migration = neue `V*`-Datei
im Image → wird beim nächsten App-Start automatisch appliziert. Bei mehreren Migrationen sperrt
Flyway die DB (Single-Replica macht das unkritisch).

## Backups

Die DB ist das einzige Wertvolle (Daten via DWD-FTP reproduzierbar, aber Minuten teuer →
745k Records). `scripts/db-backup.sh` dumpt die laufende MariaDB (`mariadb-dump
--single-transaction`), gzippt und rotiert (`RETAIN_DAYS`, Default 14).

```bash
# manuell
BACKUP_DIR=/var/backups/climate scripts/db-backup.sh

# nightly via cron (crontab -e):
30 3 * * * cd /opt/climatedataanalyser && BACKUP_DIR=/var/backups/climate scripts/db-backup.sh >> /var/log/climate-backup.log 2>&1
```

**Restore:**

```bash
gunzip -c /var/backups/climate/climate-YYYYmmdd-HHMMSS.sql.gz \
  | docker compose -f docker-compose.prod.yml exec -T db \
      mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"
```

Für echte Disaster-Recovery die Dumps zusätzlich **off-host** spiegeln (Objektspeicher/rsync).

## Datenbank-Credentials

`spring.datasource.username` und `spring.datasource.password` stehen **nicht** in `application.properties` — sie müssen ausschliesslich via Umgebungsvariablen gesetzt werden:

| Env-Var | Beschreibung |
|---|---|
| `SPRING_DATASOURCE_USERNAME` | DB-Benutzer (z. B. `climateRUN`) |
| `SPRING_DATASOURCE_PASSWORD` | DB-Passwort |

**Compose/Prod:** Die Variablen werden über `.env.db` (via `env_file:` im compose-Service `app`) bereitgestellt. `.env.db` darf nie committet werden (steht im `.gitignore`).

**Lokaler Bare-Run (`mvn`/IDE):** Die Variablen müssen in der IDE-Run-Config (z. B. IntelliJ → Run/Debug Configurations → Environment variables) oder im Shell-Kontext gesetzt werden:

```bash
export SPRING_DATASOURCE_USERNAME=climateRUN
export SPRING_DATASOURCE_PASSWORD=<geheim>
mvn spring-boot:run
```

Ohne gesetzte Env-Vars verweigert Spring Boot den Start (DataSource-Konfigurationsfehler) — das ist gewollt.

Tests sind davon **nicht** betroffen: `test-it.properties` konfiguriert eine In-Memory-H2-Datenbank und braucht keine echten Credentials.

## Release

Ein Release entsteht durch ein annotiertes git-Tag:

```bash
git tag -a v1.2.3 -m "Release v1.2.3"
git push --tags
```

Die CI-Pipeline (`.github/workflows/release.yml`) wird dadurch ausgelöst und:

1. Führt den Secret-Scan (gitleaks) und `mvn test` als Gates aus.
2. Baut das Docker-Image und pusht es nach `ghcr.io/studis-softwareschmiede/climatedataanalyser` mit drei Tags:
   - `1.2.3` (exakte Version)
   - `1.2` (Major.Minor — zeigt immer auf das neueste Patch-Release)
   - `latest` (neuestes Release insgesamt)
3. Legt ein GitHub-Release mit automatisch generierten Release-Notes an.

**Versionierungs-Konvention:** Die `pom.xml` behält `*-SNAPSHOT` für die laufende Entwicklung (Dev-Build via `build.yml` bei Push auf master). Die Release-Identität ist ausschliesslich das git-Tag und der daraus abgeleitete Image-Tag — keine manuelle `pom.xml`-Änderung nötig, bevor man taggt.

**Prod-Deployment:** Prod-Umgebungen MÜSSEN ein fixes Versions-Tag verwenden (z. B. `1.2.3`), niemals `latest`. Das verhindert unbeabsichtigte Updates bei `docker compose pull`.

```yaml
# docker-compose.prod.yml — Beispiel
services:
  app:
    image: ghcr.io/studis-softwareschmiede/climatedataanalyser:1.2.3
```

## Credential-Rotation

Um die DB-Credentials zu rotieren:

1. Neuen Wert in der Env-Quelle setzen (`.env.db` auf dem Server oder im Secret-Store).
2. App-Container neu starten: `docker compose up -d --no-deps app` (bzw. `docker compose -f docker-compose.prod.yml up -d --no-deps app`).

Es ist keine Code-Änderung und kein neues Image nötig.
