package ch.studer.germanclimatedataanalyser.model.dto.db;

import ch.studer.germanclimatedataanalyser.service.ui.dbController.DbLoadRowMapper;
import ch.studer.germanclimatedataanalyser.service.ui.dbController.DbStatusEnum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DbLoadResponseDto {

    private String isDbLoaded;
    private String lastLoad;
    private String status;
    private List<DbLoadStep> dbLoadSteps = new ArrayList<DbLoadStep>();
    private Map<String, Integer> fileCounts = new HashMap<>();  // ftpData, unzipedFiles, inputFiles → counts

    public DbLoadResponseDto(List<DbLoadRowMapper.JobExecutionInformation> dbLoadInformation, DbStatusEnum dbStatus) {
        this.mapToDbLoadResponsDto(dbLoadInformation, dbStatus);

    }


    public void mapToDbLoadResponsDto(List<DbLoadRowMapper.JobExecutionInformation> jobExecutionInformations, DbStatusEnum dbStatus) {
        this.isDbLoaded = dbStatus.name();

        // Empty-State (frische DB, noch nie ein Job-Run): defensiv fallback (closes #28).
        // Vorher: .get(0) auf leerer Liste → IndexOutOfBoundsException → HTTP 500 → Frontend stuck.
        if (jobExecutionInformations == null || jobExecutionInformations.isEmpty()) {
            this.lastLoad = null;
            this.status = "NEVER_RUN";
            // dbLoadSteps bleibt leer — Frontend zeigt Pipeline-Skeleton mit 'pending'.
            return;
        }

        this.lastLoad = jobExecutionInformations.get(0).endTime;
        this.status = jobExecutionInformations.get(0).status;

        for (DbLoadRowMapper.JobExecutionInformation jobExecutionInformation : jobExecutionInformations) {
            DbLoadStep dbLoadStep = new DbLoadStep(
                    jobExecutionInformation.getStepName()
                    , jobExecutionInformation.getStartTime()
                    , jobExecutionInformation.getStepEndTime()
                    , jobExecutionInformation.getReadCount()
                    , jobExecutionInformation.getWriteCount()
                    , jobExecutionInformation.getStepStatus()
                    , jobExecutionInformation.getStepExitMessage()
            );
            dbLoadSteps.add(dbLoadStep);

        }
    }

    public Map<String, Integer> getFileCounts() {
        return fileCounts;
    }

    public void setFileCounts(Map<String, Integer> fileCounts) {
        this.fileCounts = fileCounts;
    }

    // TODO remove Code
//    private String getTextForIsDbLoaded(boolean isDbLoaded) {
//        String text = "";
//
//        if (isDbLoaded) {
//            text = "DB is loaded!";
//        } else {
//
//            text = "DB is not loaded!";
//        }
//
//
//        return text;
//    }


    public String getLastLoad() {
        return lastLoad;
    }

    public void setLastLoad(String lastLoad) {
        this.lastLoad = lastLoad;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<DbLoadStep> getDbLoadSteps() {
        return dbLoadSteps;
    }

    public void setDbLoadSteps(List<DbLoadStep> dbLoadSteps) {
        this.dbLoadSteps = dbLoadSteps;
    }

    public String getIsDbLoaded() {
        return isDbLoaded;
    }

    public void setIsDbLoaded(String isDbLoaded) {
        this.isDbLoaded = isDbLoaded;
    }
}
