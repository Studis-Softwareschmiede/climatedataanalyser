package ch.studer.germanclimatedataanalyser.batch.writer;

import ch.studer.germanclimatedataanalyser.model.database.Station;
import ch.studer.germanclimatedataanalyser.service.db.StationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;


public class StationDBWriter implements ItemWriter<Station> {

    private static final Logger LOG = LoggerFactory.getLogger(StationDBWriter.class);

    private final StationService stationService;

    public StationDBWriter(StationService stationService) {
        this.stationService = stationService;
    }

    @Override
    public void write(Chunk<? extends Station> stations) throws Exception {

        stationService.saveAllStation(stations.getItems());


    }
}
