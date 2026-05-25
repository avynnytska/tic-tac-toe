package org.example.engine;

import org.example.engine.domain.Game;
import org.example.engine.domain.GameStatus;
import org.example.engine.domain.Player;
import org.example.engine.mapper.GameMapper;
import org.example.engine.storage.GameRepository;
import org.example.engine.storage.JpaGameStore;
import org.example.engine.service.GameEngineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class JpaGameStoreTest {

    @Autowired
    GameEngineService service;

    @Autowired
    GameRepository repository;

    @Autowired
    GameMapper mapper;

    @Test
    void moves_areWrittenThroughToH2() {
        Game g = service.createGame();
        service.applyMove(g.getId(), Player.X, 1, 1);
        service.applyMove(g.getId(), Player.O, 0, 0);

        var entity = repository.findById(g.getId()).orElseThrow();
        assertEquals("O___X____", entity.getBoard());
        assertEquals(Player.X, entity.getNextPlayer());
        assertEquals(GameStatus.IN_PROGRESS, entity.getStatus());
    }

    @Test
    void simulatedRestart_recoversFromH2() {
        Game g = service.createGame();
        UUID id = g.getId();
        service.applyMove(id, Player.X, 0, 0);
        service.applyMove(id, Player.O, 1, 1);

        // Build a fresh service backed by the same repository — mimics restart
        GameEngineService recovered = new GameEngineService(new JpaGameStore(repository, mapper));
        recovered.loadFromStorage();

        Game restored = recovered.getGame(id);
        assertEquals(Player.X, restored.getBoard()[0][0]);
        assertEquals(Player.O, restored.getBoard()[1][1]);
        assertEquals(Player.X, restored.getNextPlayer());
        assertEquals(GameStatus.IN_PROGRESS, restored.getStatus());
    }
}
