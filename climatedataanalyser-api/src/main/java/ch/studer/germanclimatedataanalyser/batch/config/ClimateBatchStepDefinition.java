package ch.studer.germanclimatedataanalyser.batch.config;

import ch.studer.germanclimatedataanalyser.batch.processor.ClimateProcessor;
import ch.studer.germanclimatedataanalyser.batch.reader.WeatherReader;
import ch.studer.germanclimatedataanalyser.batch.writer.ClimateWriter;
import ch.studer.germanclimatedataanalyser.model.database.StationWeatherPerYear;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Configuration
public class ClimateBatchStepDefinition {

    @Autowired
    private JobBuilderFactory jobBuilderFactoryImport;

    @Autowired
    private StepBuilderFactory stepBuilderFactoryImport;


    @Autowired
    private WeatherReader weatherReader;

    @Bean
    @StepScope
    public ClimateProcessor climateProcessor() {
        return new ClimateProcessor();
    }

    @Bean
    @StepScope
    public ClimateWriter climateWriter() {
        return new ClimateWriter();
    }

    @Bean("importClimateRecords")
    public Step importClimateRecords() {
        // Step-Name kebab-case wie die anderen 4 (import-{temperature,station,weather,climate}-records).
        // Wird ins BATCH_STEP_EXECUTION geschrieben und vom GUI-Skeleton gematcht.
        return stepBuilderFactoryImport.get("import-climate-records")
                .<StationWeatherPerYear, StationWeatherPerYear>chunk(5000)
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
