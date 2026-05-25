package org.example.engine.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.example.engine.domain.Game;
import org.example.engine.domain.Player;
import org.example.engine.exception.GameAlreadyExistsException;
import org.example.engine.exception.GameNotFoundException;
import org.example.engine.exception.InvalidMoveException;
import org.example.engine.storage.GameStore;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class GameEngineService {

    private static final int BOARD_SIZE = 3;

    private final ConcurrentHashMap<UUID, Game> games = new ConcurrentHashMap<>();
    private final GameStore gameStore;

    @PostConstruct
    public void loadFromStorage() {
        gameStore.loadAll().forEach(g -> games.putIfAbsent(g.getId(), g));
    }

    public Game createGame() {
        return createGame(null);
    }

    public Game createGame(UUID requestedId) {
        if (requestedId != null) {
            return createGameWithId(requestedId);
        }

        while (true) {
            UUID generatedId = UUID.randomUUID();
            Game game = new Game(generatedId);
            if (games.putIfAbsent(generatedId, game) == null) {
                gameStore.save(game);
                return game;
            }
        }
    }

    private Game createGameWithId(UUID id) {
        Game game = new Game(id);
        if (games.putIfAbsent(id, game) != null) {
            throw new GameAlreadyExistsException(id);
        }
        gameStore.save(game);
        return game;
    }

    public Game getGame(UUID id) {
        Game game = games.get(id);
        if (game == null) throw new GameNotFoundException(id);
        return game;
    }

    public Game applyMove(UUID gameId, Player player, int row, int col) {
        Game game = getGame(gameId);
        synchronized (game) {
            validateMove(game, player, row, col);
            game.applyMove(player, row, col);
            gameStore.save(game);
            return game;
        }
    }

    private void validateMove(Game game, Player player, int row, int col) {
        if (game.getStatus().isFinished()) {
            throw new InvalidMoveException("Game is already finished: " + game.getStatus());
        }
        if (isOutOfBounds(row, col)) {
            throw new InvalidMoveException("Position out of bounds: (" + row + "," + col + ")");
        }
        if (game.getNextPlayer() != player) {
            throw new InvalidMoveException("Not " + player + "'s turn, expected " + game.getNextPlayer());
        }
        if (!game.isCellEmpty(row, col)) {
            throw new InvalidMoveException("Cell already occupied: (" + row + "," + col + ")");
        }
    }

    private boolean isOutOfBounds(int row, int col) {
        return row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE;
    }
}
