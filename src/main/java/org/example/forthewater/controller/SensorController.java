package org.example.forthewater.controller;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.forthewater.dto.sensor.BeaconLatestDTO;
import org.example.forthewater.dto.sensor.BeaconLocationDTO;
import org.example.forthewater.dto.sensor.BeaconStatusDTO;
import org.example.forthewater.dto.sensor.SensorTelemetry;
import org.example.forthewater.model.BeaconEntity;
import org.example.forthewater.model.BeaconReadingEntity;
import org.example.forthewater.repository.BeaconReadingRepository;
import org.example.forthewater.repository.BeaconRepository;
import org.example.forthewater.service.WaterAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/data/beacons")
@RequiredArgsConstructor
public class SensorController {

    private final BeaconReadingRepository repository;
    private final BeaconRepository beaconRepository;
    private final WaterAnalysisService waterAnalysisService;


    /**
     * RAW DUMP (For Debugging)
     * URL: GET /data/beacons/all
     */
    @GetMapping("/all")
    public ResponseEntity<List<BeaconReadingEntity>> getAllReadings() {
        log.info("📊 Fetching all sensor readings from the database.");
        return ResponseEntity.ok(repository.findAll());
    }

    @PostMapping("/{uuid}")
    public ResponseEntity<String> receiveSensorData(
            @PathVariable String uuid,
            @RequestBody SensorTelemetry data) {

        // 1. Find or create the Beacon
        BeaconEntity beacon = beaconRepository.findByUuid(uuid)
                .orElseGet(() -> beaconRepository.save(
                        BeaconEntity.builder().uuid(uuid).build()
                ));


        // 2. Build the Reading linked to that Beacon
        BeaconReadingEntity reading = BeaconReadingEntity.builder()
                .beacon(beacon)
                .timestamp(OffsetDateTime.now())
                .temperature(data.temperature())
                .latitude(data.lat())
                .longitude(data.lon())
                .ph(data.ph())
                .activity(data.bylhi().activity())
                .speedPxS(data.bylhi().speed_px_s())
                .immobility(data.bylhi().immobility())
                .dispersionPx(data.bylhi().dispersion_px())
                .anomalyScore(data.bylhi().anomaly_score())
                .anomalyFlag(data.bylhi().anomaly_flag())
                .build();

        repository.save(reading);
        return ResponseEntity.ok("Telemetry linked to beacon " + uuid);
    }

    /**
     * GET ALL BEACONS + LATEST READING
     * URL: GET http://10.121.68.128:8080/data/beacons/latest
     */
    @GetMapping("/latest")
    public ResponseEntity<List<BeaconLatestDTO>> getLatestForEach() {
        log.info("📡 Fetching the latest pulse for the entire beacon fleet");
        return ResponseEntity.ok(waterAnalysisService.getAllBeaconsWithLatest());
    }

    @GetMapping("/{uuid}/telemetry")
    public ResponseEntity<List<BeaconReadingEntity>> getFullTelemetry(@PathVariable String uuid) {
        return beaconRepository.findByUuid(uuid)
                .map(beacon -> ResponseEntity.ok(beacon.getReadings()))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 2. GET ALL BEACONS WITH COORDINATES
     * Perfect for the "Live Map" view.
     */
    @GetMapping("/map-locations")
    public ResponseEntity<List<BeaconLocationDTO>> getMapLocations() {
        log.info("📍 Fetching latest coordinates for all beacons");
        return ResponseEntity.ok(waterAnalysisService.getAllBeaconsWithLatestCoords());
    }


    public List<BeaconStatusDTO> getLatestStatusForAllBeacons() {
        return beaconRepository.findAll().stream().map(beacon -> {
            // Find the single most recent reading for this beacon
            BeaconReadingEntity latest = repository
                    .findFirstByBeaconOrderByTimestampDesc(beacon)
                    .orElse(null);

            return new BeaconStatusDTO(
                    beacon.getUuid(),
                    latest
            );
        }).toList();
    }
}