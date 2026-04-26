package org.example.forthewater.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "beacons")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BeaconEntity {


    @Column(unique = true, nullable = false)
    @Id
    private String uuid;

    @OneToMany(mappedBy = "beacon", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BeaconReadingEntity> readings;
}