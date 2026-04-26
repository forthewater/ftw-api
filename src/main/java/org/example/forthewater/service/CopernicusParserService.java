package org.example.forthewater.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.forthewater.dto.WaterBodyDetails;
import org.example.forthewater.dto.WeeklyWaterMetric;
import org.example.forthewater.dto.copernicus.CopernicusMetrics;
import org.example.forthewater.mapper.WaterBodyMapper;
import org.example.forthewater.mapper.WaterMetricMapper;
import org.example.forthewater.model.WaterBodyEntity;
import org.example.forthewater.repository.WaterBodyRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CopernicusParserService {

    private final ObjectMapper objectMapper;
    private WaterBodyRepository waterBodyRepository;
    private WaterBodyMapper waterBodyMapper;
    private WaterMetricMapper waterMetricMapper;

    public List<WeeklyWaterMetric> parseToMeansOnly(String rawJson) {
        List<WeeklyWaterMetric> cleanData = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode dataArray = root.path("data");

            if (dataArray.isMissingNode() || !dataArray.isArray()) {
                log.warn("No 'data' array found in Copernicus response!");
                return cleanData;
            }

            for (JsonNode weekData : dataArray) {
                // 1. Grab the Date
                String from = weekData.path("interval").path("from").asText();
                String to = weekData.path("interval").path("to").asText();

                // 2. Navigate down to the 'mean' for each metric
                JsonNode outputs = weekData.path("outputs");

                double dataMaskMean = getMean(outputs, "dataMask");
                double ndwiMean = getMean(outputs, "ndwi");
                double ndciMean = getMean(outputs, "ndci");
                double turbidityMean = getMean(outputs, "turbidity");

                // 4. Build our clean object
                cleanData.add(WeeklyWaterMetric.builder()
                        .from(from)
                        .to(to)
                        .ndwi(ndwiMean)
                        .ndci(ndciMean)
                        .turbidity(turbidityMean)
                        .build());
            }

        } catch (Exception e) {
            log.error("Failed to parse Copernicus JSON", e);
        }

        return cleanData;
    }

    public CopernicusMetrics getFullWaterBodyData(double lat, double lon) {
        // 1. Find the parent using your fast index
        WaterBodyEntity waterBody = waterBodyRepository.findByCenterLatAndCenterLon(lat, lon)
                .orElseThrow(() -> new RuntimeException("Vibe Check Failed: Lake not in DB"));

        // 2. Convert the parent to Details (Polygon)
        WaterBodyDetails details = waterBodyMapper.toDto(waterBody);

        // 3. Convert the children (Metrics) to the DTO list
        List<WeeklyWaterMetric> metrics = waterBody.getMetrics().stream()
                .map(waterMetricMapper::toDto)
                .toList();

        return new CopernicusMetrics(details, metrics);
    }

    // A tiny helper method to stop us from writing .path().path().path() 100 times
    private double getMean(JsonNode outputs, String metricName) {
        return outputs.path(metricName)
                .path("bands")
                .path("B0")
                .path("stats")
                .path("mean")
                .asDouble(0.0); // Returns 0.0 if the data is missing
    }
}