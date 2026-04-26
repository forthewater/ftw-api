package org.example.forthewater.dto.sensor;

import java.time.OffsetDateTime;

public record BeaconLocationDTO(
        String uuid,
        Double latitude,
        Double longitude,
        OffsetDateTime lastUpdated
) {}