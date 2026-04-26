package org.example.forthewater.client;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.forthewater.Coordinate;
import org.example.forthewater.dto.WaterBodyDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OverpassClient {

    private final RestClient overpassRestClient;

    @Value("${app.overpass.search-radius:1000}")
    private int searchRadius;

    public WaterBodyDetails getLakePolygon(double lat, double lon) {
        log.info("Fetching polygon for coordinates: {}, {}", lat, lon);

        String query = String.format("""
            [out:json];
            (
              way["natural"="water"](around:%d, %f, %f);
              relation["natural"="water"](around:%d, %f, %f);
              way["water"="reservoir"](around:%d, %f, %f);
              relation["water"="reservoir"](around:%d, %f, %f);
            );
            out geom;
            """, searchRadius, lat, lon, searchRadius, lat, lon, searchRadius, lat, lon, searchRadius, lat, lon);

        JsonNode response = overpassRestClient.get()
                .uri(uriBuilder -> uriBuilder.queryParam("data", query).build())
                .retrieve()
                .body(JsonNode.class);

        return parseOverpassResponse(response);
    }

    private WaterBodyDetails parseOverpassResponse(JsonNode rootNode) {
        JsonNode elements = rootNode.path("elements");
        if (elements.isMissingNode() || !elements.isArray() || elements.isEmpty()) {
            return new WaterBodyDetails(null, List.of(), "No water body found in the given radius.");
        }

        JsonNode waterBody = elements.get(0);
        String type = waterBody.path("type").asText();
        String name = waterBody.path("tags").path("name").asText("Unknown Water Body");

        List<Coordinate> finalPolygon = new ArrayList<>();

        if ("way".equals(type)) {
            // Simple lakes are already perfect loops
            JsonNode geometry = waterBody.path("geometry");
            for (JsonNode node : geometry) {
                finalPolygon.add(new Coordinate(node.path("lat").asDouble(), node.path("lon").asDouble()));
            }
        } else if ("relation".equals(type)) {
            // Massive lakes are broken segments. We must collect them and stitch them!
            List<List<Coordinate>> brokenSegments = new ArrayList<>();
            JsonNode members = waterBody.path("members");

            for (JsonNode member : members) {
                if ("outer".equals(member.path("role").asText())) {
                    List<Coordinate> segment = new ArrayList<>();
                    JsonNode geometry = member.path("geometry");
                    for (JsonNode node : geometry) {
                        segment.add(new Coordinate(node.path("lat").asDouble(), node.path("lon").asDouble()));
                    }
                    if (!segment.isEmpty()) brokenSegments.add(segment);
                }
            }
            // Execute the stiching algorithm
            finalPolygon = stitchSegments(brokenSegments);
        }

        return new WaterBodyDetails(name, finalPolygon, null);
    }

    /**
     * The Hackathon GIS Stitcher: Connects broken shoreline segments head-to-tail.
     */
    private List<Coordinate> stitchSegments(List<List<Coordinate>> segments) {
        if (segments.isEmpty()) return new ArrayList<>();

        // Start our master polygon with the first random segment
        List<Coordinate> stitched = new ArrayList<>(segments.remove(0));

        boolean matchFound = true;
        while (matchFound && !segments.isEmpty()) {
            matchFound = false;
            Coordinate currentStart = stitched.get(0);
            Coordinate currentEnd = stitched.get(stitched.size() - 1);

            for (int i = 0; i < segments.size(); i++) {
                List<Coordinate> piece = segments.get(i);
                Coordinate pieceStart = piece.get(0);
                Coordinate pieceEnd = piece.get(piece.size() - 1);

                if (isClose(currentEnd, pieceStart)) {
                    // Snaps perfectly to the end
                    stitched.addAll(piece.subList(1, piece.size()));
                    segments.remove(i);
                    matchFound = true; break;
                } else if (isClose(currentEnd, pieceEnd)) {
                    // Snaps to the end, but the piece is backwards! Reverse it first.
                    for (int j = piece.size() - 2; j >= 0; j--) stitched.add(piece.get(j));
                    segments.remove(i);
                    matchFound = true; break;
                } else if (isClose(currentStart, pieceEnd)) {
                    // Snaps to the front
                    stitched.addAll(0, piece.subList(0, piece.size() - 1));
                    segments.remove(i);
                    matchFound = true; break;
                } else if (isClose(currentStart, pieceStart)) {
                    // Snaps to the front, but backwards!
                    for (int j = 1; j < piece.size(); j++) stitched.add(0, piece.get(j));
                    segments.remove(i);
                    matchFound = true; break;
                }
            }
        }
        return stitched;
    }

    // Helper to check if two GPS coordinates are touching
    private boolean isClose(Coordinate a, Coordinate b) {
        return Math.abs(a.lat() - b.lat()) < 0.0001 && Math.abs(a.lon() - b.lon()) < 0.0001;
    }
}