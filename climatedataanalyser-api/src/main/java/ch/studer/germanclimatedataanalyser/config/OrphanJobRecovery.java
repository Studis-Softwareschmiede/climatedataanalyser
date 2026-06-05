package ch.studer.germanclimatedataanalyser.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Verwaiste-Job-Recovery beim App-Start.
 *
 * <p>Wird die App mitten in einem Load-Job beendet (Crash / Redeploy / Container-Neustart),
 * bleibt der zugehörige BATCH_JOB_EXECUTION-Eintrag für immer im Status STARTED/STARTING —
 * obwohl real kein Job mehr läuft. Das blockiert dauerhaft den Single-Job-Anker
 * ({@code batchImportStart}/{@code clear} liefern dann ewig 409) und zeigt im UI einen
 * eingefrorenen "läuft"-Zustand.
 *
 * <p>Da beim App-Start definitiv kein Job aus einer früheren Instanz mehr ausgeführt wird
 * (Spring Batch resumed STARTED-Jobs nicht automatisch), werden solche Reste hier sauber
 * auf FAILED gesetzt → Guard wieder frei, UI zeigt einen ehrlichen "fehlgeschlagen"-Zustand.
 */
@Component
public class OrphanJobRecovery {

    private static final Logger log = LoggerFactory.getLogger(OrphanJobRecovery.class);

    private final JdbcTemplate jdbcTemplate;

    public OrphanJobRecovery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void markOrphanedJobsFailed() {
        try {
            int steps = jdbcTemplate.update(
                "UPDATE BATCH_STEP_EXECUTION SET STATUS='FAILED', " +
                "END_TIME=COALESCE(END_TIME, NOW()) WHERE STATUS IN ('STARTED','STARTING')");
            int jobs = jdbcTemplate.update(
                "UPDATE BATCH_JOB_EXECUTION SET STATUS='FAILED', EXIT_CODE='FAILED', " +
                "END_TIME=COALESCE(END_TIME, NOW()) WHERE STATUS IN ('STARTED','STARTING')");
            if (jobs > 0) {
                log.warn("Verwaiste Job-Reste beim Start bereinigt: {} Job(s), {} Step(s) → FAILED " +
                        "(App war mitten in einem Lauf beendet worden).", jobs, steps);
            }
        } catch (Exception e) {
            // BATCH_*-Tabellen evtl. noch nicht da (allererster Start vor Flyway-Job-Schema)
            // oder sonstiger transienter Fehler → nicht den App-Start blockieren.
            log.warn("Orphan-Job-Recovery übersprungen: {}", e.getMessage());
        }
    }
}
