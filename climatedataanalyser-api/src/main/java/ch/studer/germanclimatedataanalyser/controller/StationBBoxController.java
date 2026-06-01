package ch.studer.germanclimatedataanalyser.controller;

import ch.studer.germanclimatedataanalyser.model.dto.BoundingBoxDto;
import ch.studer.germanclimatedataanalyser.model.dto.helper.GpsPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stations")
@CrossOrigin
public class StationBBoxController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * GET /api/stations/bbox?bundesland={name}
     * Returns the bounding box (NW + SE corners) of all stations in a Bundesland.
     * AC-B1..AC-B5, AC-B7
     */
    @GetMapping("/bbox")
    public ResponseEntity<?> getBoundingBox(@RequestParam("bundesland") String bundesland) {

        // Actual column names from STATION table: GEO_LATITUDE, GEO_LENGTH, BUNDES_LAND
        // (spec hint used different names; real schema verified against live DB)
        String sql = "SELECT MIN(GEO_LATITUDE) AS minLat, MAX(GEO_LATITUDE) AS maxLat, " +
                     "MIN(GEO_LENGTH) AS minLon, MAX(GEO_LENGTH) AS maxLon " +
                     "FROM STATION WHERE BUNDES_LAND = ?";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, bundesland);

        if (rows.isEmpty() || rows.get(0).get("minLat") == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Bundesland '" + bundesland + "' not found");
            return ResponseEntity.status(404).body(error);
        }

        Map<String, Object> row = rows.get(0);
        double minLat = toDouble(row.get("minLat"));
        double maxLat = toDouble(row.get("maxLat"));
        double minLon = toDouble(row.get("minLon"));
        double maxLon = toDouble(row.get("maxLon"));

        // nw = max latitude (north) + min longitude (west)
        GpsPoint nw = new GpsPoint(maxLat, minLon);
        // se = min latitude (south) + max longitude (east)
        GpsPoint se = new GpsPoint(minLat, maxLon);

        return ResponseEntity.ok(new BoundingBoxDto(nw, se));
    }

    private double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }
}
