package ch.studer.germanclimatedataanalyser.batch.config;

import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/**
 * Asynchroner JobLauncher.
 *
 * <p>SB3 / Spring Batch 5 konfiguriert per Default einen <b>synchronen</b>
 * {@link TaskExecutorJobLauncher} (SyncTaskExecutor): {@code jobLauncher.run(...)}
 * läuft dann auf dem HTTP-Request-Thread und blockiert den REST-Trigger
 * {@code /api/database/batchImportStart} für die gesamte Import-Dauer (Minuten) →
 * Frontend-Spinner hängt, das Live-Polling auf {@code BATCH_STEP_EXECUTION} läuft ins Leere.
 *
 * <p>Mit einem {@link SimpleAsyncTaskExecutor} kehrt {@code run(...)} sofort zurück und der
 * Job läuft auf einem Hintergrund-Thread — der Trigger antwortet unmittelbar, das Frontend
 * pollt den Fortschritt. (SB2-Verhalten, das beim SB2→3-Upgrade verloren ging.)
 *
 * <p>Boots {@code SpringBootBatchConfiguration#jobLauncher} ist ein gewöhnlicher (sync) Bean
 * mit festem Namen {@code jobLauncher} (nicht ConditionalOnMissingBean). Daher hier ein
 * EIGENER Bean-Name + {@code @Primary} — die Injection per Typ ({@code JobLauncher}) im
 * Controller wählt diesen; Boots sync-Launcher bleibt ungenutzt bestehen.
 */
@Configuration
public class BatchLauncherConfig {

    @Bean
    @Primary
    public JobLauncher asyncJobLauncher(JobRepository jobRepository) throws Exception {
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(new SimpleAsyncTaskExecutor("batch-job-"));
        launcher.afterPropertiesSet();
        return launcher;
    }
}
