package ch.studer.germanclimatedataanalyser.controller;

import ch.studer.germanclimatedataanalyser.model.dto.AppInfo.AppInfoDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reiner Unit-Test (kein Spring-Context / keine DB) für {@link AppInfo}.
 * Der Controller injiziert BuildProperties optional via {@link ObjectProvider}:
 *  - vorhanden (echter Maven-/Docker-Build mit build-info.properties) → echte Version/Zeit,
 *  - nicht vorhanden (IDE-Run ohne build-info → kein Bean) → "unknown", kein Crash.
 */
class AppInfoTest {

    @SuppressWarnings("unchecked")
    private AppInfo appInfoWith(BuildProperties bp) {
        ObjectProvider<BuildProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bp);   // bp == null → "keine Bean"-Fall
        return new AppInfo(provider);
    }

    private AppInfo appInfoWith(Properties props) {
        return appInfoWith(new BuildProperties(props));
    }

    @Test
    void returnsUnknownWhenNoBuildPropertiesBean() {
        // Kein build-info.properties → keine BuildProperties-Bean → getIfAvailable() == null.
        // Darf nicht crashen, sondern "unknown" liefern.
        AppInfo appInfo = appInfoWith((BuildProperties) null);
        AppInfoDto dto = assertDoesNotThrow(appInfo::appinfo);

        assertEquals("unknown", dto.getVersion());
        assertEquals("unknown", dto.getBuildTime());
    }

    @Test
    void buildTimeIsUnknownWhenNoTimeProperty() {
        // BuildProperties vorhanden, aber ohne "time" → getTime() == null.
        // Vor dem Fix: null.atZone(...) → NullPointerException → HTTP 500.
        Properties props = new Properties();
        props.put("version", "1.2.3");

        AppInfo appInfo = appInfoWith(props);
        AppInfoDto dto = assertDoesNotThrow(appInfo::appinfo);

        assertEquals("1.2.3", dto.getVersion());
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
