package ch.studer.germanclimatedataanalyser.controller;

import ch.studer.germanclimatedataanalyser.model.dto.db.DbLoadResponseDto;
import ch.studer.germanclimatedataanalyser.service.ui.dbController.DbLoadInformationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@RestController
@RequestMapping("/api/database")
@CrossOrigin
public class DataBaseController {

    @Autowired
    JobLauncher jobLauncher;

    @Autowired
    DbLoadInformationService dbLoadInformationService;

    @Autowired
    Job job;

    @Autowired
    JdbcTemplate jdbcTemplate;

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
        return this.dbLoadInformationService.getDbLoadInformation();
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

        result.put("cleared", TABLES_TO_TRUNCATE);
        result.put("countsBefore", countsBefore);
        result.put("countsAfter", countsAfter);
        result.put("message", "All app and Spring-Batch tables truncated.");
        log.info("=== CLEAR DONE — counts: before={} after={} ===", countsBefore, countsAfter);
        return result;
    }

}
