package org.example.session.client;

import org.example.session.domain.Player;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
public class EngineClient {

    private final RestClient restClient;

    public EngineClient(@Value("${engine.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public EngineGameState createGame(UUID gameId) {
        return restClient.post()
                .uri("/games")
                .body(Map.of("gameId", gameId))
                .retrieve()
                .body(EngineGameState.class);
    }

    public EngineGameState getGame(UUID gameId) {
        return restClient.get()
                .uri("/games/{id}", gameId)
                .retrieve()
                .body(EngineGameState.class);
    }

    public EngineGameState applyMove(UUID gameId, Player player, int row, int col) {
        return restClient.post()
                .uri("/games/{id}/move", gameId)
                .body(Map.of("player", player.name(), "row", row, "col", col))
                .retrieve()
                .body(EngineGameState.class);
    }
}
