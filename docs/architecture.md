# Detailkonzept / Architektur — ClimateDataAnalyser

## Domänenmodell

Das System verarbeitet historische Klimadaten des Deutschen Wetterdienstes (DWD) für deutsche Wetterstationen.

**Begriffe:**

- **Station** — eine DWD-Wetterstation mit eindeutiger numerischer ID (`STATION_ID`), Geo-Koordinaten (Breite/Länge), Höhe, Name und Bundesland. Eine Station kann mehrere Zeiträume (DATE_BEGIN/DATE_END) abdecken.
- **Bundesland** — eines der 16 deutschen Bundesländer; primäres Filterkriterium für Abfragen neben GPS-Bounding-Box.
- **Month (Monatsmesswert)** — ein einzelner Monat einer Station mit Rohmesswerten: mittlere Temperatur (MO_TT), Minimum (MO_TN), Maximum (MO_TX), Niederschlag (MO_RR), Sonnenstunden (MO_SD_S), Wind (MO_FK) etc. Wird aus DWD-CSV-Dateien importiert.
- **StationWeatherPerYear (Jahres-Wetterdatensatz)** — aggregierte Monatsmitteltemperaturen (Januar–Dezember) für eine Station und ein Kalenderjahr. Kann auch künstlich berechnet sein (`CALCULATED_ARTIFICIALLY`).
- **StationClimate (30-Jahres-Klimamittel)** — aggregierte Monatsdurchschnittstemperaturen für eine Station über eine 30-Jahres-Klimaperiode (START_PERIOD–END_PERIOD, z.B. 1988–2018).
- **Klimaperiode** — typisch 30 Jahre; der konfigurierbare Parameter `climate.calculation.period.year` steuert die Länge.
- **Null-Temperatur** — Sentinel-Wert `-999.0000` (BigDecimal) für fehlende Messwerte; wird bei Berechnungen herausgefiltert.

**Beziehungen:**

```
Station (1) ←── STATION_ID ──→ (*) StationWeatherPerYear
Station (1) ←── STATION_ID ──→ (*) StationClimate
Station (1) ←── STATIONS_ID ──→ (*) Month (Rohimport)
StationWeatherPerYear wird aus Month aggregiert (Batch-Step)
StationClimate wird aus StationWeatherPerYear berechnet (Batch-Step)
```

---

## Komponenten

### Frontend — Angular 13 (`climatedataanalyser-ng/`)

Single-Page-Application, Angular 13.3.x. Wird beim Maven-Build via `frontend-maven-plugin` gebaut und als statische Ressourcen in das WAR eingebettet. Im Produktivbetrieb: kein separater Webserver nötig; Spring Boot liefert die Angular-Dateien aus.

**Module/Komponenten:**
- `analytics` — Vergleich zweier Klimaperioden (Bundesland oder GPS-BBox), ruft `POST /api/analytics/request/`
- `climates` — Klima-Zeitreihe (Bundesland oder GPS-BBox) mit konfigurierbarem Jahr-Abstand, ruft `GET /api/climateRecords/`
- `database` — DB-Load-Status, Batch-Import-Start, Batch-Löschen, ruft `GET/POST /api/database/`
- `navigation` — App-Header mit Versionanzeige, ruft `GET /api/appInfo/`
- `shared/ApiService` — zentraler HTTP-Client für alle Endpoints
- `wolfgang` — Kartenkomponente (OpenLayers + OSM-Tiles); Stations-Bounding-Box via `GET /api/stations/bbox`

**Boundaries:** Das Frontend kennt nur die REST-API. Kein direkter DB-Zugriff. Im Dev-Modus (`environment.ts`) wird `BASE_URL = http://localhost:8092` genutzt; im Prod-Build (`environment.prod.ts`) ist `BASE_URL = ''` (relative URLs, same-origin).

---

### Backend — Spring Boot 2.6.6 WAR (`climatedataanalyser-api/`)

Java 11, Port 8092. Verpackt als executable WAR (Spring Boot WarLauncher). Enthält das Angular-Build-Ergebnis.

**Controller-Schicht (`controller/`):**

| Klasse | Mapping | Zweck |
|---|---|---|
| `AppInfo` | `GET /api/appInfo/` | Build-Version + Buildzeit aus `BuildProperties` |
| `AnalyticsController` | `GET /api/analytics/`, `POST /api/analytics/request/` | Bundesland-Liste + Klimavergleich |
| `Climates` | `GET /api/climateRecords/` | Klima-Zeitreihe mit Jahr-Abstand |
| `DataBaseController` | `GET /api/database/batchImportStart`, `GET /api/database/`, `POST /api/database/clear` | Batch-Job-Trigger, DB-Status, Tabellen-Truncate |
| `MonitoringController` | `GET /api/memory-status/` | JVM-Heap-Statistiken |
| `StationBBoxController` | `GET /api/stations/bbox` | Bounding-Box aller Stationen eines Bundeslands |
| `controller` (Klasse) | `GET /api/status/` | Einfacher Liveness-Check |
| `ComparedStations` | `/api/comparedStations` | TODO — kein Endpunkt implementiert |

**Service-Schicht (`service/`):**

- `service/db/`: `StationService`, `ClimateService`, `MonthService`, `StationWeatherService` — CRUD/Abfragen gegen die Entities via DAO
- `service/ui/analytics/`: `ClimateAnalyserService` (Klimavergleich zweier Perioden), `ClimateHistoryAnalyserService` (Zeitreihe der Perioden)
- `service/ui/climateRecords/`: `ClimateRecordService` (Klima-Zeitreihe, Differenzberechnung)
- `service/ui/dbController/`: `DbLoadInformationService` (Spring-Batch-Status), `DbStatusInformationService` (DB-Füllstatus: `empty/loading/loaded/stopped/failed`)

**DAO-Schicht (`dao/`):**

- `StationClimateImpl` — Hibernate-JPQL-Queries für Klimadaten (nach Bundesland, GPS-BBox, ab Jahr)
- `StationDaoImpl` — Abfragen auf STATION (Bundesland-Existenz, alle Bundesländer)
- `DbLoadInformationeImpl` — JDBC-Abfragen auf Spring-Batch-Meta-Tabellen (letzter Job-Status)
- `MonthDAOImpl`, `StationWeatherImpl` — Speichern von Month- und Weather-Entities

**Batch-Pipeline (`batch/`):**

Spring Batch, `@EnableBatchProcessing`. Job `importGermanClimateDataJob`. Wird NICHT beim Start ausgelöst (`spring.batch.job.enabled=false`); Trigger via `GET /api/database/batchImportStart?withFTP=true|false`.

**CORS (`config/CorsConfig`):**

Globale CORS-Konfiguration für `/api/**`. Erlaubte Origins via `app.cors.allowed-origins` (Property), in Prod via `APP_CORS_ALLOWED_ORIGINS` (Env-Variable, Spring Relaxed Binding). Kein Wildcard.

---

### Datenbank — MySQL/MariaDB

Datenbankname `CLIMATE`. Tabellen: `MONTH_`, `STATION`, `WEATHER`, `CLIMATE` (Applikationsdaten) + Spring-Batch-Meta-Tabellen (`BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION` etc.). Schema wird beim Start via `spring.datasource.initialization-mode=always` + `schema.sql` erzeugt (CREATE TABLE IF NOT EXISTS). Connection-Pool: HikariCP (max 10, connection-timeout 30 s, max-lifetime 30 min, idle-timeout 10 min).

---

## Kern-Flows

### Flow 1 — DWD-Daten-Import (Batch-Job)

Auslöser: `GET /api/database/batchImportStart?withFTP=true|false`

```
1. Step: download (Tasklet ClimateFtpDataDownloader)
   - Wenn withFTP=true: FTP-Connect zu opendata.dwd.de (anonymous)
     → Verzeichnis climate_environment/CDC/observations_germany/climate/monthly/kl/historical/
     → Alle Dateien nach download/FTPData/ herunterladen
   - Wenn withFTP=false: FTP-Schritt übersprungen (bestehende Dateien in FTPData/ werden verwendet)

2. Step: unzipFiles (Tasklet ClimateFtpDataUnziper)
   - Alle .zip-Dateien aus FTPData/ entpacken → download/UnzipedDataInputDataFiles/
   - Korrupte/truncated ZIPs werden übersprungen (Warning-Log)
   - Entpackte Dateien nach download/InputFiles/ verschieben

3. Step: import-temperature-records (Chunk-Reader: MultiResourceItemReader auf *.produkt*-Dateien)
   - Liest DWD-CSV-Dateien (Semikolon-Delimiter) → MonthFile → Month-Entity
   - Chunk-Größe: 10 000; FaultTolerant mit skipLimit=100 (FlatFileParseException, IncorrectTokenCountException)
   - Skipped Records werden in SkippedRecordTracker erfasst und via /api/database/ zurückgemeldet
   - Writer: saveAll via MonthService → MONTH_-Tabelle

4. Step: import-station-records (Chunk-Reader: FlatFileItemReader auf KL_Monatswerte_Beschreibung_Stationen.txt)
   - Fixed-Width-Format (Cp1252), 2 Header-Zeilen überspringen
   - Tokenizer mit setStrict(false) wegen trailing whitespace + "Abgabe"-Spalte
   - Bundesland-Range: Spalten 103–143 (verhindert "Bayern   Frei"-Artefakte)
   - Chunk-Größe: 100; skipLimit=100
   - Writer: StationDBWriter → STATION-Tabelle

5. Step: import-weather-records (Chunk-Reader liest aus MONTH_-Tabelle via WeatherReader)
   - Aggregiert Month-Einträge zu jährlichen Monatsmitteltemperaturen
   - Chunk-Größe: 5 000
   - Writer: WeatherWriter → WEATHER-Tabelle (StationWeatherPerYear)

6. Step: import-climate-records (Chunk-Reader liest aus WEATHER-Tabelle via WeatherReader)
   - Berechnet 30-Jahres-Klimamittel aus StationWeatherPerYear
   - Chunk-Größe: 5 000
   - Writer: ClimateWriter → CLIMATE-Tabelle (StationClimate)
   - saveAll: flush+clear alle 50 Elemente (OOM-Schutz bei GenerationType.IDENTITY)
```

Vorbedingung für sauberen Re-Import: `POST /api/database/clear` (truncatiert alle App- und Batch-Meta-Tabellen, FK_CHECKS temporär deaktiviert).

---

### Flow 2 — Analytics-Abfrage (Klimavergleich)

```
1. Frontend: POST /api/analytics/request/
   Body: { bundesland, gps1, gps2, yearOrigine, yearToCompare }

2. AnalyticsController → ClimateAnalyserService.getClimateAnalyticsByClimateAnalyserRequest()

3. Input-Validierung (Service):
   - Entweder Bundesland ODER GPS-Koordinaten (nicht beide gleichzeitig)
   - GPS-Werte müssen gültige Lat/Long-Ranges haben
   - yearOrigine + yearToCompare müssen numerisch sein

4. DAO-Abfrage (StationClimateDAO):
   - By Bundesland: JPQL-Join StationClimate ↔ Station WHERE bundesLand = :bundesland
   - By GPS-BBox: JPQL-Join mit GEO_LATITUDE/GEO_LENGTH BETWEEN-Bedingungen

5. Aggregation (Service):
   - Alle StationClimate für yearOrigine → Durchschnitt über alle Stationen (TemperatureForMonths.getAverage)
   - Alle StationClimate für yearToCompare (nur Stationen, die auch in yearOrigine existierten) → Durchschnitt
   - Differenz: compare - origin pro Monat
   - Zusätzlich: Klima-Zeitreihe (ClimateHistoryAnalyserService)

6. Response: ClimateAnalyserResponseDto (original, compare, climateHistoryAverageDtos, errorMsg)
```

---

### Flow 3 — Klima-Zeitreihe (`/api/climateRecords/`)

```
1. Frontend: GET /api/climateRecords/?bundesland=&gps1.lat=&gps1.long=&gps2.lat=&gps2.long=&startYear=&distanceYear=

2. ClimateRecordService:
   - Input-Validierung (Bundesland ODER GPS, Jahr 4-stellig numerisch, distanceYear numerisch)
   - DAO: getClimateForBundeslandFromYearOrderByYearAndStationId ODER getClimateForGpsCoordinatesFromYearOrderByYearAndStationId
   - Filter: nur Klimaperioden ab startYear, mit Mindestabstand distanceYear
   - Aggregation: pro Startperiode → Durchschnitt aller Stationen → ClimateRecord
   - Differenzberechnung: je zwei aufeinanderfolgende Klimaperioden wird ein Delta-Record eingefügt
```

---

### Flow 4 — AppInfo / Monitoring

```
GET /api/appInfo/  → Version + Buildzeit aus Maven BuildProperties
GET /api/status/   → Liveness ("Server is running!")
GET /api/memory-status/  → JVM-Heap (total, max, free)
GET /api/database/ → Spring-Batch-Meta-Status des letzten Jobs (Steps, Read/Write-Counts, Skipped Records, File-Counts in den Download-Ordnern)
```

---

## Zustände

### Batch-Job-Lebenszyklus (Spring Batch Meta)

Spring Batch hält den Job-Status in `BATCH_JOB_EXECUTION.STATUS`:

```
STARTING → STARTED → COMPLETED
                   → FAILED
                   → STOPPED
```

Step-Status analog in `BATCH_STEP_EXECUTION.STATUS`. Der Frontend-DB-Status (`DbStatusEnum`) spiegelt dies auf: `empty | loading | loaded | stopped | failed`. Abfrage via `GET /api/database/` (liest BATCH_JOB_EXECUTION + BATCH_STEP_EXECUTION für die letzte Job-Execution-ID).

### DB-Load-Status

- `empty` — MONTH_-Tabelle hat 0 Einträge
- `loading` — Job läuft (Status STARTED)
- `loaded` — letzter Job COMPLETED
- `stopped` — Job manuell gestoppt
- `failed` — Job fehlgeschlagen

---

## Externe Schnittstellen

### DWD-FTP (Quelldaten)

- Server: `opendata.dwd.de` (anonymer FTP, kein Passwort)
- Verzeichnis: `climate_environment/CDC/observations_germany/climate/monthly/kl/historical/`
- Format: ZIP-Dateien mit DWD-CSV-Monatsdaten (Semikolon-getrennt, UTF-8) + eine Stationsbeschreibungsdatei (`KL_Monatswerte_Beschreibung_Stationen.txt`, fixed-width, Cp1252)
- Zugriff: nur beim Batch-Import; nicht bei laufendem Frontend

### OpenStreetMap-Tiles (Karte)

- Genutzt von der Kartenkomponente (`wolfgang`) via OpenLayers
- Standard-OSM-Tile-Endpunkt (TBD: genaue URL in der Angular-Komponente zu verifizieren)

### REST-API-Endpunkte (`/api/*`)

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/api/status/` | Liveness-Check |
| `GET` | `/api/appInfo/` | Build-Version + Buildzeit |
| `GET` | `/api/memory-status/` | JVM-Heap-Statistiken |
| `GET` | `/api/analytics/` | Liste aller Bundesländer |
| `POST` | `/api/analytics/request/` | Klimavergleich zweier Perioden |
| `GET` | `/api/climateRecords/` | Klima-Zeitreihe mit Differenzen |
| `GET` | `/api/database/` | Batch-Job-Status + File-Counts |
| `GET` | `/api/database/batchImportStart` | Batch-Import starten (`?withFTP=true|false`) |
| `POST` | `/api/database/clear` | Alle Tabellen truncaten (Re-Import-Vorbereitung) |
| `GET` | `/api/stations/bbox` | Bounding-Box aller Stationen eines Bundeslands (`?bundesland=`) |

---

## NFRs

- **Performance Batch-Import:** Chunk-Größe 10 000 (Temperature), 5 000 (Weather/Climate), 100 (Station). HikariCP max 10 Connections. OOM-Schutz in `StationClimateImpl.saveAll`: flush+clear alle 50 Entities (Hibernate L1-Cache). Spring-Task-Pool: keep-alive 6000 s, scheduling pool size 3.
- **Keine öffentliche Erreichbarkeit der DB:** In Produktion (`docker-compose.prod.yml`) hat der DB-Container kein `ports:`-Mapping; nur `expose: ["3306"]` (Compose-intern). Dev (`docker-compose.yml`): DB via `${DB_PORT:-3306}:3306` erreichbar (nur lokal).
- **CORS env-konfigurierbar:** `app.cors.allowed-origins` (Property-Default: `http://localhost:4200`); in Prod via `APP_CORS_ALLOWED_ORIGINS` überschreibbar (Spring Relaxed Binding). Kein Wildcard. Gilt für alle `/api/**`-Pfade.
- **Credentials ausschliesslich via Env-Vars:** `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` (und optional `SPRING_DATASOURCE_URL`). Keine hartkodierten Werte in `application.properties` oder im Image. `.env.db`-Datei wird via `env_file` in Compose eingelesen; ist nicht im Repository.
- **Non-root Container:** Das Docker-Image legt einen `appuser` (system, no-login) an und läuft unter diesem.

---

## Entscheidungen (ADR)

**ADR-001 · 2026-06-02 · Env-Only Credentials**
Datenbankpasswort und -nutzer werden ausschliesslich via `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` (Env-Vars) übergeben, nie in `application.properties` oder im Image. Begründung: verhindert Secret-Leaks bei Image-Pushes und ist Voraussetzung für Secret-Scan (gitleaks) im CI. Verworfene Alternative: `.properties`-Datei im Image (unsicher).

**ADR-002 · 2026-06-02 · Env-konfigurierbare CORS-Allowlist**
CORS-Origins werden über `APP_CORS_ALLOWED_ORIGINS` (Env-Variable, Spring Relaxed Binding) konfiguriert statt als Compile-Zeit-Wert. Default `http://localhost:4200` für Dev. Begründung: ein einziges Image für mehrere Deployments; kein Rebuild bei Prod-URL-Änderung. Verworfene Alternative: `@CrossOrigin`-Annotationen pro Controller (fehlerhaft bei mehreren Controllern, nicht zentral überschreibbar).

**ADR-003 · 2026-06-02 · Container-Release + Git-Tag-Versionierung**
Releases werden via annotiertem Git-Tag `vX.Y.Z` ausgelöst; die Release-Pipeline (`release.yml`) baut das Docker-Image mit Semver-Tags (`X.Y.Z`, `X.Y`, `latest`) und legt ein GitHub-Release mit auto-generierten Notes an. Begründung: reproduzierbare Releases, immutable Image-Tags, keine manuellen Schritte. Verworfene Alternative: manuelle Image-Builds und Deployment ohne Git-Tag.

**ADR-004 · 2026-06-02 · Executable-WAR-Image (kein separater Tomcat)**
Das Docker-Image nutzt das durch Spring Boot umverpackte executable WAR (`climatedataanalyser-api-*.war`) direkt als `java -jar /app.war`. Kein separater Tomcat-Container. Begründung: einfacheres Deployment, kein Context-Path-Problem, geringere Angriffsfläche. Verworfene Alternative: externes Tomcat-Image mit WAR-Deploy (erfordert Context-Path-Handling in Angular).

**ADR-005 · 2026-06-02 · `master` als Default-Branch**
Der Org-Fork behält `master` als Default-Branch (historisch bedingt durch den Upstream-Fork). CI-Workflows triggern auf `main` und `master`. `/flow` nutzt `master` als PR-Base. Begründung: Kompatibilität mit bestehendem Upstream und vorhandenem Commit-History. Verworfene Alternative: Umbenennung zu `main` (würde existierende Branch-Referenzen und CI-Logs brechen).
