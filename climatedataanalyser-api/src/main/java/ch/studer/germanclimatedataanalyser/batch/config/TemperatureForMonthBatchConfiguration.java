package ch.studer.germanclimatedataanalyser.batch.config;

import ch.studer.germanclimatedataanalyser.batch.listener.SkippedRecordTracker;
import ch.studer.germanclimatedataanalyser.batch.listener.StepProcessorListener;
import ch.studer.germanclimatedataanalyser.batch.listener.StepWriterListener;
import ch.studer.germanclimatedataanalyser.batch.processor.TemperatureForMonthProcessor;
import ch.studer.germanclimatedataanalyser.batch.writer.TemperatureForMonthDBWriter;
import ch.studer.germanclimatedataanalyser.common.DirectoryUtilityImpl;
import ch.studer.germanclimatedataanalyser.model.database.Month;
import ch.studer.germanclimatedataanalyser.model.file.MonthFile;
import ch.studer.germanclimatedataanalyser.service.db.MonthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.MultiResourceItemReader;
import org.springframework.batch.infrastructure.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.batch.infrastructure.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.infrastructure.item.file.transform.IncorrectTokenCountException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;


@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Configuration
public class TemperatureForMonthBatchConfiguration {
    private final SkippedRecordTracker skippedRecordTracker;
    private final MonthService monthService;

    @Value("${climate.path.temperature.input.file.pattern}")
    private String inputFilePattern;

    @Value("${climate.path.inputFolderName}")
    private String inputDirectoryName;

    public TemperatureForMonthBatchConfiguration(SkippedRecordTracker skippedRecordTracker,
                                                 MonthService monthService) {
        this.skippedRecordTracker = skippedRecordTracker;
        this.monthService = monthService;
    }

    private static final Logger log = LoggerFactory.getLogger(TemperatureForMonthBatchConfiguration.class);

    @Bean
    @StepScope
    public MultiResourceItemReader<MonthFile> monthFilesReader() {

        Resource[] inputResources = DirectoryUtilityImpl.getResources(DirectoryUtilityImpl.getDirectory(inputDirectoryName).listFiles(), inputFilePattern);
        log.info("InputRessource :" + inputResources.toString());

        MultiResourceItemReader<MonthFile> resourceItemReader = new MultiResourceItemReader<MonthFile>();
        resourceItemReader.setResources(inputResources);
        resourceItemReader.setDelegate(reader());
        return resourceItemReader;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Bean
    @StepScope
    public FlatFileItemReader<MonthFile> reader() {
        //Create reader instance
        FlatFileItemReader<MonthFile> reader = new FlatFileItemReader<MonthFile>();

        //Set number of lines to skips. Use it if file has header rows.
        reader.setLinesToSkip(1);
        reader.setEncoding("utf-8");

        //Configure how each line will be parsed and mapped to different values
        reader.setLineMapper(new DefaultLineMapper() {
            {
                //3 columns in each row
                setLineTokenizer(new DelimitedLineTokenizer() {

                    {
                        setNames("stationsId"
                                , "messDatumBeginn"
                                , "messDatumEnde"
                                , "qn4"
                                , "moN"
                                , "moTt"
                                , "moTx"
                                , "moTn"
                                , "moFk"
                                , "mxTx"
                                , "mxFx"
                                , "mxTn"
                                , "moSdS"
                                , "qn6"
                                , "moRr"
                                , "mxRs"
                                , "eor");
                        setDelimiter(";");
                    }
                });
                //Set values in Employee class
                setFieldSetMapper(new BeanWrapperFieldSetMapper<MonthFile>() {
                    {
                        setTargetType(MonthFile.class);
                    }
                });
            }
        });
        return reader;
    }

    @Bean
    @StepScope
    public TemperatureForMonthProcessor temperaturProcessor() {
        return new TemperatureForMonthProcessor();
    }

    @Bean
    @StepScope
    public TemperatureForMonthDBWriter monthWriter() {
        return new TemperatureForMonthDBWriter(monthService);
    }


    @Bean
    public Step importTemperatureRecords(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("import-temperature-records", jobRepository)
                .<MonthFile, Month>chunk(10000, transactionManager)
                .reader(monthFilesReader())
                .listener(new StepProcessorListener())
                .processor(temperaturProcessor())
                .listener(new StepWriterListener())
                .writer(monthWriter())
                // Fault-tolerant read: max 100 korrupte Zeilen pro Run werden geskipped + im
                // SkippedRecordTracker erfasst (für Frontend-Bericht). Bei >100 → hard fail.
                // Begründung: stilles Skip wäre Lüge gegenüber dem Daten-User. Jeder Skip wird
                // mit file/line/input/message getrackt und im API-Response ausgegeben.
                .faultTolerant()
                .skip(FlatFileParseException.class)
                .skip(IncorrectTokenCountException.class)
                .skipLimit(100)
                .listener(new SkipListener<MonthFile, Month>() {
                    @Override
                    public void onSkipInRead(Throwable t) {
                        // Wir können hier nicht direkt an die jobExecutionId — der Tracker
                        // wird vom SkipReportingJobListener (Job-Level) aufgeräumt. Hier nur erfassen.
                        skippedRecordTracker.add(currentJobExecutionId(), "import-temperature-records", t);
                    }
                    @Override public void onSkipInWrite(Month item, Throwable t) {}
                    @Override public void onSkipInProcess(MonthFile item, Throwable t) {}
                })
                .build()
                ;
    }

    /**
     * Holt die aktuelle JobExecutionId aus dem Spring-Batch StepSynchronizationManager.
     * Wir laufen im StepScope-Kontext (siehe @StepScope auf Reader-Beans),
     * StepSynchronizationManager liefert den aktuellen StepContext.
     */
    private static Long currentJobExecutionId() {
        try {
            org.springframework.batch.core.scope.context.StepContext ctx =
                    org.springframework.batch.core.scope.context.StepSynchronizationManager.getContext();
            if (ctx != null && ctx.getStepExecution() != null
                    && ctx.getStepExecution().getJobExecution() != null) {
                return ctx.getStepExecution().getJobExecution().getId();
            }
        } catch (Exception ignored) {}
        return -1L;  // Fallback — Tracker behandelt -1 als "unknown job"
    }


}
