package ch.studer.germanclimatedataanalyser.batch.config;

import ch.studer.germanclimatedataanalyser.common.DirectoryUtilityImpl;
import ch.studer.germanclimatedataanalyser.model.file.StationFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression für Issue #29: readerStation() darf bei fehlender Input-Datei
 * (z.B. Import-Lauf mit withFTP=false → kein Download) NICHT crashen, sondern
 * 0 Records liefern.
 */
class StationReaderMissingFileTest {

    private Object originalPathName;

    @AfterEach
    void restorePathName() {
        // statischen Zustand wiederherstellen (sonst Leakage in andere Tests)
        ReflectionTestUtils.setField(DirectoryUtilityImpl.class, "PATH_NAME", originalPathName);
    }

    @Test
    void readerStation_missingInputFile_doesNotThrow_andReadsZeroRecords() throws Exception {
        // leeres Download-Verzeichnis ohne Stationsdatei (= withFTP=false-Situation)
        Path tmpRoot = Files.createTempDirectory("station-missing-test");
        originalPathName = ReflectionTestUtils.getField(DirectoryUtilityImpl.class, "PATH_NAME");
        ReflectionTestUtils.setField(DirectoryUtilityImpl.class, "PATH_NAME", tmpRoot.toString() + "/");

        StationBatchStepDefinition cfg = new StationBatchStepDefinition(null, null);
        ReflectionTestUtils.setField(cfg, "ftpDirectoryName", "ftp");
        ReflectionTestUtils.setField(cfg, "stationFileName", "Stationsliste");

        // 1) Bean-Erzeugung darf nicht werfen (der eigentliche #29-Crash)
        @SuppressWarnings("unchecked")
        FlatFileItemReader<StationFile>[] holder = new FlatFileItemReader[1];
        assertThatCode(() -> holder[0] = cfg.readerStation()).doesNotThrowAnyException();
        FlatFileItemReader<StationFile> reader = holder[0];
        assertThat(reader).isNotNull();

        // 2) open() + read() → 0 Records dank setStrict(false), kein ItemStreamException
        ExecutionContext ec = new ExecutionContext();
        assertThatCode(() -> reader.open(ec)).doesNotThrowAnyException();
        assertThat(reader.read()).as("missing file → empty read").isNull();
        reader.close();
    }
}
