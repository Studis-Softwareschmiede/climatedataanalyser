package ch.studer.germanclimatedataanalyser.service.ui.dbController;

import ch.studer.germanclimatedataanalyser.dao.DbLoadInformationeDAO;
import ch.studer.germanclimatedataanalyser.model.dto.db.DbLoadResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbLoadInformationServiceImpl implements DbLoadInformationService {

    private final DbLoadInformationeDAO dbLoadInformationeDAO;
    private final DbStatusInformationService dbStatusInformationService;

    public DbLoadInformationServiceImpl(DbLoadInformationeDAO dbLoadInformationeDAO,
                                        DbStatusInformationService dbStatusInformationService) {
        this.dbLoadInformationeDAO = dbLoadInformationeDAO;
        this.dbStatusInformationService = dbStatusInformationService;
    }

    private static final Logger log = LoggerFactory.getLogger(DbLoadInformationServiceImpl.class);


    @Override
    public DbLoadResponseDto getDbLoadInformation() {
        DbLoadResponseDto dbLoadResponseDto = new DbLoadResponseDto(dbLoadInformationeDAO.getDbLoadInformation(), dbStatusInformationService.getDbStatus());
        return dbLoadResponseDto;
    }

    @Override
    public boolean isDbLoaded() {
        return (dbLoadInformationeDAO.getMonthTableCount() > 0);
    }
}
