package ch.studer.germanclimatedataanalyser.batch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-Memory Tracker für übersprungene Records (Spring-Batch SkipListener-Pattern).
 * Pro Job-Execution-ID wird eine Liste von SkippedRecord-Einträgen geführt.
 *
 * <p>Persistenz: nur in-memory. App-Restart → Liste leer (akzeptabel: Restart
 * setzt eh alle laufenden Jobs auf FAILED, der vorherige Run ist abgeschlossen
 * und wird über die normale Job-History gefunden).
 *
 * <p>Truncate: getClearAll() leert die Map — aufgerufen vom /api/database/clear.
 */
@Component
public class SkippedRecordTracker {

    private static final Logger log = LoggerFactory.getLogger(SkippedRecordTracker.class);

    /** jobExecutionId → list of skips */
    private final Map<Long, List<SkippedRecord>> bucket = new ConcurrentHashMap<>();

    public void add(Long jobExecutionId, String stepName, Throwable t) {
        SkippedRecord r = new SkippedRecord();
        r.stepName = stepName;
        r.message = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
        // FlatFileParseException-Spezifika extrahieren (file + line stehen im message-String,
        // aber wenn die konkrete Exception die richtigen Getter hat, holen wir's strukturiert).
        try {
            if (t instanceof org.springframework.batch.infrastructure.item.file.FlatFileParseException ffpe) {
                r.lineNumber = ffpe.getLineNumber();
                r.input = ffpe.getInput();
            }
        } catch (Exception ignored) {
            // best-effort — message-Field hat eh schon die Info
        }
        bucket.computeIfAbsent(jobExecutionId, k -> new ArrayList<>()).add(r);
        log.warn("[SKIP] job={}, step={}, line={}, input='{}'",
                jobExecutionId, stepName, r.lineNumber, r.input);
    }

    public List<SkippedRecord> getForJob(Long jobExecutionId) {
        return bucket.getOrDefault(jobExecutionId, List.of());
    }

    public void clearAll() {
        log.info("[SKIP-TRACKER] cleared {} job entries", bucket.size());
        bucket.clear();
    }

    public static class SkippedRecord {
        public String stepName;
        public Integer lineNumber;
        public String input;
        public String message;

        public String getStepName() { return stepName; }
        public Integer getLineNumber() { return lineNumber; }
        public String getInput() { return input; }
        public String getMessage() { return message; }
    }
}
