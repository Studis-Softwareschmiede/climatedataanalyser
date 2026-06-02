# Datenmodell — ClimateDataAnalyser

Datenbank: `CLIMATE` (MySQL/MariaDB). Single-tenant, kein Mandanten-Filter.

---

## Entitäten

### MONTH_ (Java: `Month`)

Rohe Monatsmesswerte aus DWD-CSV-Dateien. Jeder Datensatz entspricht einem Messmonat einer Station.

| Spalte | Typ | Constraint | Beschreibung |
|---|---|---|---|
| `MONTH_ID` | BIGINT AUTO_INCREMENT | PK NOT NULL | Technischer PK |
| `STATIONS_ID` | INT | NOT NULL | DWD-Stations-ID (kein FK-Constraint in DB) |
| `MESS_DATUM_BEGINN` | DATE | NOT NULL | Beginn des Messzeitraums |
| `MESS_DATUM_ENDE` | DATE | NOT NULL | Ende des Messzeitraums |
| `QN_4` | INT | DEFAULT NULL | Qualitätsniveau Gruppe 4 |
| `MO_N` | DECIMAL(7,4) | DEFAULT NULL | Mittlere Bewölkung (Achtel) |
| `MO_TT` | DECIMAL(7,4) | DEFAULT NULL | Mittlere Temperatur in 2 m (°C) |
| `MO_TX` | DECIMAL(7,4) | DEFAULT NULL | Mittlere Tagesmaximumtemperatur (°C) |
| `MO_TN` | DECIMAL(7,4) | DEFAULT NULL | Mittlere Tagesminimumtemperatur (°C) |
| `MO_FK` | DECIMAL(7,4) | DEFAULT NULL | Mittlere Windgeschwindigkeit (m/s) |
| `MX_TX` | DECIMAL(7,4) | DEFAULT NULL | Höchstes Tagesmaximum der Temperatur (°C) |
| `MX_FX` | DECIMAL(7,4) | DEFAULT NULL | Höchste Windspitze (m/s) |
| `MX_TN` | DECIMAL(7,4) | DEFAULT NULL | Niedrigstes Tagesminimum der Temperatur (°C) |
| `MO_SD_S` | DECIMAL(7,4) | DEFAULT NULL | Monatssumme der Sonnenscheindauer (h) |
| `QN_6` | INT | DEFAULT NULL | Qualitätsniveau Gruppe 6 |
| `MO_RR` | DECIMAL(7,4) | DEFAULT NULL | Monatssumme des Niederschlags (mm) |
| `MX_RS` | DECIMAL(7,4) | DEFAULT NULL | Höchster Tagesniederschlag (mm) |

Primärschlüssel: `MONTH_ID` (AUTO_INCREMENT, GenerationType.IDENTITY).

---

### STATION (Java: `Station`)

DWD-Stationsstammdaten. Eine Station kann mehrere Zeilen haben (verschiedene Gültigkeitszeiträume).

| Spalte | Typ | Constraint | Beschreibung |
|---|---|---|---|
| `ID` | BIGINT AUTO_INCREMENT | PK NOT NULL | Technischer PK |
| `STATION_ID` | INT | NOT NULL | DWD-Stations-ID (fachlicher Identifier) |
| `DATE_BEGIN` | DATE | NOT NULL | Beginn der Stations-Gültigkeit |
| `DATE_END` | DATE | NOT NULL | Ende der Stations-Gültigkeit |
| `STATION_HIGH` | DECIMAL(9,3) | NOT NULL | Stationshöhe über NN (m) |
| `GEO_LATITUDE` | DECIMAL(7,4) | NOT NULL | Geographische Breite (Dezimalgrad) |
| `GEO_LENGTH` | DECIMAL(7,4) | NOT NULL | Geographische Länge (Dezimalgrad) |
| `STATION_NAME` | VARCHAR(100) | NOT NULL | Stationsname |
| `BUNDES_LAND` | VARCHAR(100) | NOT NULL | Deutsches Bundesland |

Primärschlüssel: `ID` (AUTO_INCREMENT, GenerationType.IDENTITY). `STATION_ID` ist der fachliche Identifier und Verknüpfungsschlüssel für alle anderen Tabellen (kein DB-FK-Constraint vorhanden).

---

### WEATHER (Java: `StationWeatherPerYear`)

Aggregierte Monats-Durchschnittstemperaturen pro Station pro Kalenderjahr. Erbt die 12 Monats-Spalten von `TemperatureForMonths` (`@MappedSuperclass`).

| Spalte | Typ | Constraint | Beschreibung |
|---|---|---|---|
| `WEATHER_ID` | BIGINT AUTO_INCREMENT | PK NOT NULL | Technischer PK |
| `STATION_ID` | INT | NOT NULL | DWD-Stations-ID |
| `YEAR_` | VARCHAR(4) | DEFAULT NULL | Kalenderjahr (yyyy) |
| `CALCULATED_ARTIFICIALLY` | BOOLEAN | NOT NULL | true = aus Nachbarjahren interpoliert |
| `JANUAR` | DECIMAL(7,4) | NOT NULL | Mittlere Temperatur Januar (°C) |
| `FEBRUAR` | DECIMAL(7,4) | NOT NULL | Mittlere Temperatur Februar (°C) |
| `MAERZ` | DECIMAL(7,4) | NOT NULL | Mittlere Temperatur März (°C) |
| `APRIL` | DECIMAL(7,4) | NOT NULL | Mittlere Temperatur April (°C) |
| `MAI` | DECIMAL(7,4) | NOT NULL | Mittlere Temperatur Mai (°C) |
| `JUNI` | DECIMAL(7,4) | NOT NULL | Mittlere Temperatur Juni (°C) |
| `JULI` | DECIMAL(7,4) | NOT NULL | Mittlere Temperatur Juli (°C) |
| `AUGUST` | DECIMAL(7,4) | NOT NULL | Mittlere Temperatur August (°C) |
| `SEPTEMBER` | DECIMAL(7,4) | NOT NULL | Mittlere Temperatur September (°C) |
| `OKTOBER` | DECIMAL(7,4) | NOT NULL | Mittlere Temperatur Oktober (°C) |
| `NOVEMBER` | DECIMAL(7,4) | NOT NULL | Mittlere Temperatur November (°C) |
| `DEZEMBER` | DECIMAL(7,4) | NOT NULL | Mittlere Temperatur Dezember (°C) |

Primärschlüssel: `WEATHER_ID` (AUTO_INCREMENT, GenerationType.IDENTITY).
Null-Sentinel: fehlende Temperaturwerte werden als `-999.0000` gespeichert.

---

### CLIMATE (Java: `StationClimate`)

30-Jahres-Klimamittel pro Station und Klimaperiode. Erbt die 12 Monats-Spalten + `STATION_ID` von `StationTemperature` und `TemperatureForMonths` (`@MappedSuperclass`).

| Spalte | Typ | Constraint | Beschreibung |
|---|---|---|---|
| `CLIMATE_ID` | BIGINT AUTO_INCREMENT | PK NOT NULL | Technischer PK |
| `STATION_ID` | INT | NOT NULL | DWD-Stations-ID |
| `START_PERIOD` | VARCHAR(4) | DEFAULT NULL | Startjahr der Klimaperiode (yyyy, z.B. `1988`) |
| `END_PERIOD` | VARCHAR(4) | DEFAULT NULL | Endjahr der Klimaperiode (yyyy, z.B. `2018`) |
| `JANUAR` | DECIMAL(7,4) | NOT NULL | Klimamittel Januar (°C) |
| `FEBRUAR` | DECIMAL(7,4) | NOT NULL | Klimamittel Februar (°C) |
| `MAERZ` | DECIMAL(7,4) | NOT NULL | Klimamittel März (°C) |
| `APRIL` | DECIMAL(7,4) | NOT NULL | Klimamittel April (°C) |
| `MAI` | DECIMAL(7,4) | NOT NULL | Klimamittel Mai (°C) |
| `JUNI` | DECIMAL(7,4) | NOT NULL | Klimamittel Juni (°C) |
| `JULI` | DECIMAL(7,4) | NOT NULL | Klimamittel Juli (°C) |
| `AUGUST` | DECIMAL(7,4) | NOT NULL | Klimamittel August (°C) |
| `SEPTEMBER` | DECIMAL(7,4) | NOT NULL | Klimamittel September (°C) |
| `OKTOBER` | DECIMAL(7,4) | NOT NULL | Klimamittel Oktober (°C) |
| `NOVEMBER` | DECIMAL(7,4) | NOT NULL | Klimamittel November (°C) |
| `DEZEMBER` | DECIMAL(7,4) | NOT NULL | Klimamittel Dezember (°C) |

Primärschlüssel: `CLIMATE_ID` (AUTO_INCREMENT, GenerationType.IDENTITY).

---

### Java-Vererbungshierarchie (Mapping)

```
TemperatureForMonths (@MappedSuperclass)
  └─ StationTemperature (@MappedSuperclass)   + STATION_ID
       ├─ StationWeatherPerYear (@Entity → WEATHER)   + WEATHER_ID, YEAR_, CALCULATED_ARTIFICIALLY
       └─ StationClimate (@Entity → CLIMATE)          + CLIMATE_ID, START_PERIOD, END_PERIOD
```

`TemperatureForMonths` ist auch direkt instanziierbar (für Berechnungen im Service-Layer), wird aber nicht als eigene Tabelle gemappt.

---

### Spring-Batch-Meta-Tabellen

Automatisch von Spring Batch verwaltet (`spring.batch.jdbc.initialize-schema=always`).

| Tabelle | Beschreibung |
|---|---|
| `BATCH_JOB_INSTANCE` | Je einmalige Job-Definition (Name + Key) |
| `BATCH_JOB_EXECUTION` | Je Job-Lauf mit Status, Start-/Endzeit |
| `BATCH_JOB_EXECUTION_PARAMS` | Job-Parameter (z.B. `time`, `withFTP`) |
| `BATCH_JOB_EXECUTION_CONTEXT` | Serialisierter Job-Kontext |
| `BATCH_STEP_EXECUTION` | Je Step-Lauf mit Read/Write-Counts, Status |
| `BATCH_STEP_EXECUTION_CONTEXT` | Serialisierter Step-Kontext |
| `BATCH_*_SEQ` | Sequenz-Tabellen (3 Stück) für ID-Generierung |

---

## Beziehungen

Alle Verknüpfungen laufen über `STATION_ID` (DWD-fachlicher Schlüssel). Es existieren **keine** Datenbank-Fremdschlüssel-Constraints zwischen den Applikationstabellen. Die Verknüpfung wird ausschliesslich im Applikationscode (JPQL-Joins) hergestellt.

```
STATION.STATION_ID  (1) ──── (*) MONTH_.STATIONS_ID
STATION.STATION_ID  (1) ──── (*) WEATHER.STATION_ID
STATION.STATION_ID  (1) ──── (*) CLIMATE.STATION_ID
```

Kardinalitäten:
- Eine Station hat 0..* Monatseinträge (MONTH_)
- Eine Station hat 0..* Jahresdatensätze (WEATHER)
- Eine Station hat 0..* Klimaperioden-Datensätze (CLIMATE); typisch eine pro 30-Jahres-Fenster

Die Spring-Batch-Meta-Tabellen sind untereinander durch FK-Constraints verknüpft (Standard Spring-Batch-Schema, Engine=InnoDB).

---

## Indizes

### Vorhandene Indizes (aus schema.sql)

`schema.sql` definiert keine expliziten Indizes ausser den PKs (AUTO_INCREMENT). Es existieren keine `CREATE INDEX`-Statements im aktuellen Schema.

### Empfohlene Indizes auf Filter-/Join-Spalten

Die folgenden Indizes fehlen und sollten bei einer Flyway-Migration (Item #17) angelegt werden:

| Tabelle | Spalte(n) | Begründung |
|---|---|---|
| `STATION` | `STATION_ID` | Join-Spalte für alle JPQL-Queries |
| `STATION` | `BUNDES_LAND` | Häufiges WHERE-Kriterium |
| `STATION` | `GEO_LATITUDE, GEO_LENGTH` | Bounding-Box-Queries (BETWEEN) |
| `WEATHER` | `STATION_ID` | Join + Aggregation |
| `WEATHER` | `YEAR_` | Filter nach Jahr |
| `CLIMATE` | `STATION_ID` | Join + Aggregation |
| `CLIMATE` | `END_PERIOD` | Filter nach Klimaperioden-Endjahr |
| `CLIMATE` | `START_PERIOD` | Sortierung + Filter |
| `MONTH_` | `STATIONS_ID` | Join beim WeatherReader (liest MONTH_ → WEATHER) |

---

## RLS / Zugriffskonzept

Nicht zutreffend — das System ist single-tenant. Es gibt keinen Mandanten-Filter, kein Row-Level-Security, kein `auth.uid()`-Konzept. Alle Applikationsdaten liegen in der Datenbank `CLIMATE` und sind vollständig über den Applikationsbenutzer zugänglich.

Zugangskontrolle erfolgt auf Infrastruktur-Ebene: DB-Credentials ausschliesslich via Env-Vars (`SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`). In Produktion ist die DB nicht direkt von aussen erreichbar (nur Compose-intern via `expose`, kein `ports:`).

---

## Migrations-Reihenfolge

### Aktueller Stand

Das Schema wird beim Anwendungsstart via Spring Boot `initialization-mode=always` + `schema.sql` erstellt (CREATE TABLE IF NOT EXISTS). Das bedeutet:

1. Spring Boot startet → DataSource-Init → `schema.sql` wird ausgeführt (idempotent durch IF NOT EXISTS)
2. Spring Batch prüft/erstellt eigene Meta-Tabellen (`spring.batch.jdbc.initialize-schema=always`)

Die Ausführungsreihenfolge in `schema.sql`: `MONTH_` → `STATION` → `WEATHER` → `CLIMATE`.

`preWorkSchema.sql` ist ein manuelles Setup-Skript (DROP + CREATE Database, Nutzer anlegen, Tabellen erstellen) für initiale lokale Einrichtung — **nicht** Teil des automatischen Starts.

### Offene Migration

Flyway-Integration ist als Item #17 geplant (offen). Solange Item #17 nicht umgesetzt ist, gibt es keinen Migrations-Mechanismus für Schema-Änderungen nach dem ersten Start. Eine spätere Flyway-Migration müsste:

1. `V1__initial_schema.sql` — entspricht dem aktuellen `schema.sql`-Inhalt
2. `V2__add_indexes.sql` — fehlende Indizes auf Filter-/FK-Spalten (siehe oben)
3. `spring.datasource.initialization-mode` deaktivieren (auf `never` setzen)
