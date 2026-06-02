package ch.studer.germanclimatedataanalyser;

import ch.studer.germanclimatedataanalyser.batch.reader.MonthReader;
import ch.studer.germanclimatedataanalyser.batch.reader.WeatherReader;
import ch.studer.germanclimatedataanalyser.dao.StationClimateDAO;
import ch.studer.germanclimatedataanalyser.model.dto.helper.Bundesland;
import ch.studer.germanclimatedataanalyser.service.db.*;
import ch.studer.germanclimatedataanalyser.service.ui.analytics.ClimateAnalyserService;
import ch.studer.germanclimatedataanalyser.service.ui.analytics.ClimateAnalyserServiceImpl;
import ch.studer.germanclimatedataanalyser.service.ui.climateRecords.ClimateRecordService;
import ch.studer.germanclimatedataanalyser.service.ui.climateRecords.ClimateRecordServiceImpl;
import ch.studer.germanclimatedataanalyser.service.ui.dbController.DbLoadInformationService;
import ch.studer.germanclimatedataanalyser.service.ui.dbController.DbLoadInformationServiceImpl;
import ch.studer.germanclimatedataanalyser.service.ui.dbController.DbStatusInformationService;
import ch.studer.germanclimatedataanalyser.service.ui.dbController.DbStatusInformationServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
public class GermanClimateDataAnalyserApplicationContext {

    @Bean
    ClimateService climateService(StationClimateDAO stationClimateDAO) {
        return new ClimateServiceImpl(stationClimateDAO);
    }

    @Bean
    @DependsOnDatabaseInitialization
    MonthReader monthReader(DataSource dataSource) {
        return new MonthReader(dataSource);
    }

    @Bean
    @DependsOnDatabaseInitialization
    WeatherReader weatherReader(DataSource dataSource) {
        return new WeatherReader(dataSource);
    }

    @Bean
    ClimateAnalyserService climateAnalyserService(ClimateService climateService, StationService stationService) {
        return new ClimateAnalyserServiceImpl(climateService, stationService);
    }

    @Bean
    DbLoadInformationService dbLoadInformationService(
            ch.studer.germanclimatedataanalyser.dao.DbLoadInformationeDAO dbLoadInformationeDAO,
            DbStatusInformationService dbStatusInformationService) {
        return new DbLoadInformationServiceImpl(dbLoadInformationeDAO, dbStatusInformationService);
    }

    @Bean
    ClimateRecordService climateRecordService(
            StationClimateDAO stationClimateDAO,
            Bundesland bundeslandProofer) {
        return new ClimateRecordServiceImpl(stationClimateDAO, bundeslandProofer);
    }

    @Bean
    Bundesland bundesland(StationService stationService) {
        return new Bundesland(stationService);
    }

    @Bean
    DbStatusInformationService dbStatus(JdbcTemplate jdbcTemplate) {
        return new DbStatusInformationServiceImpl(jdbcTemplate);
    }

    @ConditionalOnMissingBean
    @Bean
    public BuildProperties buildProperties() {
        Properties properties = new Properties();
        properties.put("group", "ch.climateDataAnalyser");
        properties.put("artifact", "Climate Data Analyser");
        properties.put("version", "not-jarred");
        return new BuildProperties(properties);
    }
}
