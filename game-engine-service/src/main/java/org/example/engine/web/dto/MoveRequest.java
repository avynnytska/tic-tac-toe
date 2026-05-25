package org.example.engine.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.example.engine.domain.Player;

public record MoveRequest(
        @NotNull Player player,
        @Min(0) @Max(2) int row,
        @Min(0) @Max(2) int col
) {}
