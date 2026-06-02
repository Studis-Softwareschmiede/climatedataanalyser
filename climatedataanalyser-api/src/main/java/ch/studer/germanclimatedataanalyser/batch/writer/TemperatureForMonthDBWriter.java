package ch.studer.germanclimatedataanalyser.batch.writer;

import ch.studer.germanclimatedataanalyser.model.database.Month;
import ch.studer.germanclimatedataanalyser.service.db.MonthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;


public class TemperatureForMonthDBWriter implements ItemWriter<Month> {

    private static final Logger log = LoggerFactory.getLogger(TemperatureForMonthDBWriter.class);

    private final MonthService monthService;

    public TemperatureForMonthDBWriter(MonthService monthService) {
        this.monthService = monthService;
    }

    @Override
    public void write(Chunk<? extends Month> months) throws Exception {

        monthService.saveAllMonth(months.getItems());


    }
}
