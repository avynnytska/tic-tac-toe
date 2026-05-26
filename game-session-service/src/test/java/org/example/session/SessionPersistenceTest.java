package org.example.session;

import org.example.engine.GameEngineApplication;
import org.example.session.domain.Session;
import org.example.session.mapper.MoveMapper;
import org.example.session.mapper.SessionMapper;
import org.example.session.storage.JpaSessionStore;
import org.example.session.storage.SessionRepository;
import org.example.session.service.SessionService;
import org.example.session.web.dto.SessionResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"session.move-delay-ms=0"}
)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sessionpersist;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SessionPersistenceTest {

    private static ConfigurableApplicationContext engineContext;

    @LocalServerPort
    int sessionPort;

    @Autowired
    SessionRepository repository;

    @Autowired
    SessionService service;

    @Autowired
    SessionMapper sessionMapper;

    @Autowired
    MoveMapper moveMapper;

    @BeforeAll
    static void startEngine() {
        engineContext = new SpringApplication(GameEngineApplication.class)
                .run("--server.port=0",
                        "--spring.datasource.url=jdbc:h2:mem:enginepersist;DB_CLOSE_DELAY=-1",
                        "--spring.jpa.hibernate.ddl-auto=create-drop");
    }

    @AfterAll
    static void stopEngine() {
        if (engineContext != null) engineContext.close();
    }

    @DynamicPropertySource
    static void overrideEngineUrl(DynamicPropertyRegistry registry) {
        registry.add("engine.base-url", () -> {
            int port = engineContext.getEnvironment().getProperty("local.server.port", Integer.class);
            return "http://localhost:" + port;
        });
    }

    @Test
    void simulationProgress_isPersisted_andRecovered() {
        RestClient client = RestClient.create("http://localhost:" + sessionPort);

        SessionResponse created = client.post().uri("/sessions").retrieve().body(SessionResponse.class);
        UUID sessionId = created.sessionId();

        SessionResponse finished = client.post()
                .uri("/sessions/{id}/simulate", sessionId)
                .retrieve()
                .body(SessionResponse.class);

        assertTrue(finished.status().isFinished());
        int movesPlayed = finished.moves().size();

        // Database has the entity with all moves
        var entity = repository.findById(sessionId).orElseThrow();
        assertEquals(movesPlayed, entity.getMoves().size());
        assertEquals(finished.status(), entity.getStatus());

        // Simulate restart: build a fresh service from the same repository
        SessionService recovered = new SessionService(
                null /* engineClient — unused for read */,
                null /* strategy — unused */,
                new org.example.session.sse.SessionEventBroker(),
                new JpaSessionStore(repository, sessionMapper, moveMapper),
                0L
        );
        recovered.loadFromStorage();

        Session restored = recovered.getSession(sessionId);
        assertEquals(movesPlayed, restored.getMoves().size());
        assertEquals(finished.status(), restored.getStatus());
        assertEquals(finished.winner(), restored.getWinner());
    }
}
