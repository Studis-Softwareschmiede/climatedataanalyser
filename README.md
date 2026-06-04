# climatedataanalyser

Spring Boot (Java 21) + Angular 21 Anwendung zur Analyse deutscher Klimadaten (DWD).
Ein einziges Docker-Image (API + eingebettetes Frontend), MariaDB als Datenbank,
Schema-Migration via Flyway beim Boot.

## Quick Start (ein Befehl)

Auf einem Host mit Docker — kein Klonen, kein Konfigurieren:

```bash
curl -O https://raw.githubusercontent.com/Studis-Softwareschmiede/climatedataanalyser/master/docker-compose.simple.yml
docker compose -f docker-compose.simple.yml up -d
```

→ App läuft auf `http://<host>:8092` (Flyway migriert selbst). Daten einmalig laden:

```bash
curl "http://<host>:8092/api/database/batchImportStart?withFTP=true"
```

Der DWD-FTP-Import läuft asynchron (~Minuten, ~745k Records). Das war's.

> Die Quick-Start-Variante nutzt Default-Passwörter (DB nur container-intern, kein
> Host-Port), `:latest` und **kein** TLS — ideal zum Ausprobieren. Für eine gehärtete
> Produktion (gepinnte Version, HTTPS/Domain, Backups) siehe **[docs/deployment.md](docs/deployment.md)**.

## Deployment-Varianten im Überblick

| Variante | Aufwand | Wofür |
|---|---|---|
| `docker-compose.simple.yml` | 1 Befehl | Ausprobieren / Demo / interner Host |
| `docker-compose.prod.yml` (+ `docker-compose.caddy.yml`) | Runbook | Gehärtete Prod: gepinnt, HTTPS, Backups — siehe [docs/deployment.md](docs/deployment.md) |
| **Coolify** (Self-hosted PaaS) | GUI, einmalig einrichten | „Läuft von selbst": Auto-TLS, Auto-Deploy, Backups per Klick — siehe [docs/deployment.md#coolify](docs/deployment.md) |

## Entwicklung

```bash
docker compose up -d            # Dev-Stack (DB-Port nach außen zum Debuggen)
```

Details, Credentials, Release-Prozess und VPS-Runbook: **[docs/deployment.md](docs/deployment.md)**.
