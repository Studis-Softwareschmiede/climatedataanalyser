package ch.studer.germanclimatedataanalyser.dao;

import ch.studer.germanclimatedataanalyser.service.ui.dbController.DbLoadRowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DbLoadInformationeImpl implements DbLoadInformationeDAO {

    private final JdbcTemplate jdbcTemplate;

    public DbLoadInformationeImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final Logger LOG = LoggerFactory.getLogger(DbLoadInformationeImpl.class);


    @Override
    public List<DbLoadRowMapper.JobExecutionInformation> getDbLoadInformation() {
        return jdbcTemplate.query(
                """
                SELECT j.End_Time
                      ,j.Status
                      ,s.Step_Name
                      ,s.Start_Time
                      ,s.End_Time as Step_End_Time
                      ,s.Read_Count
                      ,s.Write_Count
                      ,s.Status as Step_Status
                      ,s.Exit_Message as Step_Exit_Message
                      FROM CLIMATE.BATCH_JOB_EXECUTION j ,CLIMATE.BATCH_STEP_EXECUTION s
                where j.Job_execution_id = s.job_execution_id\s
                and j.job_execution_id = (select max(JOB_EXECUTION_ID) from CLIMATE.BATCH_JOB_EXECUTION)
                order by s.step_execution_id;\
                """, new DbLoadRowMapper());
    }

    @Override
    public int getMonthTableCount() {

        Integer counter;
        counter = jdbcTemplate.queryForObject("SELECT count(*) FROM CLIMATE.MONTH_;", Integer.class);

        return counter;
    }


}
