package org.example.forthewater.dto.sensor;


public record BylhiData(
        Double activity,
        Double speed_px_s,
        Double immobility,
        Double dispersion_px,
        Double anomaly_score,
        int anomaly_flag
) {}