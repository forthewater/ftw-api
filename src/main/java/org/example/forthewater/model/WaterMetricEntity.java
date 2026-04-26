package org.example.forthewater.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "water_metrics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaterMetricEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "water_body_id", nullable = false)
    private WaterBodyEntity waterBody;

    @Column(nullable = false)
    private OffsetDateTime fromDate;

    // Your primary search and sort column
    @Column(nullable = false)
    private OffsetDateTime toDate;

    private double ndwi;
    private double ndci;
    private double turbidity;
    private double dataMask;
}