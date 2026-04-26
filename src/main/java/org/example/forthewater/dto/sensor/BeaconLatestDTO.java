package org.example.forthewater.dto.sensor;

import org.example.forthewater.model.BeaconReadingEntity;

public record BeaconLatestDTO(
        String uuid,
        BeaconReadingEntity latestReading
) {}