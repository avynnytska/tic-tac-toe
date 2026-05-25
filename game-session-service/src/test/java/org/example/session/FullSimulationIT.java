package org.example.session;

import org.example.engine.GameEngineApplication;
import org.example.session.domain.GameStatus;
import org.example.session.web.dto.SessionResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"session.move-delay-ms=0"}
)
class FullSimulationIT {

    private static ConfigurableApplicationContext engineContext;

    @LocalServerPort
    int sessionPort;

    @BeforeAll
    static void startEngine() {
        engineContext = new SpringApplication(GameEngineApplication.class)
                .run("--server.port=0", "--spring.main.web-application-type=servlet");
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
    void runFullGameThroughBothServices() {
        RestClient client = RestClient.create("http://localhost:" + sessionPort);

        SessionResponse created = client.post().uri("/sessions").retrieve().body(SessionResponse.class);
        assertNotNull(created);
        UUID sessionId = created.sessionId();

        SessionResponse finished = client.post()
                .uri("/sessions/{id}/simulate", sessionId)
                .retrieve()
                .body(SessionResponse.class);

        assertNotNull(finished);
        assertTrue(finished.status().isFinished(),
                "Expected game to be finished but was " + finished.status());
        assertTrue(finished.moves().size() >= 5 && finished.moves().size() <= 9,
                "Expected 5-9 moves but got " + finished.moves().size());
        if (finished.status() == GameStatus.X_WON || finished.status() == GameStatus.O_WON) {
            assertNotNull(finished.winner());
        }
    }
}
