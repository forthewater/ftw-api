package org.example.forthewater.dto.sensor;

import org.example.forthewater.model.BeaconReadingEntity;

public record BeaconStatusDTO(
        String uuid,
        BeaconReadingEntity latestReading
) {}