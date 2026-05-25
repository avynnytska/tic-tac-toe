package org.example.session.storage.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.session.domain.GameStatus;
import org.example.session.domain.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "sessions")
@Getter
@Setter
@NoArgsConstructor
public class SessionEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID gameId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(length = 9, nullable = false)
    private String board;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameStatus status;

    @Enumerated(EnumType.STRING)
    private Player winner;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderColumn(name = "move_index")
    private List<MoveEntity> moves = new ArrayList<>();
}
