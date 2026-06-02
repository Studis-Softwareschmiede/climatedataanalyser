package ch.studer.germanclimatedataanalyser.batch.listener;

import ch.studer.germanclimatedataanalyser.service.ui.dbController.DbStatusEnum;
import ch.studer.germanclimatedataanalyser.service.ui.dbController.DbStatusInformationService;
import org.hibernate.Session;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;

@Component
public class JobCompletionNotificationListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobCompletionNotificationListener.class);

    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;
    private final DbStatusInformationService dbStatus;

    public JobCompletionNotificationListener(JdbcTemplate jdbcTemplate,
                                             EntityManager entityManager,
                                             DbStatusInformationService dbStatus) {
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
        this.dbStatus = dbStatus;
    }

    private Session getSession() {
        return entityManager.unwrap(Session.class);
    }

    @Override
    public void beforeJob(@NotNull JobExecution jobExecution) {

        log.info("****************************************************************");
        log.info("!!!                      JOB START                           !!!");
        log.info("****************************************************************");

        dbStatus.setDbStatus(DbStatusEnum.loading);
        // Prepend the Tables
        jdbcTemplate.execute("Delete FROM STATION");
        jdbcTemplate.execute("Delete FROM MONTH_");
        jdbcTemplate.execute("Delete FROM WEATHER");
        jdbcTemplate.execute("Delete FROM CLIMATE");


    }

    @Override
    public void afterJob(JobExecution jobExecution) {

        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            dbStatus.setDbStatus(DbStatusEnum.loaded);
            log.info("##########################################################");
            log.info("!!!          JOB FINISHED! SUCCESSFULLY                !!!");
            log.info("##########################################################");
        } else {
            dbStatus.setDbStatus(DbStatusEnum.failed);
            log.info("??????????????????????????????????????????????????????????");
            log.info("!!!          JOB FAILED                                !!!");
            log.info("??????????????????????????????????????????????????????????");
            log.info("Batch Exit Status :" + jobExecution.getStatus());

        }

        log.info("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        log.info("!!!                     STATISTIC                            !!!");
        log.info("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");


        log.info("****************************************************************");
        log.info("!!!          JOB ENDE                                        !!!");
        log.info("****************************************************************");

    }


}
