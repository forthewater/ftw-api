package org.example.forthewater;

import org.example.forthewater.dto.WaterBodyDetails;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class WaterTrackingService {
    private final org.example.forthewater.client.OverpassClient overpassClient;

    public WaterTrackingService(org.example.forthewater.client.OverpassClient overpassClient) {
        this.overpassClient = overpassClient;
    }

    public WaterBodyDetails initializeWaterBodyTracking(double lat, double lon) {
        // 1. Synchronous (Fast): Overpass RestClient executes instantly
        WaterBodyDetails details = overpassClient.getLakePolygon(lat, lon);

//        // 2. Asynchronous (Slow): We push the Copernicus RestClient call into a separate thread
//        CompletableFuture.runAsync(() -> {
//            // This will take a few seconds, but it won't block the UI
//            copernicusClient.triggerHistoricalDataAnalysis(details.polygon(), details.name());
//        });

        // 3. UI gets the polygon immediately to draw the map
        return details;
    }
}