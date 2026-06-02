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

`docker-compose.yml` — startet db (Port `${DB_PORT:-3306}` auf dem Host erreichbar zum Debuggen), migrations und app.

## Produktion

```bash
docker compose -f docker-compose.prod.yml up -d
```

`docker-compose.prod.yml` — identischer Stack, aber der db-Service exportiert **keinen** Host-Port. Die Datenbank ist ausschliesslich compose-intern erreichbar (`expose: ["3306"]`). Der App-Port (`${APP_PORT:-8092}`) bleibt nach aussen offen.

## Migrations-Runner (manuell)

```bash
docker compose [-f docker-compose.prod.yml] run --rm migrations
```

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
