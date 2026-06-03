# Upgrade-Lauf — Folge-Session 2026-06-03 — DB-Runtime-Lessons (für /retro)

Quelle: Debugging des SB-3.3.13-Preview von climatedataanalyser (FTP/„geht nicht weiter").
Aufgedeckt: eine **Klasse** von Upgrade-Defekten, die die H2-Unit-Test-Gate strukturell **nicht** sieht.
Wichtig für /retro: **G1-Trennung** — nur das **Meta-Muster** universell promoten; die Flyway-/MariaDB-/`/app`-Spezifika als belegte FAKTEN in Tool-Packs/Templates.

## Universeller Kern (repo-agnostisch, → upgrade-subsystem)
**Grüne Unit-Tests ≠ funktionierendes Upgrade**, sobald ein Rung Laufzeit-Oberflächen berührt, die Unit-Tests strukturell nicht abdecken: **echte DB (Engine + Treiber + Migrationstool), das gepackte Image, nicht-unit-getestete IO-/Batch-Pfade.** Die Abnahme muss genau diese Flächen prüfen.

## Belege aus diesem Lauf (n=1, NICHT als Universal-Regel promoten)
- **D1 (Flyway-Modul):** SB2→3 hob Flyway 8→10 (BOM). Flyway 10 hat DB-Support **modularisiert** → ohne `flyway-mysql` Boot-Fehler `Unsupported Database: MySQL/MariaDB`. OpenRewrite fügt das Modul nicht hinzu. Tests (H2 + `spring.flyway.enabled=false`) sahen es nie → **gemergtes Produktiv-Image war kaputt** (AC-F1 fälschlich „done" auf grünen H2-Tests). → **flyway-10-Pack** (Sektion A/Checklist).
- **D2 (Tool↔Engine-Version):** `mysql:8`-Tag floatet auf 8.4 → Flyway 10.10 kennt es nicht. Floating-Major-Tags vermeiden. → generisch: **Images pinnen**.
- **D3 (dialect↔Engine-Mismatch):** `db_dialect=mysql` + mysql-connector-j, aber Fabrik-Fragment deployt **MariaDB 11**; Flyway 8 tolerierte, Flyway 10 nicht. → Prinzip generisch (dialect↔Engine↔Treiber konsistent), Instanz fabrik-fragment-spezifisch. → **db-Fragment/Template**.
- **D4 (non-root writable):** Batch-FTP schreibt nach `/app/download`, Container läuft non-root, `/app` root-owned → `AccessDeniedException`. Batch-Reader/IO **nicht** unit-getestet. → Prinzip generisch (writable Data-Dir für non-root), Trigger climate-spezifisch. → **Dockerfile-Template**.
- **D5 (Preview-Infra):** DB nach Docker-Neustart vom Netz abgekoppelt; Docker-Disk durch Build-Cache voll. → **preview-Subsystem** härten (Image-Pin, Restart-Reattach, Disk-Guard, Health „DB im Netz+erreichbar").

## Promotion-Trennung (für den /retro-PR)
1. **upgrade-subsystem.md (universell):** Kern oben; DB-/migrations-/treiber-/BOM-berührende Rungs brauchen **Real-Engine-Runtime-Verify als Akzeptanzkriterium des Rungs selbst** (nicht nachgelagert); Unit-Gate-Blindfleck-Regel; Runtime-Verify als harte `Depends-on`-Vorbedingung; Images pinnen.
2. **flyway-10-Pack:** `flyway-<dialect>`-Modul ab Flyway 10 Pflicht + strengere Engine-Erkennung (belegt: D1).
3. **Templates:** Dockerfile → writable Data-Dir für non-root (D4, Prinzip); db-Fragment → dialect↔Engine↔Treiber-Konsistenz + Image-Pin (D2/D3, Prinzip).
4. **preview-Subsystem:** Resilienz/Disk-Guard/Health (D5).
