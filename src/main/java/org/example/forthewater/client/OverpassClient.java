package org.example.forthewater.client;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.forthewater.Coordinate;
import org.example.forthewater.dto.WaterBodyDetails;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
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

        RuntimeException lastFailure = null;
        for (int radius : searchRadii()) {
            try {
                WaterBodyDetails details = fetchLakePolygon(lat, lon, radius);
                if (!details.polygon().isEmpty()) {
                    return details;
                }
                log.warn("Overpass returned no usable polygon for {}, {} within {}m: {}", lat, lon, radius, details.warning());
            } catch (RuntimeException ex) {
                lastFailure = ex;
                log.warn("Overpass lookup failed for {}, {} within {}m. Retrying if another radius is available.", lat, lon, radius, ex);
            }
        }

        if (lastFailure != null) {
            throw lastFailure;
        }

        return new WaterBodyDetails(null, List.of(), "No usable water body polygon found near the selected point.");
    }

    private int[] searchRadii() {
        return new int[] {
                searchRadius,
                Math.max(searchRadius * 2, 2000),
                Math.max(searchRadius * 4, 5000)
        };
    }

    private WaterBodyDetails fetchLakePolygon(double lat, double lon, int radius) {

        String query = String.format("""
            [out:json];
            (
              way["natural"="water"](around:%d, %f, %f);
              relation["natural"="water"](around:%d, %f, %f);
              way["water"~"lake|pond|reservoir|basin"](around:%d, %f, %f);
              relation["water"~"lake|pond|reservoir|basin"](around:%d, %f, %f);
              way["landuse"="reservoir"](around:%d, %f, %f);
              relation["landuse"="reservoir"](around:%d, %f, %f);
              way["water"="reservoir"](around:%d, %f, %f);
              relation["water"="reservoir"](around:%d, %f, %f);
            );
            out geom;
            """,
                radius, lat, lon,
                radius, lat, lon,
                radius, lat, lon,
                radius, lat, lon,
                radius, lat, lon,
                radius, lat, lon,
                radius, lat, lon,
                radius, lat, lon);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("data", query);

        JsonNode response = overpassRestClient.post()
                .uri("")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        return parseOverpassResponse(response, lat, lon);
    }

    private WaterBodyDetails parseOverpassResponse(JsonNode rootNode, double lat, double lon) {
        JsonNode elements = rootNode.path("elements");
        if (elements.isMissingNode() || !elements.isArray() || elements.isEmpty()) {
            return new WaterBodyDetails(null, List.of(), "No water body found in the given radius.");
        }

        List<WaterCandidate> candidates = new ArrayList<>();

        for (JsonNode waterBody : elements) {
            String type = waterBody.path("type").asText();
            String name = waterBody.path("tags").path("name").asText("Unknown Water Body");
            List<Coordinate> polygon = new ArrayList<>();

            if ("way".equals(type)) {
                polygon = readGeometry(waterBody.path("geometry"));
            } else if ("relation".equals(type)) {
                // Massive lakes are broken segments. We must collect them and stitch them.
                List<List<Coordinate>> brokenSegments = new ArrayList<>();
                JsonNode members = waterBody.path("members");

                for (JsonNode member : members) {
                    if ("outer".equals(member.path("role").asText())) {
                        List<Coordinate> segment = readGeometry(member.path("geometry"));
                        if (segment.size() >= 2) brokenSegments.add(segment);
                    }
                }

                polygon = stitchSegments(brokenSegments);
            }

            polygon = normalizePolygon(polygon);
            if (!isUsablePolygon(polygon)) {
                continue;
            }

            candidates.add(new WaterCandidate(
                    name,
                    polygon,
                    pointInPolygon(lat, lon, polygon),
                    distanceToPolygonMeters(lat, lon, polygon),
                    Math.abs(polygonArea(polygon))
            ));
        }

        return candidates.stream()
                .min(Comparator
                        .comparing((WaterCandidate candidate) -> !candidate.containsPoint())
                        .thenComparingDouble(WaterCandidate::distanceMeters)
                        .thenComparing(Comparator.comparingDouble(WaterCandidate::area).reversed()))
                .map(candidate -> new WaterBodyDetails(candidate.name(), candidate.polygon(), null))
                .orElseGet(() -> new WaterBodyDetails(null, List.of(), "No usable water body polygon found in the given radius."));
    }

    private List<Coordinate> readGeometry(JsonNode geometry) {
        List<Coordinate> coordinates = new ArrayList<>();
        if (!geometry.isArray()) {
            return coordinates;
        }

        for (JsonNode node : geometry) {
            JsonNode lat = node.path("lat");
            JsonNode lon = node.path("lon");
            if (!lat.isNumber() || !lon.isNumber()) {
                continue;
            }
            coordinates.add(new Coordinate(lat.asDouble(), lon.asDouble()));
        }
        return coordinates;
    }

    private List<Coordinate> normalizePolygon(List<Coordinate> polygon) {
        List<Coordinate> normalized = new ArrayList<>();
        for (Coordinate coordinate : polygon) {
            if (normalized.isEmpty() || !samePoint(normalized.get(normalized.size() - 1), coordinate)) {
                normalized.add(coordinate);
            }
        }

        if (normalized.size() >= 3 && !samePoint(normalized.get(0), normalized.get(normalized.size() - 1))) {
            normalized.add(normalized.get(0));
        }

        return normalized;
    }

    private boolean isUsablePolygon(List<Coordinate> polygon) {
        return polygon.size() >= 4 && Math.abs(polygonArea(polygon)) > 0.00000001;
    }

    private boolean samePoint(Coordinate a, Coordinate b) {
        return Math.abs(a.lat() - b.lat()) < 0.0000001 && Math.abs(a.lon() - b.lon()) < 0.0000001;
    }

    private double polygonArea(List<Coordinate> polygon) {
        double area = 0;
        for (int i = 0; i < polygon.size() - 1; i++) {
            Coordinate current = polygon.get(i);
            Coordinate next = polygon.get(i + 1);
            area += current.lon() * next.lat() - next.lon() * current.lat();
        }
        return area / 2.0;
    }

    private boolean pointInPolygon(double lat, double lon, List<Coordinate> polygon) {
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            Coordinate pi = polygon.get(i);
            Coordinate pj = polygon.get(j);
            boolean intersects = ((pi.lat() > lat) != (pj.lat() > lat))
                    && (lon < (pj.lon() - pi.lon()) * (lat - pi.lat()) / (pj.lat() - pi.lat()) + pi.lon());
            if (intersects) inside = !inside;
        }
        return inside;
    }

    private double distanceToPolygonMeters(double lat, double lon, List<Coordinate> polygon) {
        if (pointInPolygon(lat, lon, polygon)) {
            return 0;
        }

        double minDistance = Double.MAX_VALUE;
        for (int i = 0; i < polygon.size() - 1; i++) {
            minDistance = Math.min(minDistance, distanceToSegmentMeters(lat, lon, polygon.get(i), polygon.get(i + 1)));
        }
        return minDistance;
    }

    private double distanceToSegmentMeters(double lat, double lon, Coordinate a, Coordinate b) {
        double metersPerLon = 111_320.0 * Math.cos(Math.toRadians(lat));
        double px = 0;
        double py = 0;
        double ax = (a.lon() - lon) * metersPerLon;
        double ay = (a.lat() - lat) * 110_540.0;
        double bx = (b.lon() - lon) * metersPerLon;
        double by = (b.lat() - lat) * 110_540.0;

        double dx = bx - ax;
        double dy = by - ay;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0) {
            return Math.hypot(px - ax, py - ay);
        }

        double t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lengthSquared));
        double closestX = ax + t * dx;
        double closestY = ay + t * dy;
        return Math.hypot(px - closestX, py - closestY);
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

    private record WaterCandidate(
            String name,
            List<Coordinate> polygon,
            boolean containsPoint,
            double distanceMeters,
            double area
    ) {}
}
