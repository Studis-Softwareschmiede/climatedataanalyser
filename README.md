# climatedataanalyser

Spring Boot (Java 21) + Angular 21 Anwendung zur Analyse deutscher Klimadaten (DWD).
Ein einziges Docker-Image (API + eingebettetes Frontend), MariaDB als Datenbank,
Schema-Migration via Flyway beim Boot.

## Quick Start

Auf einem Host mit Docker — kein Klonen, kein Konfigurieren. Lege den Stack nach
`/opt/climatedataanalyser` (FHS-Konvention für self-hosted Apps) und übereigne dir das
Verzeichnis, damit du **ohne `sudo` und nicht als root** arbeitest:

```bash
sudo mkdir -p /opt/climatedataanalyser
sudo chown $USER:$USER /opt/climatedataanalyser
cd /opt/climatedataanalyser
curl -O https://raw.githubusercontent.com/Studis-Softwareschmiede/climatedataanalyser/master/docker-compose.simple.yml
docker compose -f docker-compose.simple.yml up -d
```

> **Nicht in `/home` ausführen** — dort fehlt die Schreibberechtigung (`curl … Permission denied`).
> In `/opt/climatedataanalyser` liegt nur die Compose-Datei; die DB-Daten leben in einem
> Docker Named Volume (`…_db_data`, verwaltet unter `/var/lib/docker/volumes/`) und
> überstehen ein `docker compose down && up`.
>
> Sagt `docker` „permission denied" auf `docker.sock`: einmalig `sudo usermod -aG docker $USER`
> + neu einloggen — dann läuft Docker ohne `sudo`.

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
