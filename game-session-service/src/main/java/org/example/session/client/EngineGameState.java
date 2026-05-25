package org.example.session.client;

import org.example.session.domain.GameStatus;
import org.example.session.domain.Player;

import java.util.UUID;

public record EngineGameState(
        UUID id,
        String[][] board,
        Player nextPlayer,
        GameStatus status,
        Player winner
) {}
