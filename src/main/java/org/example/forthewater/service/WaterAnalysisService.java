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
import java.util.Optional;
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
     * TRACK: Returns cached data when present, otherwise fetches from APIs and persists.
     */
    public CopernicusMetrics fetchFreshData(double lat, double lon) throws Exception {
        log.info("🔎 Track request for {}, {}", lat, lon);

        Optional<WaterBodyEntity> cachedByCoordinates = waterBodyRepository.findByCenterLatAndCenterLon(lat, lon);
        if (cachedByCoordinates.isPresent()
                && hasMetrics(cachedByCoordinates.get())
                && hasReliableOutline(cachedByCoordinates.get())) {
            log.info("✅ Cache hit for {}, {}. Returning stored water body.", lat, lon);
            return toCopernicusMetrics(cachedByCoordinates.get());
        }

        log.info("🚀 Cache miss for {}, {}. Fetching fresh satellite data.", lat, lon);

        WaterBodyDetails details = fetchWaterBodyDetails(lat, lon);

        try {
            String rawJson = copernicusClient.fetchHistoricalData(details.polygon(), details.name());
            List<WeeklyWaterMetric> metricsDto = parserService.parseToMeansOnly(rawJson);
            if (metricsDto.isEmpty()) {
                throw new RuntimeException("Copernicus returned no weekly metrics.");
            }

            // Update or create in DB.
            WaterBodyEntity entity = cachedByCoordinates
                    .orElseGet(() -> waterBodyMapper.toEntity(details, lat, lon));
            if (cachedByCoordinates.isPresent()) {
                waterBodyMapper.updateEntity(entity, details, lat, lon);
            }

            // Map and link metrics.
            List<WaterMetricEntity> metricEntities = metricsDto.stream()
                    .map(dto -> mapToEntity(dto, entity))
                    .collect(Collectors.toList());

            entity.setMetrics(metricEntities);
            WaterBodyEntity savedEntity = waterBodyRepository.save(entity);

            return new CopernicusMetrics(String.valueOf(savedEntity.getId()), details, metricsDto);
        } catch (Exception ex) {
            Optional<WaterBodyEntity> cached = cachedByCoordinates.isPresent()
                    ? cachedByCoordinates
                    : waterBodyRepository.findByName(details.name());

            if (cached.isPresent() && hasMetrics(cached.get()) && hasReliableOutline(cached.get())) {
                log.warn("Fresh Copernicus fetch failed for {}, {}. Returning cached DB data instead.", lat, lon, ex);
                return toCopernicusMetrics(cached.get());
            }

            String rootCause = rootCauseMessage(ex);
            log.warn("Fresh Copernicus fetch failed for {}, {} with no usable cached metrics: {}", lat, lon, rootCause, ex);
            throw new RuntimeException("Satellite data fetch failed and no cached metrics are available: " + rootCause, ex);
        }
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
                            String.valueOf(e.getId()),
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
                        String.valueOf(entity.getId()),
                        waterBodyMapper.toDto(entity),
                        entity.getMetrics().stream().map(waterMetricMapper::toDto).toList()
                ))
                .toList();
    }

    @Transactional
    public boolean deleteWaterBodyById(long id) {
        if (!waterBodyRepository.existsById(id)) {
            return false;
        }

        waterBodyRepository.deleteById(id);
        return true;
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

    private CopernicusMetrics toCopernicusMetrics(WaterBodyEntity entity) {
        return new CopernicusMetrics(
                String.valueOf(entity.getId()),
                waterBodyMapper.toDto(entity),
                entity.getMetrics().stream().map(waterMetricMapper::toDto).toList()
        );
    }

    private boolean hasMetrics(WaterBodyEntity entity) {
        return entity.getMetrics() != null && !entity.getMetrics().isEmpty();
    }

    private boolean hasReliableOutline(WaterBodyEntity entity) {
        return entity.getName() != null && !entity.getName().startsWith("Water body at ");
    }

    private String rootCauseMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }

        String message = cursor.getMessage() != null ? cursor.getMessage() : cursor.getClass().getSimpleName();
        if (message.contains("ACCESS_INSUFFICIENT_PROCESSING_UNITS")
                || message.contains("Insufficient processing units")) {
            return "Copernicus account has insufficient processing units or request credits.";
        }

        return message;
    }

    private WaterBodyDetails fetchWaterBodyDetails(double lat, double lon) {
        try {
            WaterBodyDetails details = overpassClient.getLakePolygon(lat, lon);
            if (!details.polygon().isEmpty()) {
                return details;
            }
            log.warn("Overpass returned no water body for {}, {}.", lat, lon);
        } catch (Exception ex) {
            log.warn("Overpass outline lookup failed for {}, {}.", lat, lon, ex);
        }

        throw new RuntimeException("OpenStreetMap outline lookup failed. Choose a point inside the water body and try again.");
    }
}
