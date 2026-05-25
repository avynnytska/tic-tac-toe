package org.example.session.storage.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.session.domain.Player;

import java.time.Instant;

@Entity
@Table(name = "moves")
@Getter
@Setter
@NoArgsConstructor
public class MoveEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private SessionEntity session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Player player;

    @Column(name = "row_idx", nullable = false)
    private int row;

    @Column(name = "col_idx", nullable = false)
    private int col;

    @Column(nullable = false)
    private Instant playedAt;

    public MoveEntity(SessionEntity session, Player player, int row, int col, Instant playedAt) {
        this.session = session;
        this.player = player;
        this.row = row;
        this.col = col;
        this.playedAt = playedAt;
    }
}
