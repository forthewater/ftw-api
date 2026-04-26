package org.example.forthewater.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "water_bodies", indexes = {
        @Index(name = "idx_coords", columnList = "centerLat, centerLon")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaterBodyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    // Use TEXT because polygons can exceed the 255 character limit of VARCHAR
    @Column(columnDefinition = "TEXT")
    private String polygonJson;

    private double centerLat;
    private double centerLon;

    @OneToMany(mappedBy = "waterBody", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WaterMetricEntity> metrics;
}