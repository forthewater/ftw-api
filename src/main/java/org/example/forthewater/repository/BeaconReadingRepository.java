package org.example.forthewater.repository;

import org.example.forthewater.model.BeaconEntity;
import org.example.forthewater.model.BeaconReadingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BeaconReadingRepository extends JpaRepository<BeaconReadingEntity, Long> {

    /**
     * Gets the most recent pulse for a specific beacon.
     * Used for the "Live Map" view to show current coordinates.
     */
    Optional<BeaconReadingEntity> findFirstByBeaconOrderByTimestampDesc(BeaconEntity beacon);

    /**
     * Gets the full history for a beacon.
     * Use this to draw the movement trail on the UI.
     */
    List<BeaconReadingEntity> findByBeaconOrderByTimestampDesc(BeaconEntity beacon);
}