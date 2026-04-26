package org.example.forthewater.dto.sensor;

import java.time.OffsetDateTime;

public record BeaconLocationDTO(
        String uuid,
        String name,
        Double latitude,
        Double longitude,
        OffsetDateTime lastUpdated
) {}