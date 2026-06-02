package ch.studer.germanclimatedataanalyser.dao;

import org.springframework.stereotype.Component;

@Component
public class DataAccess {

    private final MonthDAO monthDAO;
    private final StationDAO stationDAO;
    private final StationClimateDAO stationClimateDAO;
    private final StationWeatherDAO stationWeatherDAO;

    public DataAccess(MonthDAO monthDAO, StationDAO stationDAO,
                      StationClimateDAO stationClimateDAO, StationWeatherDAO stationWeatherDAO) {
        this.monthDAO = monthDAO;
        this.stationDAO = stationDAO;
        this.stationClimateDAO = stationClimateDAO;
        this.stationWeatherDAO = stationWeatherDAO;
    }


    public MonthDAO getMonthDAO() {
        return monthDAO;
    }

    public StationDAO getStationDAO() {
        return stationDAO;
    }

    public StationClimateDAO getStationClimateDAO() {
        return stationClimateDAO;
    }

    public StationWeatherDAO getStationWeatherDAO() {
        return stationWeatherDAO;
    }
}
