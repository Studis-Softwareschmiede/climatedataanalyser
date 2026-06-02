# UpgradePlan — 2026-06-02-upgrade

> Spec-Typ: **UpgradePlan** (vom `/upgrade`-Skill erzeugt, Phase D). Durable Source of Truth. `coder` baut daraus, `tester` testet gegen die AC, `reviewer` prüft dagegen (Drift-Gate). Bindender Hintergrund: `docs/architecture/upgrade-subsystem.md` (in agent-flow).

- **Spec-ID:** `upgrade-2026-06-02-upgrade`
- **Status:** executing
- **Erzeugt:** 2026-06-02 · **Solver-Quelle:** Pack-Header-Constraints (`frameworks/`, `migration/`, `java`, `build/maven`) + Web-Fallback
- **Re-Validate-Vorbedingung:** erfüllt (`adoption_validated_at: 2026-06-02`, Flyway-Schema-Hoheit, `/` →200)

## 1. Zweck
Modernisierung des Stacks (Java-API + Angular-Frontend) auf den neuesten kompatiblen, sicheren, lauffähigen Stand — **eine Major-Stufe je Achse**, jede Stufe gegate-bar via `/flow` (coder → reviewer ⇄ tester → land).

## 2. Ist → Ziel (Solver-Ergebnis)

| Achse | Ist | Ziel | Begründung (Solver) | Quelle |
|---|---|---|---|---|
| language (java) | 11 | **21 (LTS)** | SB3-Floor `java≥17`, SB4-Floor `java≥17`, flyway-10-Floor `java≥17`; JDK 21 von `spring-boot-3/A02` empfohlen, von SB4 voll unterstützt. **Interleaved** (s.u.): erst 11→17 (von SB 2.6 toleriert), dann 17→21 (mit SB3). | spring-boot-3/A02, spring-boot-4/A01-02 |
| build (maven) | 3.9.9 | **3.9.9 (unverändert)** | erfüllt SB4-Floor `maven≥3.6.3` bereits — kein Bump nötig | spring-boot-4 `requires.build.maven≥3.6.3` |
| frameworks[spring-boot] | 2.6.6 | **4.x** | neueste GA (4.0 GA 2025-11-20, kein Preview); Leiter 2.6→3.x→4.x | spring-boot-4 Header (GA), spring-boot-3 |
| frameworks[angular] | 13.3.2 | **21.x** | neueste supportete Major (Release 2025-11-20); Leiter 13→14→…→21 (je Major `ng update`) | angular-21 Header |
| language (ts) | 4.6.3 | **5.9.x** | getrieben von `angular-21.requires.typescript ">=5.9 <6.0"` | angular-21 Header |
| node (build-toolchain) | frontend-maven-plugin-pinned | **22.12+ (LTS)** | `angular-21.requires.node "^20.19 \|\| ^22.12 \|\| ^24"` → Node 22 LTS gewählt | angular-21 Header |
| db_migration_tool (flyway) | 8.0.5 | **10.x** | BOM-managed: SB3-BOM→Flyway 9, SB4-BOM→Flyway 10; flyway-10-Floor `java≥17` durch JDK 21 erfüllt | spring-boot-4 `compatible_with.migration.flyway≥10`, flyway-10/A01 |
| db_dialect (mysql/MariaDB) | mysql | unverändert | reiner Versions-Scope, kein Dialekt-Wechsel | — |
| container_runtime | tomcat (SB-Default) | unverändert | **nicht** Undertow → SB4-Ausschluss `container=undertow` greift nicht | spring-boot-4 `incompatible` |

**Aufgelöste Konflikte:** Sprach-Floor-Kopplung — SB 2.6.6 läuft **nicht** auf JDK 21, SB3 fordert aber JDK ≥17. Aufgelöst durch **Interleaving** der Sprach- und Framework-Achse statt strikt „language first": JDK 11→17 (SB-2.6-tolerant) **vor** SB→3, JDK 17→21 **mit** SB3, bevor SB→4.

**Bump-Reihenfolge (`order[]`, interleaved):**
`java 11→17  →  spring-boot 2.6→3.x  →  java 17→21  →  spring-boot 3.x→4.x  →  flyway→10 (BOM-verify)` ‖ **parallel & unabhängig:** `angular 13→…→21` (eigenes Modul, eigene Toolchain).

**Unlösbar (`conflicts[]`):** keine.

## 3. Wissenslücken (Phase E)
**Keine.** Alle Ziel-Packs vorhanden: `java`, `frameworks/spring-boot-4`, `frameworks/angular-21`, `migration/flyway-10`, `build/maven`. Angular-Zwischen-Majors (14–20) brauchen **kein** eigenes Pack (Gap-Regel gilt nur für fehlende **Ziel**-Packs; Zwischenstufen laufen über offizielles `ng update` + Source-Pack `angular-13` / Target-Pack `angular-21`). → Kein `train --bootstrap`.

## 4. Leiter — Acceptance-Kriterien pro Stufe
> Jede Stufe = **ein Board-Item** (`Depends-on` auf die vorige + Solver-Vorbedingungen). „Build + Tests grün" ist Pflicht pro Stufe.

### Achse language + frameworks[spring-boot] (interleaved Backend-Leiter)
- `AC-L1:` **java 11 → 17** — `<java.version>17`, Dockerfile-Base-Image + CI `java-version: 17` gepinnt; Build (`mvn -B -ntp verify`) + Tests grün auf SB 2.6.6/JDK 17. *(SB 2.6 unterstützt JDK 17 — sicherer Vorlauf-Schritt.)*
- `AC-F1:` **spring-boot 2.6 → 3.x** *(dep AC-L1)* — Zwischenschritt 2.6→2.7 (letzte 2.x), dann 3.x: `javax.*`→`jakarta.*` (Persistence/Servlet/Validation), **Spring Batch 5** API-Migration (`spring-boot-starter-batch`), `mysql:mysql-connector-java`→`com.mysql:mysql-connector-j`, Flyway-BOM 9. Build + Tests grün. (spring-boot-3/A01, A02)
- `AC-L2:` **java 17 → 21** *(dep AC-F1)* — Toolchain/CI/Dockerfile auf JDK 21 (LTS); Build + Tests grün auf SB3/JDK 21.
- `AC-F2:` **spring-boot 3.x → 4.x** *(dep AC-F1, AC-L2)* — SB-4-Migration-Guide, Jakarta EE 11/Servlet 6.1, Flyway-BOM 10; Build + Tests grün.
- `AC-M1:` **flyway → 10 (verify)** *(dep AC-F2, Label `db` → DBA-Review)* — durch SB4-BOM bereits auf 10; Migrations-Apply V1→V2 grün auf frischer DB, Marker/`flyway_schema_history` ok, App `/` →200.

### Achse frameworks[angular] (Major-Leiter, parallel zum Backend)
- `AC-NG1:` **angular 13 → 14** — `ng update @angular/core@14 @angular/cli@14` + Migrations-Schematics; Build + Tests grün.
- `AC-NG2:` 14 → 15 · `AC-NG3:` 15 → 16 · `AC-NG4:` 16 → 17 · `AC-NG5:` 17 → 18 · `AC-NG6:` 18 → 19 · `AC-NG7:` 19 → 20 · `AC-NG8:` **20 → 21** (je `ng update` + Schematics, Build + Tests grün; TS schrittweise → 5.9, Node → 22 LTS).
- `AC-NG9 (Modernisierung, optional, dep AC-NG8):` Standalone-/Control-Flow-/`inject()`-Schematics anwenden; Build + Tests grün. (Adressiert zugleich Issue #2: 8 HIGH-CVEs in @angular/* 13.3.2.)

## 5. Abschluss-Kriterien (gesamter Plan)
- `AC-Z1:` voller Build + Test-Suite + Smoke grün auf SB 4.x / JDK 21 / Angular 21.
- `AC-Z2:` `profile` spiegelt Ziel-Versionen; `adoption_validated_at` invalidiert (DB-Achse via Flyway-10 berührt) → Re-Validate beim nächsten `/preview up`.
- `AC-Z3:` keine entfernten/deprecateten APIs der übersprungenen Majors (reviewer-Checklist `spring-boot-4`, `angular-21`, `flyway-10`).

## 6. Nicht-Ziele
- Kein Tool-/Framework-**Wechsel** (nur Versions-Modernisierung), keine Auto-Konvertierung.
- Kein Sprung über mehrere Majors in einem Item.
- Keine neuen user-sichtbaren Features (reiner Upgrade-Scope).

## 7. Abhängigkeiten / Risiken
- **Spring Batch 5** (SB3) ist der größte Brocken im Backend — API-Bruch (`JobBuilderFactory`/`StepBuilderFactory` deprecated/entfernt). Sichtbar als roter Build im tester-Gate von AC-F1.
- 3rd-Party-Libs müssen die Ziel-Majors unterstützen (h2, commons-net, util-compress) — pro Stufe als roter Build sichtbar.
- Angular 13→21 = 8 Major-`ng update`-Schritte; Node-Toolchain (frontend-maven-plugin) muss je Stufe mitziehen.
- **Supersedes** die Doku-Stub-Items #21 (spring-boot eol) und #22 (angular eol) und adressiert #2 (angular CVEs) konkret als ausführbare Leiter.
