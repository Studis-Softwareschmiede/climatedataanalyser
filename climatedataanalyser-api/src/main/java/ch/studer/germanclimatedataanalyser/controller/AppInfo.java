package ch.studer.germanclimatedataanalyser.controller;

import ch.studer.germanclimatedataanalyser.model.dto.AppInfo.AppInfoDto;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/appInfo")
@CrossOrigin
public class AppInfo {

    private final BuildProperties buildProperties;

    public AppInfo(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    @GetMapping("/")
    AppInfoDto appinfo() {
        AppInfoDto appInfoDto = new AppInfoDto();
        appInfoDto.setVersion(buildProperties.getVersion());
        // getTime() ist null, wenn keine build-info.properties vorliegt (z.B. IDE-Run ohne
        // Maven-build-info → @ConditionalOnMissingBean-Fallback in der Application-Config
        // setzt kein "time"-Property). Ohne diesen Guard → NPE → HTTP 500 auf /api/appInfo/.
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

