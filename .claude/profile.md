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
db_migration_tool: skeleton                     # kein flyway/liquibase in pom.xml gefunden; Default-Mapping (§5) wäre flyway@10 — explizit auf skeleton gesetzt (Backlog-Item: Migration zu flyway@10 prüfen, Important)

test: "mvn -B -ntp test"
lint: "mvn -B -ntp -DskipTests verify"
smoke: "curl -fsS -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/health || true"
merge_policy: pr
default_branch: master                            # Org-Fork von Alexstuder/climatedataanalyser → Default ist master (nicht main). /flow nutzt das für PR-Base, direct-Push und CI-Watch. Fork-PRs IMMER mit `gh pr create --repo <org-fork>` (origin-URL), sonst zielt gh aufs Upstream-Parent → "Resource not accessible by integration".
board: 9                                          # Org-Project https://github.com/orgs/Studis-Softwareschmiede/projects/9 (angelegt 2026-06-01 nach GitHub-App-Auth-Wechsel via ensure-gh-auth.sh)
deploy: docker
image: ghcr.io/studis-softwareschmiede/climatedataanalyser
registry: ghcr

# Validate-Cache (Phase 5 — End-to-End-Smoke gegen db_scripts/000_init_meta.sql)
adoption_validated_at: 2026-05-31T20:45:35Z
adoption_validated_dialect: mysql
adoption_validated_companions: []
adoption_validated_migration_tool: skeleton
