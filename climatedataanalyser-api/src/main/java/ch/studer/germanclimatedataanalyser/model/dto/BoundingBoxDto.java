package ch.studer.germanclimatedataanalyser.model.dto;

import ch.studer.germanclimatedataanalyser.model.dto.helper.GpsPoint;

public class BoundingBoxDto {

    private GpsPoint nw;
    private GpsPoint se;

    public BoundingBoxDto() {
    }

    public BoundingBoxDto(GpsPoint nw, GpsPoint se) {
        this.nw = nw;
        this.se = se;
    }

    public GpsPoint getNw() {
        return nw;
    }

    public void setNw(GpsPoint nw) {
        this.nw = nw;
    }

    public GpsPoint getSe() {
        return se;
    }

    public void setSe(GpsPoint se) {
        this.se = se;
    }
}
