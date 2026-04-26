package org.example.forthewater.repository;

import org.example.forthewater.model.BeaconEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BeaconRepository extends JpaRepository<BeaconEntity, Long> {

    // Used to look up the parent device when data arrives via UUID
    Optional<BeaconEntity> findByUuid(String uuid);

    // Quick check for the "Vibe Check" logic
    boolean existsByUuid(String uuid);
}