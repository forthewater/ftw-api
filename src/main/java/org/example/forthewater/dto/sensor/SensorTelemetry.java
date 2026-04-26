package org.example.forthewater.dto.sensor;

public record SensorTelemetry(
        Double temperature,
        Double lat,
        Double lon, // Note: Use 'longitude' if you want to avoid 'long' keyword issues, but sticking to your note
        Double ph,
        BylhiData bylhi
) {}
