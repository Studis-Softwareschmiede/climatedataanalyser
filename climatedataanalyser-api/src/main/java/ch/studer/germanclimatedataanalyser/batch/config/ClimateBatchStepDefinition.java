package ch.studer.germanclimatedataanalyser.batch.config;

import ch.studer.germanclimatedataanalyser.batch.processor.ClimateProcessor;
import ch.studer.germanclimatedataanalyser.batch.reader.WeatherReader;
import ch.studer.germanclimatedataanalyser.batch.writer.ClimateWriter;
import ch.studer.germanclimatedataanalyser.model.database.StationWeatherPerYear;
import ch.studer.germanclimatedataanalyser.service.db.ClimateService;
import ch.studer.germanclimatedataanalyser.service.db.StationWeatherService;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Configuration
public class ClimateBatchStepDefinition {

    private final WeatherReader weatherReader;
    private final ClimateService climateService;
    private final StationWeatherService stationWeatherService;

    public ClimateBatchStepDefinition(WeatherReader weatherReader,
                                      ClimateService climateService,
                                      StationWeatherService stationWeatherService) {
        this.weatherReader = weatherReader;
        this.climateService = climateService;
        this.stationWeatherService = stationWeatherService;
    }

    @Bean
    @StepScope
    public ClimateProcessor climateProcessor() {
        return new ClimateProcessor();
    }

    @Bean
    @StepScope
    public ClimateWriter climateWriter() {
        return new ClimateWriter(climateService, stationWeatherService);
    }

    @Bean("importClimateRecords")
    public Step importClimateRecords(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        // Step-Name kebab-case wie die anderen 4 (import-{temperature,station,weather,climate}-records).
        // Wird ins BATCH_STEP_EXECUTION geschrieben und vom GUI-Skeleton gematcht.
        return new StepBuilder("import-climate-records", jobRepository)
                .<StationWeatherPerYear, StationWeatherPerYear>chunk(5000, transactionManager)
                //.reader(temperatureFromDbReader())
                .reader(weatherReader.getWeatherFromDbReader())
                //.listener(new StepProcessorListener(statistics()))
                .processor(climateProcessor())
                //.listener(new StepWriterListener(statistics()))
                .writer(climateWriter())
                .build()
                ;
    }

}
