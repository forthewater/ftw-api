package org.example.forthewater.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class WeeklyWaterMetric {
    private String from;
    private String to;
    private double ndwi;      // Water Extent
    private double ndci;      // Chlorophyll / Algae
    private double turbidity; // Sediment
}