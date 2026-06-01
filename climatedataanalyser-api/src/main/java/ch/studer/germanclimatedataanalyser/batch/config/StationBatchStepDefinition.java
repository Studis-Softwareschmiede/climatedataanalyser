package ch.studer.germanclimatedataanalyser.batch.config;

import ch.studer.germanclimatedataanalyser.batch.listener.SkippedRecordTracker;
import ch.studer.germanclimatedataanalyser.batch.processor.StationProcessor;
import ch.studer.germanclimatedataanalyser.batch.writer.StationDBWriter;
import ch.studer.germanclimatedataanalyser.common.DirectoryUtilityImpl;
import ch.studer.germanclimatedataanalyser.model.database.Station;
import ch.studer.germanclimatedataanalyser.model.file.StationFile;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.FixedLengthTokenizer;
import org.springframework.batch.item.file.transform.IncorrectTokenCountException;
import org.springframework.batch.item.file.transform.Range;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileNotFoundException;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Configuration
public class StationBatchStepDefinition {

    @Autowired
    private StepBuilderFactory stepBuilderFactoryImport;

    @Autowired
    private SkippedRecordTracker skippedRecordTracker;

    @Value("${climate.path.ftpDataFolderName}")
    private String ftpDirectoryName;

    @Value("${climate.path.station.input.file.pattern}")
    private String stationFileName;

    @Bean
    @StepScope
    public StationProcessor stationProcessor() {
        return new StationProcessor();
    }

    @Bean
    @StepScope
    public StationDBWriter stationWriter() {
        return new StationDBWriter();
    }

    public FixedLengthTokenizer stationTokenizer() {
        FixedLengthTokenizer tokenizer = new FixedLengthTokenizer();

        tokenizer.setNames("stationsId", "dateBegin", "dateEnd", "stationHigh", "geoLatitude", "geoLength", "stationName", "bundesLand");
        tokenizer.setColumns(new Range(1, 5),
                new Range(6, 14),
                new Range(15, 23),
                new Range(24, 38),
                new Range(39, 50),
                new Range(51, 60),
                new Range(61, 102),
                new Range(103, 200)
        );
        // DWD-Datei hat ~1001 Zeichen pro Zeile (massives Trailing-Whitespace nach Bundesland).
        // Unsere Ranges definieren nur bis Position 200 — Spring-Batch wäre per default strict
        // und würde IncorrectTokenCountException werfen. setStrict(false) toleriert
        // Trailing-Chars und behandelt fehlende nachfolgende Spalten als leer.
        tokenizer.setStrict(false);
        return tokenizer;
    }

    @Bean
    @StepScope
    public FlatFileItemReader<StationFile> readerStation() {
        try {
            // Get the File as Resource object
            Resource inputResource = DirectoryUtilityImpl.getResource(DirectoryUtilityImpl.getDirectory(ftpDirectoryName), stationFileName);

            //Create reader instance
            FlatFileItemReader<StationFile> reader = new FlatFileItemReader<StationFile>();

            // There should be only One File ! So take the first one !
            reader.setResource(inputResource);

            //Set the right encoding for ANSI
            reader.setEncoding("Cp1252");

            //Set number of lines to skips. Use it if file has header rows.
            reader.setLinesToSkip(2);

            //Configure how each line will be parsed and mapped to different values
            reader.setLineMapper(new DefaultLineMapper() {
                {
                    //
                    setLineTokenizer(stationTokenizer());
                    //Set values in Employee class
                    setFieldSetMapper(new BeanWrapperFieldSetMapper<StationFile>() {
                        {
                            setTargetType(StationFile.class);
                        }
                    });
                }
            });
            return reader;

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    @Transactional
    @Bean
    public Step importStations() {
        return stepBuilderFactoryImport.get("import-station-records")
                .<StationFile, Station>chunk(100)
                .reader(readerStation())
                .processor(stationProcessor())
                .writer(stationWriter())
                // Fault-Tolerance analog zu import-temperature-records:
                // einzelne korrupte Stations-Zeilen skippen statt den ganzen Step zu killen,
                // ABER hard-fail bei >100 (echtes Format-Problem, kein einzelner Bad-Record).
                .faultTolerant()
                .skip(FlatFileParseException.class)
                .skip(IncorrectTokenCountException.class)
                .skipLimit(100)
                .listener(new SkipListener<StationFile, Station>() {
                    @Override public void onSkipInRead(Throwable t) {
                        Long jobId = currentJobExecutionId();
                        if (jobId != null) {
                            skippedRecordTracker.add(jobId, "import-station-records", t);
                        }
                    }
                    @Override public void onSkipInWrite(Station item, Throwable t) { /* no-op */ }
                    @Override public void onSkipInProcess(StationFile item, Throwable t) { /* no-op */ }
                })
                .build()
                ;
    }

    private Long currentJobExecutionId() {
        StepExecution se = StepSynchronizationManager.getContext() != null
                ? StepSynchronizationManager.getContext().getStepExecution() : null;
        return se != null ? se.getJobExecutionId() : null;
    }
}
