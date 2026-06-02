-- ##########################
-- V2: ENGINE=InnoDB + utf8mb4 + Indizes
-- Läuft auf frischen DBs (nach V1) UND auf der bestehenden baselineten DB mit Daten.
-- Kein DROP, kein DELETE — ALTER/CREATE INDEX sind datensicher.
-- ##########################

-- ---- ENGINE + CHARSET ----

ALTER TABLE `STATION`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `WEATHER`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `CLIMATE`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `MONTH_`
    ENGINE = InnoDB,
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ---- Indizes ----

-- STATION: Lookup nach STATION_ID (FK-Spalte, kein UNIQUE-Constraint in V1)
CREATE INDEX idx_station_stationid ON `STATION` (STATION_ID);
-- STATION: Filter nach Bundesland
CREATE INDEX idx_station_bundesland ON `STATION` (BUNDES_LAND);

-- WEATHER: Join/Filter via STATION_ID
CREATE INDEX idx_weather_stationid ON `WEATHER` (STATION_ID);

-- CLIMATE: Join/Filter via STATION_ID
CREATE INDEX idx_climate_stationid ON `CLIMATE` (STATION_ID);
-- CLIMATE: Filter/Order nach Periode
CREATE INDEX idx_climate_endperiod ON `CLIMATE` (END_PERIOD);

-- MONTH_: Join/Filter via STATIONS_ID
CREATE INDEX idx_month_stationsid ON `MONTH_` (STATIONS_ID);
