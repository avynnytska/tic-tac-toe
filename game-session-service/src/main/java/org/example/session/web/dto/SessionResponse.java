package org.example.session.web.dto;

import org.example.session.domain.GameStatus;
import org.example.session.domain.Move;
import org.example.session.domain.Player;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionResponse(
        UUID sessionId,
        UUID gameId,
        Instant createdAt,
        String[][] board,
        GameStatus status,
        Player winner,
        List<Move> moves
) {}
