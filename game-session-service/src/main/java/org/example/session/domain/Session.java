package org.example.session.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class Session {
    private final UUID id;
    private final UUID gameId;
    private final Instant createdAt = Instant.now();
    private final List<Move> moves = new ArrayList<>();

    @Setter
    private GameStatus status = GameStatus.IN_PROGRESS;
    @Setter
    private Player winner;
    @Setter
    private String[][] board = new String[3][3];

    public Session(UUID id, UUID gameId) {
        this.id = id;
        this.gameId = gameId;
    }

    public synchronized void addMove(Move move) {
        moves.add(move);
    }
}
