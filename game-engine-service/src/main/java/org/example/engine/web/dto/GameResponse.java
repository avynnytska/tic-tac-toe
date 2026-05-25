package org.example.engine.web.dto;

import org.example.engine.domain.GameStatus;
import org.example.engine.domain.Player;

import java.util.UUID;

public record GameResponse(
        UUID id,
        String[][] board,
        Player nextPlayer,
        GameStatus status,
        Player winner
) {}
