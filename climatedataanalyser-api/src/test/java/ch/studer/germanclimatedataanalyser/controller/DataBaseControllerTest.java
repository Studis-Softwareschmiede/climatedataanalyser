package ch.studer.germanclimatedataanalyser.controller;

import ch.studer.germanclimatedataanalyser.batch.listener.SkippedRecordTracker;
import ch.studer.germanclimatedataanalyser.service.ui.dbController.DbLoadInformationService;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-Tests für den Single-Job-Anker in {@link DataBaseController}:
 * läuft bereits ein Job (BATCH_JOB_EXECUTION-Status STARTED/STARTING), darf
 * weder ein zweiter gestartet (batchImportStart) noch truncated (clear) werden.
 */
class DataBaseControllerTest {

    private static final String RUNNING_COUNT_SQL =
        "SELECT COUNT(*) FROM BATCH_JOB_EXECUTION WHERE STATUS IN ('STARTED','STARTING')";

    private DataBaseController controller(JobLauncher launcher, JdbcTemplate jdbc) {
        return new DataBaseController(
                launcher,
                mock(DbLoadInformationService.class),
                mock(Job.class),
                jdbc,
                mock(SkippedRecordTracker.class));
    }

    @Test
    void batchImportStart_startetKeinenZweitenJobWennEinerLaeuft() throws Exception {
        JobLauncher launcher = mock(JobLauncher.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(eq(RUNNING_COUNT_SQL), eq(Integer.class))).thenReturn(1);

        ResponseEntity<Map<String, Object>> res = controller(launcher, jdbc).handle("true");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody()).containsEntry("running", true);
        verify(launcher, never()).run(any(Job.class), any(JobParameters.class));
    }

    @Test
    void batchImportStart_startetJobWennKeinerLaeuft() throws Exception {
        JobLauncher launcher = mock(JobLauncher.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(eq(RUNNING_COUNT_SQL), eq(Integer.class))).thenReturn(0);

        ResponseEntity<Map<String, Object>> res = controller(launcher, jdbc).handle("true");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(res.getBody()).containsEntry("started", true);
        verify(launcher, times(1)).run(any(Job.class), any(JobParameters.class));
    }

    @Test
    void clear_lehntAbWennEinJobLaeuft() {
        JobLauncher launcher = mock(JobLauncher.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(eq(RUNNING_COUNT_SQL), eq(Integer.class))).thenReturn(1);

        ResponseEntity<Map<String, Object>> res = controller(launcher, jdbc).clear();

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody()).containsEntry("running", true);
        // Kein TRUNCATE/FK-Toggle, wenn abgelehnt.
        verify(jdbc, never()).execute(contains("TRUNCATE"));
        verify(jdbc, never()).execute(contains("FOREIGN_KEY_CHECKS"));
    }
}
