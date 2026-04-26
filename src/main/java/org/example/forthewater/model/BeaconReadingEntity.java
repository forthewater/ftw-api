package org.example.forthewater.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "beacon_readings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BeaconReadingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beacon_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore // Prevent infinite recursion in JSON
    private BeaconEntity beacon;

    private OffsetDateTime timestamp;
    private Double temperature;
    private Double latitude;
    private Double longitude;
    private Double ph;

    // Bylhi Metrics
    private Double activity;
    private Double speedPxS;
    private Double immobility;
    private Double dispersionPx;
    private Double anomalyScore;
    private Integer anomalyFlag;
}
