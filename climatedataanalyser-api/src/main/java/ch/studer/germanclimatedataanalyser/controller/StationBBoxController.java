package ch.studer.germanclimatedataanalyser.controller;

import ch.studer.germanclimatedataanalyser.model.dto.BoundingBoxDto;
import ch.studer.germanclimatedataanalyser.model.dto.BoundingBoxDto.Coordinate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stations")
@CrossOrigin
public class StationBBoxController {

    private final JdbcTemplate jdbcTemplate;

    public StationBBoxController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * GET /api/stations/bbox?bundesland={name}
     * Returns the bounding box (NW + SE corners) of all stations in a Bundesland.
     * AC-B1..AC-B5, AC-B7, plus suggestion S3 (explicit empty-string guard → 400).
     */
    @GetMapping("/bbox")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getBoundingBox(@RequestParam("bundesland") String bundesland) {

        // [S3] explicit guard against null/empty parameter — clean 400 instead of implicit 404.
        // Spring already handles the missing-param case with 400 via MissingServletRequestParameterException,
        // but ?bundesland= (empty string) would otherwise fall through to the "not found" branch.
        if (bundesland == null || bundesland.trim().isEmpty()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Query parameter 'bundesland' must not be empty");
            return ResponseEntity.status(400).body(error);
        }

        // Actual column names from STATION table: GEO_LATITUDE, GEO_LENGTH, BUNDES_LAND
        // (spec hint used different names; real schema verified against live DB).
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
        Coordinate nw = new Coordinate(maxLat, minLon);
        // se = min latitude (south) + max longitude (east)
        Coordinate se = new Coordinate(minLat, maxLon);

        return ResponseEntity.ok(new BoundingBoxDto(nw, se));
    }

    private double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }
}
