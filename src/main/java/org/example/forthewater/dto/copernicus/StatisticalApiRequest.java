package org.example.forthewater.dto.copernicus;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class StatisticalApiRequest {

    private Input input;
    private Aggregation aggregation; // Everything happens in here now!

    @Data
    @Builder
    public static class Input {
        private Bounds bounds;
        private List<DataSource> data;
    }

    @Data
    @Builder
    public static class Bounds {
        private Geometry geometry;
    }

    @Data
    @Builder
    public static class Geometry {
        @Builder.Default
        private String type = "Polygon";
        private List<List<List<Double>>> coordinates;
    }

    @Data
    @Builder
    public static class DataSource {
        @Builder.Default
        private String type = "sentinel-2-l2a";
        // Notice dataFilter is completely gone!
    }

    @Data
    @Builder
    public static class TimeRange {
        private String from;
        private String to;
    }

    @Data
    @Builder
    public static class AggregationInterval {
        @Builder.Default
        private String of = "P1W"; // "P1W" = 1 Week, "P1D" = 1 Day
    }

    @Data
    @Builder
    public static class Aggregation {
        private TimeRange timeRange;
        private AggregationInterval aggregationInterval;
        private String evalscript;

        // THE FIX: Changed from Integer to Double, and converted to Degrees!
        @Builder.Default
        private Double resx = 0.0001; // ~11 meters in degrees
        @Builder.Default
        private Double resy = 0.0001; // ~11 meters in degrees
    }
}