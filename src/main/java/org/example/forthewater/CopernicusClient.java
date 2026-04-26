package org.example.forthewater.client; // Make sure this matches your package

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.forthewater.Coordinate;
import org.example.forthewater.CopernicusAuthClient;
import org.example.forthewater.dto.copernicus.StatisticalApiRequest;
import org.example.forthewater.service.CopernicusPayloadBuilderService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CopernicusClient {

    private final RestClient copernicusRestClient;
    private final CopernicusAuthClient authClient;
    private final CopernicusPayloadBuilderService payloadBuilder;

    public String fetchHistoricalData(List<Coordinate> polygonCoords, String waterBodyName) {
        log.info("Starting Copernicus Analysis for {}", waterBodyName);

        // 1. Grab the Auth Token
        String token = authClient.fetchAccessToken();

        // 2. Create a mutable list to build our GeoJSON coordinates
        List<List<Double>> closedRing = new ArrayList<>();

        // 3. Convert Coordinate objects to [Longitude, Latitude] pairs
        for (Coordinate c : polygonCoords) {
            closedRing.add(List.of(c.lon(), c.lat()));
        }

        // 4. === THE GEOJSON FIX ===
        // A GeoJSON Polygon MUST start and end with the exact same coordinate.
        if (!closedRing.isEmpty()) {
            List<Double> firstPoint = closedRing.get(0);
            List<Double> lastPoint = closedRing.get(closedRing.size() - 1);

            // If it is open, we simply duplicate the first point and stick it at the end to close the loop!
            if (!firstPoint.equals(lastPoint)) {
                closedRing.add(firstPoint);
            }
        }

        // 5. Wrap it in the deeply nested array structure Copernicus expects
        List<List<List<Double>>> geoJsonCoords = List.of(closedRing);

        // 6. Build the massive JSON request payload
        StatisticalApiRequest requestPayload = payloadBuilder.buildRequest(geoJsonCoords);

        log.info("Sending closed-ring polygon to Copernicus Statistical API...");

        // 7. Fire the request
        return copernicusRestClient.post()
                .uri("")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestPayload)
                .retrieve()
                .body(String.class);
    }
}