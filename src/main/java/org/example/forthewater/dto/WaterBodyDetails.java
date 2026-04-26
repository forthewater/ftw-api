package org.example.forthewater.dto;

import org.example.forthewater.Coordinate;

import java.util.List;

public record WaterBodyDetails(
        String name,
        List<Coordinate> polygon,
        String warning
) {}