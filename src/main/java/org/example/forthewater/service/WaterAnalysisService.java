package org.example.forthewater.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.forthewater.Coordinate;
import org.example.forthewater.client.CopernicusClient;
import org.example.forthewater.client.OverpassClient;
import org.example.forthewater.dto.WaterBodyDetails;
import org.example.forthewater.dto.WeeklyWaterMetric;
import org.example.forthewater.dto.copernicus.CopernicusMetrics;
import org.example.forthewater.dto.sensor.BeaconLatestDTO;
import org.example.forthewater.dto.sensor.BeaconLocationDTO;
import org.example.forthewater.mapper.WaterBodyMapper;
import org.example.forthewater.mapper.WaterMetricMapper;
import org.example.forthewater.model.BeaconReadingEntity;
import org.example.forthewater.model.WaterBodyEntity;
import org.example.forthewater.model.WaterMetricEntity;
import org.example.forthewater.repository.BeaconReadingRepository;
import org.example.forthewater.repository.BeaconRepository;
import org.example.forthewater.repository.WaterBodyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaterAnalysisService {

    private final OverpassClient overpassClient;
    private final CopernicusClient copernicusClient;
    private final CopernicusParserService parserService;
    private final WaterBodyRepository waterBodyRepository;
    private final WaterBodyMapper waterBodyMapper;
    private final WaterMetricMapper waterMetricMapper;
    private final BeaconReadingRepository readingRepository;
    private final BeaconRepository beaconRepository;

    /**
     * FRESH TRACK: No DB check. Fetches from APIs, saves to DB, returns result.
     */
    public CopernicusMetrics fetchFreshData(double lat, double lon) throws Exception {
        log.info("🚀 Force Fetching fresh satellite data for {}, {}", lat, lon);

        WaterBodyDetails details = overpassClient.getLakePolygon(lat, lon);
        if (details.polygon().isEmpty()) {
            throw new RuntimeException("No water body found.");
        }

        String rawJson = copernicusClient.fetchHistoricalData(details.polygon(), details.name());
        List<WeeklyWaterMetric> metricsDto = parserService.parseToMeansOnly(rawJson);

        // Update or Create in DB
        WaterBodyEntity entity = waterBodyRepository.findByCenterLatAndCenterLon(lat, lon)
                .orElseGet(() -> waterBodyMapper.toEntity(details, lat, lon));

        // Map and link metrics
        List<WaterMetricEntity> metricEntities = metricsDto.stream()
                .map(dto -> mapToEntity(dto, entity))
                .collect(Collectors.toList());

        entity.setMetrics(metricEntities);
        waterBodyRepository.save(entity);

        return new CopernicusMetrics(details, metricsDto);
    }

    /**
     * BULK FETCH: Reads only from DB for a list of coordinates.
     */
    public List<CopernicusMetrics> getBulkFromDb(List<Coordinate> requests) {
        log.info("📦 Bulk DB lookup for {} locations", requests.size());

        return requests.stream()
                .map(req -> waterBodyRepository.findByCenterLatAndCenterLon(req.lat(), req.lon()))
                .filter(java.util.Optional::isPresent)
                .map(opt -> {
                    WaterBodyEntity e = opt.get();
                    return new CopernicusMetrics(
                            waterBodyMapper.toDto(e),
                            e.getMetrics().stream().map(waterMetricMapper::toDto).toList()
                    );
                })
                .toList();
    }

    /**
     * FETCH ALL: Returns every single water body and its metrics stored in the DB.
     */
    public List<CopernicusMetrics> getAllSaved() {
        log.info("📊 Fetching all saved water bodies from the database...");

        return waterBodyRepository.findAll().stream()
                .map(entity -> new CopernicusMetrics(
                        waterBodyMapper.toDto(entity),
                        entity.getMetrics().stream().map(waterMetricMapper::toDto).toList()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BeaconLocationDTO> getAllBeaconsWithLatestCoords() {
        return beaconRepository.findAll().stream().map(beacon -> {
            // Find the most recent reading for this beacon
            return readingRepository.findFirstByBeaconOrderByTimestampDesc(beacon)
                    .map(lastReading -> new BeaconLocationDTO(
                            beacon.getUuid(),
                            lastReading.getLatitude(),
                            lastReading.getLongitude(),
                            lastReading.getTimestamp()
                    ))
                    // If a beacon exists but has no readings yet, return it with null coords
                    .orElse(new BeaconLocationDTO(beacon.getUuid(),  null, null, null));
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<BeaconLatestDTO> getAllBeaconsWithLatest() {
        return beaconRepository.findAll().stream().map(beacon -> {
            // Fetch the single latest pulse for this specific beacon
            BeaconReadingEntity latest = readingRepository
                    .findFirstByBeaconOrderByTimestampDesc(beacon)
                    .orElse(null);

            return new BeaconLatestDTO(
                    beacon.getUuid(),
                    latest
            );
        }).toList();
    }

    private WaterMetricEntity mapToEntity(WeeklyWaterMetric dto, WaterBodyEntity parent) {
        return WaterMetricEntity.builder()
                .waterBody(parent)
                .fromDate(OffsetDateTime.parse(dto.getFrom()))
                .toDate(OffsetDateTime.parse(dto.getTo()))
                .ndwi(dto.getNdwi())
                .ndci(dto.getNdci())
                .turbidity(dto.getTurbidity())
                .build();
    }
}