
package org.example.forthewater.mapper;

import org.example.forthewater.dto.WeeklyWaterMetric;
import org.example.forthewater.model.WaterMetricEntity;
import org.springframework.stereotype.Component;

@Component
public class WaterMetricMapper {

    public WeeklyWaterMetric toDto(WaterMetricEntity entity) {
        return new WeeklyWaterMetric(
                entity.getFromDate().toString(),
                entity.getToDate().toString(),
                entity.getNdwi(),
                entity.getNdci(),
                entity.getTurbidity()
        );
    }
}