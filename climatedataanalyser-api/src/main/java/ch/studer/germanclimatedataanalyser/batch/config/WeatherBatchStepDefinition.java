package ch.studer.germanclimatedataanalyser.batch.config;

import ch.studer.germanclimatedataanalyser.batch.processor.WeatherProcessor;
import ch.studer.germanclimatedataanalyser.batch.reader.MonthReader;
import ch.studer.germanclimatedataanalyser.batch.writer.WeatherWriter;
import ch.studer.germanclimatedataanalyser.model.database.Month;
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
public class WeatherBatchStepDefinition {

    private final MonthReader monthReader;
    private final StationWeatherService stationWeatherService;

    public WeatherBatchStepDefinition(MonthReader monthReader,
                                      StationWeatherService stationWeatherService) {
        this.monthReader = monthReader;
        this.stationWeatherService = stationWeatherService;
    }

    @Bean
    @StepScope
    public WeatherProcessor weatherProcessor() {
        return new WeatherProcessor();
    }

    @Bean
    @StepScope
    public WeatherWriter weatherWriter() {
        return new WeatherWriter(stationWeatherService);
    }

    @Bean("importWeatherRecords")
    public Step importWeatherRecords(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("import-weather-records", jobRepository)
                .<Month, Month>chunk(5000, transactionManager)
                //.reader(temperatureFromDbReader())
                .reader(monthReader.getMonthFromDbReader())
                //.listener(new StepProcessorListener(statistics()))
                .processor(weatherProcessor())
                //.listener(new StepWriterListener(statistics()))
                .writer(weatherWriter())
                .build()
                ;
    }

}
