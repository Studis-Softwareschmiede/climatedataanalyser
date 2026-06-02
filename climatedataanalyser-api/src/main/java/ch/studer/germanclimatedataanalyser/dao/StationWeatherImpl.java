package ch.studer.germanclimatedataanalyser.dao;

import ch.studer.germanclimatedataanalyser.model.database.StationWeatherPerYear;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import java.util.List;

@Repository
public class StationWeatherImpl implements StationWeatherDAO {

    private final EntityManager entityManager;

    public StationWeatherImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    private Session getSession() {
        return entityManager.unwrap(Session.class);
    }

    private static final Logger LOG = LoggerFactory.getLogger(StationWeatherImpl.class);


    @Override
    public void save(StationWeatherPerYear stationWeatherPerYear) {

        // Get the current hibernate Session
        Session currentSession = getSession();
        getSession().persist(stationWeatherPerYear);

    }

    @Override
    public void saveAll(List<StationWeatherPerYear> stationWeatherPerYears) {


        for (StationWeatherPerYear stationWeatherPerYear : stationWeatherPerYears) {
            save(stationWeatherPerYear);
        }
    }

    @Override
    public long count() {
        Query<Long> theQuery = getSession().createQuery("SELECT count(*)  FROM StationWeatherPerYear w ", Long.class);

        // execute and get result list
        return theQuery.getSingleResult();
    }
}
