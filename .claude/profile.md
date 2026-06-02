# Projekt-Profil — climatedataanalyser
# Adoptiert via /agent-flow:adopt (autonomer Lauf) am 2026-05-31

language: [java, ts]                            # Multi-Lang (Maven <modules> in pom.xml: climatedataanalyser-api=Java + climatedataanalyser-ng=TS/Angular, PR-K)
domains: []
build: maven                                    # auto-detected from ./pom.xml (root), confirmed 2026-05-31
frameworks:
  - "spring-boot@2"                             # auto-detected from pom.xml:8-13 spring-boot-starter-parent v2.6.6
  - "angular@13"                                # auto-detected from climatedataanalyser-ng/package.json @angular/core ~13.3.2
db_dialect: mysql                               # auto-detected from climatedataanalyser-api/pom.xml mysql-connector-java (legacy coords, B7-Fix), confirmed 2026-05-31
companions: []
db_migration_tool: flyway                       # flyway-core (BOM-managed 8.0.5 via spring-boot-starter-parent 2.6.6) — migriert von skeleton (Item #17)

test: "mvn -B -ntp test"
lint: "mvn -B -ntp -DskipTests verify"
smoke: "curl -fsS -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/health || true"
merge_policy: pr
default_branch: master                            # Org-Fork von Alexstuder/climatedataanalyser → Default ist master (nicht main). /flow nutzt das für PR-Base, direct-Push und CI-Watch. Fork-PRs IMMER mit `gh pr create --repo <org-fork>` (origin-URL), sonst zielt gh aufs Upstream-Parent → "Resource not accessible by integration".
board: 9                                          # Org-Project https://github.com/orgs/Studis-Softwareschmiede/projects/9 (angelegt 2026-06-01 nach GitHub-App-Auth-Wechsel via ensure-gh-auth.sh)
deploy: docker
image: ghcr.io/studis-softwareschmiede/climatedataanalyser
registry: ghcr
container_port: 8092                              # from Dockerfile EXPOSE
preview_port: 8080                                # first free host port ab 8080 (persisted by /preview up 2026-06-02)

# Validate-Cache (Phase 5 — End-to-End-Smoke: Flyway V1→V2 auf frischer DB beim App-Boot)
adoption_validated_at: 2026-06-02               # re-validate PASS: Flyway V1→V2 auf frischer DB beim App-Boot, / →200 (Skeleton-Leftover db_scripts/ entfernt, #17-Nachzug)
adoption_validated_dialect: mysql
adoption_validated_companions: []
adoption_validated_migration_tool: flyway       # Skeleton-Runner entfernt; Schema-Hoheit liegt bei Flyway (climatedataanalyser-api/.../db/migration)
