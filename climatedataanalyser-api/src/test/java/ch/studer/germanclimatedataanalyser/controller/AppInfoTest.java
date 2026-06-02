package ch.studer.germanclimatedataanalyser.controller;

import ch.studer.germanclimatedataanalyser.model.dto.AppInfo.AppInfoDto;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reiner Unit-Test (kein Spring-Context / keine DB) für {@link AppInfo}.
 * Deckt die NPE ab, die /api/appInfo/ mit HTTP 500 quittierte, wenn keine
 * build-info.properties vorliegt (IDE-Run ohne Maven-build-info → der
 * @ConditionalOnMissingBean-Fallback liefert BuildProperties ohne "time").
 */
class AppInfoTest {

    private AppInfo appInfoWith(Properties props) {
        return new AppInfo(new BuildProperties(props));
    }

    @Test
    void buildTimeIsUnknownWhenNoTimeProperty() {
        // Fallback-Szenario: BuildProperties ohne "time" → getTime() == null.
        // Vor dem Fix: null.atZone(...) → NullPointerException → HTTP 500.
        Properties props = new Properties();
        props.put("version", "not-jarred");

        AppInfo appInfo = appInfoWith(props);
        AppInfoDto dto = assertDoesNotThrow(appInfo::appinfo);

        assertEquals("not-jarred", dto.getVersion());
        assertEquals("unknown", dto.getBuildTime());
    }

    @Test
    void buildTimeIsFormattedWhenTimePresent() {
        // Echter Maven-Build: build-info.properties hat ein "time" (ISO-Instant).
        Properties props = new Properties();
        props.put("version", "1.2.3");
        props.put("time", "2022-05-31T12:26:40Z");

        AppInfo appInfo = appInfoWith(props);
        AppInfoDto dto = appInfo.appinfo();

        assertEquals("1.2.3", dto.getVersion());
        assertTrue(dto.getBuildTime().matches("\\d{4}-\\d{2}-\\d{2}:\\d{2}\\.\\d{2}"),
                "Build-Zeit sollte als yyyy-MM-dd:HH.mm formatiert sein, war: " + dto.getBuildTime());
    }
}
