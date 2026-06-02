package ch.studer.germanclimatedataanalyser.model.database;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class StationTemperature extends TemperatureForMonths {

    @Column(name = "STATION_ID")
    private int stationId;


    public StationTemperature() {
    }

    public StationTemperature(int stationId) {

        super();
        this.stationId = stationId;

    }

    public int getStationId() {
        return stationId;
    }

    public void setStationId(int stationId) {
        this.stationId = stationId;
    }

}
