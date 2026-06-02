package ch.studer.germanclimatedataanalyser.batch.listener;

import ch.studer.germanclimatedataanalyser.model.database.Month;
import org.springframework.batch.core.ItemWriteListener;
import org.springframework.batch.item.Chunk;


public class StepWriterListener implements ItemWriteListener<Month> {


    public StepWriterListener() {

    }

    @Override
    public void beforeWrite(Chunk<? extends Month> items) {

    }

    @Override
    public void afterWrite(Chunk<? extends Month> items) {


    }

    @Override
    public void onWriteError(Exception exception, Chunk<? extends Month> items) {

    }
}
