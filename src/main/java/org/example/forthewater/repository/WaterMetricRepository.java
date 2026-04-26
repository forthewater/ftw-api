package org.example.forthewater.repository;

import org.example.forthewater.model.WaterMetricEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WaterMetricRepository extends JpaRepository<WaterMetricEntity, Long> {
    // Find all historical data for a specific lake
    List<WaterMetricEntity> findByWaterBodyNameOrderByToDateDesc(String name);
}