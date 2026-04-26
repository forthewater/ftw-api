package org.example.forthewater.dto.copernicus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.example.forthewater.dto.WaterBodyDetails;
import org.example.forthewater.dto.WeeklyWaterMetric;

import java.util.List;

@AllArgsConstructor
@Getter
@Setter
public class CopernicusMetrics {
    WaterBodyDetails waterBodyDetails;
    List<WeeklyWaterMetric> weeklyWaterMetrics;
}
