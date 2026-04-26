package org.example.forthewater.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.forthewater.*;
import org.example.forthewater.dto.WaterBodyDetails;
import org.example.forthewater.dto.copernicus.CopernicusMetrics;
import org.example.forthewater.dto.WeeklyWaterMetric;
import org.example.forthewater.model.WaterBodyEntity;
import org.example.forthewater.service.CopernicusParserService;
import org.example.forthewater.service.WaterAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.example.forthewater.client.CopernicusClient;

import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/waterbody")
@RequiredArgsConstructor
public class WaterBodyController {

    private final WaterTrackingService waterTrackingService;
    private final WaterAnalysisService waterAnalysisService;


    /**
     * Test endpoint for OpenStreetMap Polygon extraction.
     * Example usage: GET /api/waterbody/test?lat=42.593&lon=23.407
     */
    @GetMapping("/outline")
    public ResponseEntity<WaterBodyDetails> testOsmIntegration(
            @RequestParam double lat,
            @RequestParam double lon) {

        log.info("Received test request for coordinates: lat={}, lon={}", lat, lon);

        // Call the service pipeline
        WaterBodyDetails details = waterTrackingService.initializeWaterBodyTracking(lat, lon);

        // If no polygon is found, return a 404 Not Found along with the warning message
        if (details.polygon().isEmpty()) {
            log.warn("No water body found at coordinates {}, {}", lat, lon);
            return ResponseEntity.status(404).body(details);
        }

        // Return a 200 OK with the full polygon JSON
        log.info("Successfully retrieved polygon for: {}", details.name());
        return ResponseEntity.ok(details);
    }
    @GetMapping("/track")
    public ResponseEntity<CopernicusMetrics> trackFresh(@RequestParam double lat, @RequestParam double lon) {
        try {
            return ResponseEntity.ok(waterAnalysisService.fetchFreshData(lat, lon));
        } catch (Exception e) {
            log.error("Satellite fetch failed!", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * BULK HISTORY (Multiple Points)
     * Reads ONLY from DB.
     */
    @PostMapping("/bulk-history")
    public ResponseEntity<List<CopernicusMetrics>> getBulkHistory(@RequestBody List<Coordinate> locations) {
        List<CopernicusMetrics> results = waterAnalysisService.getBulkFromDb(locations);
        return ResponseEntity.ok(results);
    }

    /**
     * GET ALL SAVED
     * Returns the entire database contents. Use this for your "Global Dashboard".
     */
    @GetMapping("/all")
    public ResponseEntity<List<CopernicusMetrics>> getAll() {
        return ResponseEntity.ok(waterAnalysisService.getAllSaved());
    }
}