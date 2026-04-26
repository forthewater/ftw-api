package org.example.forthewater.service;

import lombok.extern.slf4j.Slf4j;
import org.example.forthewater.dto.copernicus.StatisticalApiRequest;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
public class CopernicusPayloadBuilderService {

    public StatisticalApiRequest buildRequest(List<List<List<Double>>> geoJsonCoordinates) {

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime fiveYearsAgo = now.minusYears(5);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String toDate = now.format(formatter);
        String fromDate = fiveYearsAgo.format(formatter);

        String multiMetricEvalScript = """
            function setup() {
                return {
                    input: ["B03", "B04", "B05", "B08", "dataMask"],
                    output: [
                        { id: "ndwi", bands: 1 },
                        { id: "ndci", bands: 1 },
                        { id: "turbidity", bands: 1 },
                        { id: "dataMask", bands: 1 } // <-- Added this!
                    ]
                };
            }
            function evaluatePixel(sample) {
                let ndwi = (sample.B03 - sample.B08) / (sample.B03 + sample.B08);
                let ndci = (sample.B05 - sample.B04) / (sample.B05 + sample.B04);
                let turbidity = sample.B04; 
                return {
                    ndwi: [ndwi],
                    ndci: [ndci],
                    turbidity: [turbidity],
                    dataMask: [sample.dataMask] // <-- Added this!
                };
            }
            """;

        return StatisticalApiRequest.builder()
                .input(StatisticalApiRequest.Input.builder()
                        .bounds(StatisticalApiRequest.Bounds.builder()
                                .geometry(StatisticalApiRequest.Geometry.builder()
                                        .coordinates(geoJsonCoordinates)
                                        .build())
                                .build())
                        .data(List.of(StatisticalApiRequest.DataSource.builder()
                                .build())) // Data source is just the type now
                        .build())
                .aggregation(StatisticalApiRequest.Aggregation.builder()
                        .timeRange(StatisticalApiRequest.TimeRange.builder()
                                .from(fromDate)
                                .to(toDate)
                                .build())
                        .aggregationInterval(StatisticalApiRequest.AggregationInterval.builder()
                                .of("P1W") // Weekly aggregation
                                .build())
                        .evalscript(multiMetricEvalScript) // Evalscript moved here!
                        // resx and resy default to 10 automatically
                        .build())
                .build();
    }
}