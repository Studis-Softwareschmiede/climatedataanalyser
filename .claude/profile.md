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
board: <wird in Phase 4 gesetzt>
deploy: docker
image: ghcr.io/studis-softwareschmiede/climatedataanalyser
registry: ghcr
