package org.example.forthewater.repository;

import org.example.forthewater.model.WaterBodyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WaterBodyRepository extends JpaRepository<WaterBodyEntity, Long> {

    /**
     * Find a water body by its unique name (e.g., "Iskar Reservoir").
     * Used to check if we already have the polygon and metadata cached.
     */
    Optional<WaterBodyEntity> findByName(String name);

    /**
     * Check if a water body exists by name.
     */
    boolean existsByName(String name);

    Optional<WaterBodyEntity> findByCenterLatAndCenterLon(double centerLat, double centerLon);
}