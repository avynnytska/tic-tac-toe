package org.example.engine.storage.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.engine.domain.GameStatus;
import org.example.engine.domain.Player;

import java.util.UUID;

@Entity
@Table(name = "games")
@Getter
@Setter
@NoArgsConstructor
public class GameEntity {

    @Id
    private UUID id;

    @Column(length = 9, nullable = false)
    private String board;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Player nextPlayer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameStatus status;

    @Enumerated(EnumType.STRING)
    private Player winner;

    public GameEntity(UUID id, String board, Player nextPlayer, GameStatus status, Player winner) {
        this.id = id;
        this.board = board;
        this.nextPlayer = nextPlayer;
        this.status = status;
        this.winner = winner;
    }
}
