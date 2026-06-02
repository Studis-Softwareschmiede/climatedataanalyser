package ch.studer.germanclimatedataanalyser.model.dto.helper;

import ch.studer.germanclimatedataanalyser.service.db.StationService;

public class Bundesland {

    private final StationService stationService;

    String name;

    /** Constructor for Spring-managed bean (with full DI). */
    public Bundesland(StationService stationService) {
        this.stationService = stationService;
        this.name = "";
    }

    /** No-arg constructor for plain name-holder usage (stationService not needed). */
    public Bundesland() {
        this.stationService = null;
        this.name = "";
    }

    public boolean exists() {
        return stationService.bundeslandExists(this.name);

    }

    public String proof() {
        if (!exists()) {
            return this.name + " Bundesland doesn't exist!";
        }
        return "";
    }

    public void setName(String name) {
        this.name = name.stripLeading().stripTrailing();
    }

    public String getName() {
        return name;
    }
}
