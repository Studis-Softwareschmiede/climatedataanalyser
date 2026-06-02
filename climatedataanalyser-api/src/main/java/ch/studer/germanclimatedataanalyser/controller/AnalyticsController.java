package ch.studer.germanclimatedataanalyser.controller;

import ch.studer.germanclimatedataanalyser.model.dto.BundeslaenderDto;
import ch.studer.germanclimatedataanalyser.model.dto.ClimateAnalyserRequestDto;
import ch.studer.germanclimatedataanalyser.model.dto.ClimateAnalyserResponseDto;
import ch.studer.germanclimatedataanalyser.service.db.StationService;
import ch.studer.germanclimatedataanalyser.service.ui.analytics.ClimateAnalyserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
/*
TODO : RemoveRestController :
This RestController will be replaced by the new controllers
 /api/climates/ = returns the climate Records for Bundesland or gps
 /api/comparedStations/ = returns the compared climate Records for bundesland or gps
 */

@RestController
@RequestMapping("/api/analytics")
@CrossOrigin
public class AnalyticsController {

    private final StationService stationService;
    private final ClimateAnalyserService climateAnalyserService;

    public AnalyticsController(StationService stationService, ClimateAnalyserService climateAnalyserService) {
        this.stationService = stationService;
        this.climateAnalyserService = climateAnalyserService;
    }

    @GetMapping("/")
    public List<String> handle() throws Exception {
        BundeslaenderDto bundeslaenderDto = new BundeslaenderDto();
        return bundeslaenderDto.mapToDto(stationService.getAllBundeslaender());
    }

    @PostMapping("/request/")
    ClimateAnalyserResponseDto climateByClimateAnalyserRequest(@RequestBody ClimateAnalyserRequestDto climateAnalyserRequestDto) {
        return this.climateAnalyserService.getClimateAnalyticsByClimateAnalyserRequest(climateAnalyserRequestDto);
    }
}



