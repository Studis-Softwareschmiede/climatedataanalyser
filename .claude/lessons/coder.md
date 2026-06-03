# Coder Lessons — climatedataanalyser (newest first)

## 2026-06-02 — Flyway einführen: spring.jpa.hibernate.ddl-auto explizit setzen

Wenn Flyway als Migration-Tool eingeführt wird und `initialization-mode`/`schema.sql` entfernt
werden, MUSS gleichzeitig `spring.jpa.hibernate.ddl-auto=validate` (oder `=none`) explizit in
`application.properties` gesetzt werden. Spring-Boot setzt ohne expliziten Wert je nach
Datasource-Typ einen impliziten Default. Mit Flyway aktiv ist `validate` die sicherste Wahl:
Hibernate prüft das Schema gegen die Entities, aber verändert nichts — Flyway ist der einzige
Schema-Mutator. `update` oder `create` zusammen mit Flyway ist doppeltes Schema-Management
und führt zu Inkonsistenzen.

## 2026-06-02 — `as keyof typeof Enum` ist ein unsicherer Cast bei unkontrollierten Backend-Strings

`DbStatus[value.body.isDbLoaded as keyof typeof DbStatus]` silenziert TS7053 per Cast,
maskiert aber das Laufzeit-Risiko: Wenn das Backend einen unbekannten String liefert,
liefert der Enum-Index-Zugriff `undefined`, obwohl TypeScript `DbStatus` annimmt.

Korrekteres Muster:
```ts
const key = value.body.isDbLoaded as string;
const status: DbStatus | undefined =
  Object.prototype.hasOwnProperty.call(DbStatus, key)
    ? DbStatus[key as keyof typeof DbStatus]
    : undefined;
this.currentDbLoadStatus = status ?? null;
```
Oder wenn der Wert ein numerischer Enum-Wert (nicht der Key) ist, `Number()` +
`Object.values(DbStatus).includes(n)` prüfen.
Gilt analog für alle Enum-Lookups mit Backend-Strings.

## 2026-06-02 — takeUntil schützt NICHT, wenn die Methode via setTimeout als bare function reference aufgerufen wird

`setTimeout(this.initClimates, 1000)` übergibt die Methode ohne gebundenen `this`-Kontext.
Wenn der Callback feuert, ist `this` entweder `undefined` (strict-mode) oder `window` (sloppy) —
`this.destroy$` existiert nicht, `takeUntil(this.destroy$)` wirft einen TypeError, und die
HTTP-Subscription entkommt dem Cleanup vollständig.

Korrekte Schreibweisen:
- Arrow-Wrapper: `setTimeout(() => this.initClimates(), 1000)` — erhält den lexikalischen `this`-Kontext.
- Gebundene Referenz: `setTimeout(this.initClimates.bind(this), 1000)`.

Gilt analog für alle Callbacks, die eine Instanz-Methode als reinen Funktionswert übergeben
(Array.prototype.map, EventEmitter.on, requestAnimationFrame, etc.).

## 2026-06-02 — IDENTITY-Strategie hebt JDBC-Batching still auf

`@GeneratedValue(strategy = GenerationType.IDENTITY)` deaktiviert Hibernate-seitig das JDBC-Batch-INSERT
automatisch, weil nach jedem INSERT die DB-generierte ID per `getGeneratedKeys()` abgefragt werden muss.
`spring.jpa.properties.hibernate.jdbc.batch_size` hat für solche Entities **keine Wirkung**.

Konsequenzen:
- `flush()`/`clear()` im Loop ist trotzdem sinnvoll und korrekt (L1-Cache-Begrenzung).
- `order_inserts=true` ist harmlos, aber ebenfalls wirkungslos.
- Wenn echtes JDBC-Batching benötigt wird: Strategie auf `GenerationType.SEQUENCE` + `allocationSize`
  wechseln. Das erfordert ein Schema-Change (Sequence-Objekt in der DB) und ist ein separates Item.
- Kommentare in `application.properties` dürfen keine Batch-INSERT-Wirkung versprechen, wenn IDENTITY
  greift — das ist irreführend für spätere Lesende.

## 2026-06-02 — Constructor-Injection: @Value-Felder bleiben non-final, kein null-Konstruktor für Unit-Tests

Bei der Umstellung von Field-Injection auf Constructor-Injection:
- `@Value`-annotierte Felder dürfen NICHT `final` werden — Spring injiziert sie nach der Konstruktion via
  BeanPostProcessor (AutowiredAnnotationBeanPostProcessor), nicht via Konstruktor. `final` würde den
  Build brechen.
- Für Unit-Tests, die nur reine Berechnungslogik testen (keine DAO-Aufrufe), ist `new Impl(null)` für
  den DAO-Parameter + `ReflectionTestUtils.setField(...)` für die `@Value`-Felder das korrekte Muster.
  Nicht einen separaten No-Arg-Konstruktor einführen — das verwirrt die Intent.
- Klassen, die SOWOHL einen `@Bean`-Factory-Eintrag im ApplicationContext hatten ALS AUCH ein
  Stereotype (@Service/@Component/@Repository) tragen, wurden vor diesem Refactor doppelt registriert
  (einmal per Component-Scan, einmal per @Bean). Die Lösung ist: entweder Stereotype entfernen (wenn
  @Bean die einzige Registration sein soll) oder den @Bean-Eintrag entfernen (wenn Component-Scan reicht).
  Niemals beides gleichzeitig aktiv lassen.

## 2026-06-02 — try-with-resources-Refactor: Cause-Chain im angefassten catch-Block mitfixen

Wenn ein catch-Block im Rahmen eines try-with-resources-Refactors umstrukturiert wird
und darin ein `throw new RuntimeException("msg : " + e)` steht, MUSS die Cause-Chain
beim gleichen Commit korrigiert werden: `throw new RuntimeException("msg", e)`.
Den Exception-Parameter via String-Konkatenation (`+ e`) in die Nachricht einzubauen
verliert den Stack-Trace des Originals (nur `e.toString()` landet im Message-String).
AC "Cause-Chain bei rethrow erhalten" gilt für jeden im Diff berührten catch-Block,
auch wenn der throw-Ausdruck selbst schon vorher existierte.

## 2026-06-02 — docker-compose: `${VAR}` in `environment:` wird NICHT aus `env_file:` aufgelöst

`env_file:` injiziert Variablen nur in den Container-Prozess zur Laufzeit.
`${VAR}`-Interpolation in `environment:`, `ports:`, `volumes:` etc. findet zur **Parse-Zeit** statt
und liest aus: (1) Shell-Umgebung, (2) `.env`-Datei im CWD, (3) `--env-file`-Flag — NICHT aus `env_file:`.

Folgen: `SPRING_DATASOURCE_URL: "jdbc:mysql://db/${MARIADB_DATABASE}"` wird zu
`jdbc:mysql://db/` (leerer DB-Name), wenn `.env.db` nur per `env_file:` eingebunden ist.

Lösungen (Priorität):
1. Literal statt Variable für nicht-geheime Werte (`CLIMATE` direkt in URL — kein Secret).
2. `.env`-Symlink oder `--env-file .env.db` dokumentieren als Pflicht-Operator-Schritt.
3. Credentials als separate, direkte `environment:`-Werte ohne `${VAR}`-Interpolation
   (nur wenn der Wert wirklich per env_file zur Laufzeit aufgelöst werden soll).

## 2026-06-02 — `.env.db` fehlt im `.gitignore` → Pflicht-Eintrag

Jede `.env`-Datei, die echte Secrets aufnehmen kann (auch wenn nur per `cp example → real`),
muss im `.gitignore` stehen BEVOR der erste Commit entstehen kann.
Minimal-Eintrag: `.env.db` (oder Glob `.env.*` für alle Projekt-Envs, außer `*.example`).

## 2026-06-02 — Docker WAR-Selektion: Spring-Boot-repackaged vs. Original

Bei Spring-Boot-WAR-Projekten mit einem custom `<warName>` im `maven-war-plugin` erzeugt der Build
ZWEI WARs im `target/`-Verzeichnis:
- `<warName>.war` (kleiner, plain WAR, **kein** `Main-Class` im Manifest) → NICHT via `java -jar` lauffähig.
- `<artifactId>-<version>.war` (größer, Spring-Boot-repackaged, `Main-Class: org.springframework.boot.loader.launch.WarLauncher`) → die **ausführbare** WAR.

Im Dockerfile IMMER das repackaged Artefakt kopieren — entweder per versioniertem Namen
oder vorzugsweise mit einem Glob (`COPY --from=build /build/.../target/*-SNAPSHOT.war /app.war`
oder besser `*[^original].war` wenn CI die Version kennt).

Empfehlung: `<classifier>` in `spring-boot-maven-plugin` setzen (z.B. `exec`), dann
heißt die ausführbare WAR eindeutig `<warName>-exec.war` und die plain WAR bleibt `<warName>.war`.

## 2026-06-02 — Hardcoded DB-Credentials in application.properties verhindern Image-Secret-Freiheit

`spring.datasource.password` mit Klartext-Passwort in `src/main/resources/application.properties`
wird in die WAR eingebacken (Classpath-Resource). Das Dockerfile MUSS entweder:
(a) eine eigene `application-docker.properties` per `COPY` einspielen, die nur Platzhalter enthält, oder
(b) alle Credential-Properties als Spring-Env-Override via `-e SPRING_DATASOURCE_PASSWORD=…` übersteuern
    und explizit im Dockerfile-Kommentar dokumentieren, dass die Properties im Image durch Env überschrieben werden.
Klartext-Passwort in Classpath-Resource ist `security/R01`-kritisch.

## 2026-06-02 — spring-boot-devtools gehört NICHT ins Production-Image

`spring-boot-devtools` ohne `<optional>true</optional>` oder `<scope>provided</scope>` wird in die WAR
eingepackt. Im Container aktiviert Devtools automatisch Remote-Restart-Support auf Port 8092
(sofern nicht explizit deaktiviert), öffnet ggf. zusätzliche Debug-Endpunkte und erhöht die Image-Größe.
Korrekte Konfiguration: `<optional>true</optional>` in der Dependency, dann schließt
spring-boot-maven-plugin Devtools aus dem repackaged WAR aus.

## 2026-06-02 — RUN useradd VOR COPY/WORKDIR-Operationen auf Nutzer-Dateien ausführen

Im Dockerfile muss `RUN useradd …` IMMER VOR der letzten `COPY`-Anweisung stehen, die Dateien
ablegt, die dem App-User gehören sollen. Reihenfolge: COPY → RUN chown ODER RUN useradd → COPY.
Beim aktuellen Pattern (COPY dann useradd dann USER) besitzt `/app.war` root:root,
was bei manchen Deployments zu Problemen führt (z.B. wenn die App die Datei selbst lesen muss).
In der Praxis ist das für `java -jar` unkritisch (leserechte für other reichen), aber explizit
sauberer ist: RUN useradd vor COPY oder danach per RUN chown.
