package ch.studer.germanclimatedataanalyser.batch.config;

import ch.studer.germanclimatedataanalyser.batch.listener.JobCompletionNotificationListener;
import ch.studer.germanclimatedataanalyser.batch.tasklet.ClimateFtpDataDownloader;
import ch.studer.germanclimatedataanalyser.batch.tasklet.ClimateFtpDataUnziper;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Configuration
@Import({WeatherBatchStepDefinition.class, StationBatchStepDefinition.class})
public class AnalyzerBatchJobConfiguration {
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ClimateBatchStepDefinition climateBatchConfiguration;
    private final WeatherBatchStepDefinition weatherBatchConfiguration;
    private final StationBatchStepDefinition stationBatchConfiguration;
    private final TemperatureForMonthBatchConfiguration temperatureForMonthBatchConfiguration;
    private final ApplicationContext applicationContext;

    public AnalyzerBatchJobConfiguration(JobRepository jobRepository, PlatformTransactionManager transactionManager, ClimateBatchStepDefinition climateBatchConfiguration, WeatherBatchStepDefinition weatherBatchConfiguration, StationBatchStepDefinition stationBatchConfiguration, TemperatureForMonthBatchConfiguration temperatureForMonthBatchConfiguration, ApplicationContext applicationContext) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.climateBatchConfiguration = climateBatchConfiguration;
        this.weatherBatchConfiguration = weatherBatchConfiguration;
        this.stationBatchConfiguration = stationBatchConfiguration;
        this.temperatureForMonthBatchConfiguration = temperatureForMonthBatchConfiguration;
        this.applicationContext = applicationContext;
    }

    // # First Tasklet : Download the Files in specific Folder

    @Bean
    public ClimateFtpDataDownloader download() {

        return new ClimateFtpDataDownloader(applicationContext);
    }

    @Bean
    public Step downloadFiles() {
        return new StepBuilder("download", jobRepository)
                .tasklet(download(), transactionManager)
                .build();
    }

    // # Second Tasklet : Unzip the Files and move to the next folder
    @Bean
    public ClimateFtpDataUnziper unziper() {
        return new ClimateFtpDataUnziper();
    }

    @Bean
    public Step unzipFiles() {
        return new StepBuilder("unzipFiles", jobRepository)
                .tasklet(unziper(), transactionManager)
                .build();
    }

    // ##################################################################################
    // # Job Definition
    // ##################################################################################

    @Bean
    public Job importGermanClimateDataJob(JobCompletionNotificationListener listener) {
        return new JobBuilder("importGermanClimateDataJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(downloadFiles())
                .next(unzipFiles())
                .next(temperatureForMonthBatchConfiguration.importTemperatureRecords(jobRepository, transactionManager))
                .next(stationBatchConfiguration.importStations(jobRepository, transactionManager))
                .next(weatherBatchConfiguration.importWeatherRecords(jobRepository, transactionManager))
                .next(climateBatchConfiguration.importClimateRecords(jobRepository, transactionManager))
                .build()
                ;
    }
}



