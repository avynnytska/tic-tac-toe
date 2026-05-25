package org.example.engine;

import tools.jackson.databind.ObjectMapper;
import org.example.engine.web.dto.MoveRequest;
import org.example.engine.domain.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:controllertest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class GameControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private UUID gameId;

    @BeforeEach
    void createGame() throws Exception {
        gameId = createGameViaApi();
    }

    private UUID createGameViaApi() throws Exception {
        String body = mvc.perform(post("/games"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        UUID createdId = UUID.fromString(json.readTree(body).get("id").asText());

        mvc.perform(get("/games/" + createdId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdId.toString()));

        return createdId;
    }

    @Test
    void create_returns201WithLocationAndBody() throws Exception {
        String body = mvc.perform(post("/games"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.nextPlayer").value("X"))
                .andReturn().getResponse().getContentAsString();

        UUID createdId = UUID.fromString(json.readTree(body).get("id").asText());
        mvc.perform(get("/games/" + createdId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdId.toString()));
    }

    @Test
    void getExistingGame_returnsCurrentState() throws Exception {
        mvc.perform(get("/games/" + gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.nextPlayer").value("X"));
    }

    @Test
    void createWithExistingId_returns409() throws Exception {
        String body = """
                {"gameId":"%s"}
                """.formatted(gameId);

        mvc.perform(post("/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("GAME_ALREADY_EXISTS"));
    }

    @Test
    void moveToEmptyCell_returnsUpdatedState() throws Exception {
        MoveRequest req = new MoveRequest(Player.X, 1, 1);
        mvc.perform(post("/games/" + gameId + "/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.board[1][1]").value("X"))
                .andExpect(jsonPath("$.nextPlayer").value("O"));
    }

    @Test
    void moveToOccupiedCell_returns422() throws Exception {
        MoveRequest first = new MoveRequest(Player.X, 0, 0);
        mvc.perform(post("/games/" + gameId + "/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(first))).andExpect(status().isOk());

        MoveRequest dup = new MoveRequest(Player.O, 0, 0);
        mvc.perform(post("/games/" + gameId + "/move")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dup)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("INVALID_MOVE"));
    }

    @Test
    void getUnknownGame_returns404() throws Exception {
        mvc.perform(get("/games/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void moveWithInvalidCoordinates_returns400() throws Exception {
        String body = """
                {"player":"X","row":5,"col":0}
                """;
        mvc.perform(post("/games/" + gameId + "/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
