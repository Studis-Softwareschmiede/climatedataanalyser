package ch.studer.germanclimatedataanalyser.batch.config;

import ch.studer.germanclimatedataanalyser.batch.listener.JobCompletionNotificationListener;
import ch.studer.germanclimatedataanalyser.batch.tasklet.ClimateFtpDataDownloader;
import ch.studer.germanclimatedataanalyser.batch.tasklet.ClimateFtpDataUnziper;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Configuration
@EnableBatchProcessing
@Import({WeatherBatchStepDefinition.class, StationBatchStepDefinition.class})
public class AnalyzerBatchJobConfiguration {

    private final JobBuilderFactory jobBuilderFactoryImport;
    private final StepBuilderFactory stepBuilderFactoryImport;
    private final ClimateBatchStepDefinition climateBatchConfiguration;
    private final WeatherBatchStepDefinition weatherBatchConfiguration;
    private final StationBatchStepDefinition stationBatchConfiguration;
    private final TemperatureForMonthBatchConfiguration temperatureForMonthBatchConfiguration;
    private final ApplicationContext applicationContext;

    public AnalyzerBatchJobConfiguration(JobBuilderFactory jobBuilderFactoryImport,
                                         StepBuilderFactory stepBuilderFactoryImport,
                                         ClimateBatchStepDefinition climateBatchConfiguration,
                                         WeatherBatchStepDefinition weatherBatchConfiguration,
                                         StationBatchStepDefinition stationBatchConfiguration,
                                         TemperatureForMonthBatchConfiguration temperatureForMonthBatchConfiguration,
                                         ApplicationContext applicationContext) {
        this.jobBuilderFactoryImport = jobBuilderFactoryImport;
        this.stepBuilderFactoryImport = stepBuilderFactoryImport;
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
        return stepBuilderFactoryImport.get("download")
                .tasklet(download())
                .build();
    }

    // # Second Tasklet : Unzip the Files and move to the next folder
    @Bean
    public ClimateFtpDataUnziper unziper() {
        return new ClimateFtpDataUnziper();
    }

    @Bean
    public Step unzipFiles() {
        return stepBuilderFactoryImport.get("unzipFiles")
                .tasklet(unziper())
                .build();
    }

    // ##################################################################################
    // # Job Definition
    // ##################################################################################

    @Bean
    public Job importGermanClimateDataJob(JobCompletionNotificationListener listener) {
        return jobBuilderFactoryImport.get("importGermanClimateDataJob")
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(downloadFiles())
                .next(unzipFiles())
                .next(temperatureForMonthBatchConfiguration.importTemperatureRecords())
                .next(stationBatchConfiguration.importStations())
                .next(weatherBatchConfiguration.importWeatherRecords())
                .next(climateBatchConfiguration.importClimateRecords())
                .build()
                ;
    }
}



