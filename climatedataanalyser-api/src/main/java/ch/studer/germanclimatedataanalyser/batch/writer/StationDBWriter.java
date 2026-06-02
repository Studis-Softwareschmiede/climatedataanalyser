package ch.studer.germanclimatedataanalyser.batch.writer;

import ch.studer.germanclimatedataanalyser.model.database.Station;
import ch.studer.germanclimatedataanalyser.service.db.StationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemWriter;

import java.util.List;


public class StationDBWriter implements ItemWriter<Station> {

    private static final Logger LOG = LoggerFactory.getLogger(StationDBWriter.class);

    private final StationService stationService;

    public StationDBWriter(StationService stationService) {
        this.stationService = stationService;
    }

    @Override
    public void write(List<? extends Station> stations) {

        stationService.saveAllStation(stations);


    }
}
