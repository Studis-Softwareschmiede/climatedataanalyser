package ch.studer.germanclimatedataanalyser.controller;

import ch.studer.germanclimatedataanalyser.batch.listener.SkippedRecordTracker;
import ch.studer.germanclimatedataanalyser.model.dto.db.DbLoadResponseDto;
import ch.studer.germanclimatedataanalyser.service.ui.dbController.DbLoadInformationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@RestController
@RequestMapping("/api/database")
public class DataBaseController {

    private final JobLauncher jobLauncher;
    private final DbLoadInformationService dbLoadInformationService;
    private final Job job;
    private final JdbcTemplate jdbcTemplate;
    private final SkippedRecordTracker skippedRecordTracker;

    // Datei-Counts werden RELATIV zum Arbeitsverzeichnis gezählt — exakt dort, wohin der
    // ClimateFtpDataDownloader/-Unziper schreibt (climate.path.downloadFolder, default "download").
    // Vorher absolut hartkodiert ("/download/FTPData") → zielte am echten Ort (/app/data/download/…)
    // vorbei → ftpData immer 0.
    @Value("${climate.path.downloadFolder}")
    private String downloadFolder;
    @Value("${climate.path.ftpDataFolderName}")
    private String ftpDataFolder;
    @Value("${climate.path.unzipOutputFolderName}")
    private String unzipOutputFolder;
    @Value("${climate.path.inputFolderName}")
    private String inputFolder;

    public DataBaseController(JobLauncher jobLauncher, DbLoadInformationService dbLoadInformationService,
                              Job job, JdbcTemplate jdbcTemplate, SkippedRecordTracker skippedRecordTracker) {
        this.jobLauncher = jobLauncher;
        this.dbLoadInformationService = dbLoadInformationService;
        this.job = job;
        this.jdbcTemplate = jdbcTemplate;
        this.skippedRecordTracker = skippedRecordTracker;
    }

    private static final Logger log = LoggerFactory.getLogger(DataBaseController.class);

    // Reihenfolge wichtig: Spring-Batch FKs zuerst (Children → Parents), dann App-Daten.
    private static final List<String> TABLES_TO_TRUNCATE = List.of(
        "BATCH_STEP_EXECUTION_CONTEXT",
        "BATCH_STEP_EXECUTION",
        "BATCH_JOB_EXECUTION_CONTEXT",
        "BATCH_JOB_EXECUTION_PARAMS",
        "BATCH_JOB_EXECUTION",
        "BATCH_JOB_INSTANCE",
        "CLIMATE",
        "MONTH_",
        "STATION",
        "WEATHER"
    );

    @GetMapping("/batchImportStart")
    public void handle(@RequestParam(value = "withFTP", defaultValue = "false") String withFTP) throws Exception {

        JobParameters jobParameters =
                new JobParametersBuilder()
                        .addLong("time", System.currentTimeMillis())
                        .addString("withFTP", withFTP)
                        .toJobParameters();
        jobLauncher.run(job, jobParameters);

    }

    @GetMapping("/")
    DbLoadResponseDto dbLoadInformationRequest() {
        DbLoadResponseDto dto = this.dbLoadInformationService.getDbLoadInformation();
        dto.setFileCounts(collectFileCounts());
        dto.setElapsedSeconds(computeElapsedSeconds());
        // Skipped-Records-Bericht: aktueller (= letzter) Job
        Long jobId = currentJobExecutionId();
        if (jobId != null) {
            dto.setSkippedRecords(skippedRecordTracker.getForJob(jobId));
        }
        return dto;
    }

    /**
     * Laufzeit des aktuellen (= letzten) Job-Runs in Sekunden — läuft er noch, gegen NOW(),
     * sonst die fixe Gesamtdauer (END_TIME − START_TIME). DB-seitig gerechnet (Server-Zeit →
     * kein Client/Server-Uhren-Skew). null, wenn noch nie ein Job lief.
     */
    private Long computeElapsedSeconds() {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT START_TIME, END_TIME FROM BATCH_JOB_EXECUTION " +
                "WHERE JOB_EXECUTION_ID = (SELECT MAX(JOB_EXECUTION_ID) FROM BATCH_JOB_EXECUTION)");
            LocalDateTime start = toLocalDateTime(row.get("START_TIME"));
            if (start == null) return null;
            // START_TIME wird über die Connection (serverTimezone=Europe/Berlin) als
            // Berlin-wall-clock gespeichert — DB und JVM laufen aber in UTC. "now" daher
            // ebenfalls in Europe/Berlin rechnen, sonst 2h-Offset (negativ → auf 0 geclampt).
            // END_TIME ist gleich gespeichert → für fertige Jobs konsistent.
            LocalDateTime end = toLocalDateTime(row.get("END_TIME"));
            LocalDateTime reference = (end != null)
                    ? end
                    : LocalDateTime.now(java.time.ZoneId.of("Europe/Berlin"));
            long secs = java.time.Duration.between(start, reference).getSeconds();
            return Math.max(0, secs);
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime) return (LocalDateTime) value;
        if (value instanceof java.sql.Timestamp) return ((java.sql.Timestamp) value).toLocalDateTime();
        return null;
    }

    private Long currentJobExecutionId() {
        try {
            Integer id = jdbcTemplate.queryForObject(
                "SELECT MAX(JOB_EXECUTION_ID) FROM BATCH_JOB_EXECUTION", Integer.class);
            return id != null ? id.longValue() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Integer> collectFileCounts() {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("ftpData", countFilesIn(downloadFolder + "/" + ftpDataFolder));
        counts.put("unzipedFiles", countFilesIn(downloadFolder + "/" + unzipOutputFolder));
        counts.put("inputFiles", countFilesIn(downloadFolder + "/" + inputFolder));
        return counts;
    }

    private int countFilesIn(String pathStr) {
        try {
            java.io.File dir = new java.io.File(pathStr);
            if (!dir.exists() || !dir.isDirectory()) return 0;
            String[] entries = dir.list();
            return entries != null ? entries.length : 0;
        } catch (Exception e) {
            log.warn("countFilesIn({}) failed: {}", pathStr, e.getMessage());
            return 0;
        }
    }

    /**
     * Truncate aller App- und Spring-Batch-Meta-Tabellen — Vorbereitung für sauberen Re-Load.
     * Reset auch der Auto-Increment-Sequenzen.
     *
     * <p>POST /api/database/clear → {cleared: [<table>, ...], counts: {<table>: <new_count>}}
     * <p>Sicherheit: kein Auth-Check (lokales Tool) — produktiv würde @PreAuthorize hinzukommen.
     */
    @PostMapping("/clear")
    @Transactional
    public Map<String, Object> clear() {
        log.info("=== TRUNCATE TABLES requested via /api/database/clear ===");

        // FK-Checks aushebeln (MySQL/MariaDB) — sonst FK-Constraint-Verletzungen zwischen BATCH_*-Tables
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");

        Map<String, Object> result = new HashMap<>();
        Map<String, Integer> countsBefore = new HashMap<>();
        Map<String, Integer> countsAfter = new HashMap<>();

        try {
            for (String table : TABLES_TO_TRUNCATE) {
                try {
                    Integer before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
                    countsBefore.put(table, before != null ? before : 0);
                    jdbcTemplate.execute("TRUNCATE TABLE " + table);
                    Integer after = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
                    countsAfter.put(table, after != null ? after : 0);
                    log.info("  TRUNCATE {} — before={}, after={}", table, before, after);
                } catch (Exception e) {
                    log.warn("  TRUNCATE {} failed: {} (table may not exist yet — skipped)", table, e.getMessage());
                    countsAfter.put(table, -1);
                }
            }
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        }

        // In-Memory Skip-Tracker auch leeren — sonst zeigt der nächste Job-Report
        // Skipped-Records vom alten Run an.
        skippedRecordTracker.clearAll();

        result.put("cleared", TABLES_TO_TRUNCATE);
        result.put("countsBefore", countsBefore);
        result.put("countsAfter", countsAfter);
        result.put("message", "All app and Spring-Batch tables truncated.");
        log.info("=== CLEAR DONE — counts: before={} after={} ===", countsBefore, countsAfter);
        return result;
    }

}
