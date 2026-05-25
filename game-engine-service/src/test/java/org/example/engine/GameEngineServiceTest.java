package org.example.engine;

import org.example.engine.domain.Game;
import org.example.engine.domain.GameStatus;
import org.example.engine.domain.Player;
import org.example.engine.exception.GameAlreadyExistsException;
import org.example.engine.exception.GameNotFoundException;
import org.example.engine.exception.InvalidMoveException;
import org.example.engine.storage.GameStore;
import org.example.engine.service.GameEngineService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GameEngineServiceTest {

    private final GameEngineService service = new GameEngineService(GameStore.NOOP);

    @Test
    void createGame_startsEmpty() {
        Game g = service.createGame();
        assertEquals(GameStatus.IN_PROGRESS, g.getStatus());
        assertEquals(Player.X, g.getNextPlayer());
        assertNull(g.getWinner());
    }

    @Test
    void createGame_rejectsDuplicateId() {
        UUID id = UUID.randomUUID();
        service.createGame(id);

        GameAlreadyExistsException ex = assertThrows(GameAlreadyExistsException.class, () -> service.createGame(id));
        assertTrue(ex.getMessage().contains(id.toString()));
    }

    @Test
    void rowWin_isDetected() {
        Game g = service.createGame();
        play(g, Player.X, 0, 0);
        play(g, Player.O, 1, 0);
        play(g, Player.X, 0, 1);
        play(g, Player.O, 1, 1);
        play(g, Player.X, 0, 2);
        assertEquals(GameStatus.X_WON, g.getStatus());
        assertEquals(Player.X, g.getWinner());
    }

    @Test
    void columnWin_isDetected() {
        Game g = service.createGame();
        play(g, Player.X, 0, 1);
        play(g, Player.O, 0, 0);
        play(g, Player.X, 0, 2);
        play(g, Player.O, 1, 0);
        play(g, Player.X, 1, 1);
        play(g, Player.O, 2, 0);
        assertEquals(GameStatus.O_WON, g.getStatus());
    }

    @Test
    void diagonalWin_isDetected() {
        Game g = service.createGame();
        play(g, Player.X, 0, 0);
        play(g, Player.O, 0, 1);
        play(g, Player.X, 1, 1);
        play(g, Player.O, 0, 2);
        play(g, Player.X, 2, 2);
        assertEquals(GameStatus.X_WON, g.getStatus());
    }

    @Test
    void draw_isDetected() {
        Game g = service.createGame();
        // X O X
        // X O O
        // O X X
        play(g, Player.X, 0, 0);
        play(g, Player.O, 0, 1);
        play(g, Player.X, 0, 2);
        play(g, Player.O, 1, 1);
        play(g, Player.X, 1, 0);
        play(g, Player.O, 2, 0);
        play(g, Player.X, 2, 1);
        play(g, Player.O, 1, 2);
        play(g, Player.X, 2, 2);
        assertEquals(GameStatus.DRAW, g.getStatus());
    }

    @Test
    void rejectsOccupiedCell() {
        Game g = service.createGame();
        play(g, Player.X, 1, 1);
        assertThrows(InvalidMoveException.class,
                () -> service.applyMove(g.getId(), Player.O, 1, 1));
    }

    @Test
    void rejectsWrongTurn() {
        Game g = service.createGame();
        assertThrows(InvalidMoveException.class,
                () -> service.applyMove(g.getId(), Player.O, 0, 0));
    }

    @Test
    void rejectsMoveAfterFinish() {
        Game g = service.createGame();
        play(g, Player.X, 0, 0);
        play(g, Player.O, 1, 0);
        play(g, Player.X, 0, 1);
        play(g, Player.O, 1, 1);
        play(g, Player.X, 0, 2);
        assertTrue(g.getStatus().isFinished());
        assertThrows(InvalidMoveException.class,
                () -> service.applyMove(g.getId(), Player.O, 2, 2));
    }

    @Test
    void rejectsOutOfBounds() {
        Game g = service.createGame();
        assertThrows(InvalidMoveException.class,
                () -> service.applyMove(g.getId(), Player.X, 3, 0));
    }

    @Test
    void unknownGame_throws() {
        assertThrows(GameNotFoundException.class, () -> service.getGame(UUID.randomUUID()));
    }

    private void play(Game game, Player player, int row, int col) {
        service.applyMove(game.getId(), player, row, col);
    }
}
