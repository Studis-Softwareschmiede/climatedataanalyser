package ch.studer.germanclimatedataanalyser.service.ui.dbController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;

public class DbStatusInformationServiceImpl implements DbStatusInformationService, InitializingBean {

    private final JdbcTemplate jdbcTemplate;

    private DbStatusEnum dbStatus;

    private static final Logger log = LoggerFactory.getLogger(DbStatusInformationServiceImpl.class);

    public DbStatusInformationServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Live-Status: queryt MONTH_-Count bei jedem Call.
     * Vorher (afterPropertiesSet-only-Cache): Status blieb beim Bean-Init-Wert
     * hängen → 'empty' auch nach 745k geladenen Rows → GUI stuck.
     */
    @Override
    public DbStatusEnum getDbStatus() {
        try {
            return getMonthTableCount() > 0 ? DbStatusEnum.loaded : DbStatusEnum.empty;
        } catch (Exception e) {
            log.warn("getDbStatus() live-query failed, falling back to cached value: {}", e.getMessage());
            return dbStatus != null ? dbStatus : DbStatusEnum.empty;
        }
    }

    @Override
    public void setDbStatus(DbStatusEnum dbStatus) {
        this.dbStatus = dbStatus;
    }


    public int getMonthTableCount() {

        Integer counter;
        //counter = jdbcTemplate.queryForObject("SELECT count(*) FROM CLIMATE.MONTH_;", Integer.class);
        counter = jdbcTemplate.queryForObject("SELECT count(*) FROM MONTH_;", Integer.class);

        return counter;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (getMonthTableCount() > 0) {
            setDbStatus(DbStatusEnum.loaded);
        } else {
            setDbStatus(DbStatusEnum.empty);
        }

    }
}

