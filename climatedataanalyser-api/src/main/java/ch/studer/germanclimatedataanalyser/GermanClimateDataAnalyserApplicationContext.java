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
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

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

    // KEIN Fallback-BuildProperties-Bean mehr: ein @ConditionalOnMissingBean in dieser
    // User-@Configuration wird VOR der Auto-Config verarbeitet → die Bedingung sah immer
    // "kein Bean" → der Fallback preemptete die echte, aus build-info.properties auto-
    // konfigurierte BuildProperties (→ Version immer "not-jarred"). Der AppInfo-Controller
    // injiziert BuildProperties jetzt optional (ObjectProvider) und kommt ohne diese Bean aus.
}
