package org.example.forthewater.mapper;

import lombok.RequiredArgsConstructor;
import org.example.forthewater.dto.WaterBodyDetails;
import org.example.forthewater.Coordinate;
import org.example.forthewater.model.WaterBodyEntity;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WaterBodyMapper {

    private final ObjectMapper objectMapper;

    // Convert DTO to Entity (For Saving)
    public WaterBodyEntity toEntity(WaterBodyDetails dto, double lat, double lon) {
        return WaterBodyEntity.builder()
                .name(dto.name())
                .centerLat(lat)
                .centerLon(lon)
                .polygonJson(serializePolygon(dto.polygon()))
                .build();
    }

    // Convert Entity to DTO (For Returning to Frontend)
    public WaterBodyDetails toDto(WaterBodyEntity entity) {
        return new WaterBodyDetails(
                entity.getName(),
                deserializePolygon(entity.getPolygonJson()),
                null // Warning is usually null when coming from DB
        );
    }

    private String serializePolygon(List<Coordinate> polygon) {
        try {
            return objectMapper.writeValueAsString(polygon);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize polygon to JSON", e);
        }
    }

    private List<Coordinate> deserializePolygon(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Coordinate>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize polygon from JSON", e);
        }
    }
}