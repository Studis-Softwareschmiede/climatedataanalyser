package ch.studer.germanclimatedataanalyser.controller;

import ch.studer.germanclimatedataanalyser.model.dto.AppInfo.AppInfoDto;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/appInfo")
public class AppInfo {

    // Optional injizieren: bei einem regulären Maven-/Docker-Build existiert build-info.properties
    // → Spring Boot auto-konfiguriert eine echte BuildProperties-Bean (Version + Build-Zeit).
    // Bei einem IDE-Run ohne build-info gibt es keine Bean → getIfAvailable() == null, kein
    // Startup-Crash. (Vorher gab es einen @ConditionalOnMissingBean-Fallback in der App-Config,
    // der die echte Bean immer preemptete → Version blieb "not-jarred". Entfernt.)
    private final BuildProperties buildProperties;

    public AppInfo(ObjectProvider<BuildProperties> buildPropertiesProvider) {
        this.buildProperties = buildPropertiesProvider.getIfAvailable();
    }

    @GetMapping("/")
    AppInfoDto appinfo() {
        AppInfoDto appInfoDto = new AppInfoDto();
        if (buildProperties == null) {
            appInfoDto.setVersion("unknown");
            appInfoDto.setBuildTime("unknown");
            return appInfoDto;
        }
        appInfoDto.setVersion(buildProperties.getVersion());
        // getTime() kann null sein, wenn build-info ohne "time"-Property erzeugt wurde.
        Instant buildTime = buildProperties.getTime();
        appInfoDto.setBuildTime(buildTime != null
                ? getDateFormatted(buildTime.atZone(ZoneId.of("Europe/Paris")))
                : "unknown");

        return appInfoDto;
    }

    private String getDateFormatted(ZonedDateTime buildtime) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH.mm");
        return buildtime.format(format);
    }

}

