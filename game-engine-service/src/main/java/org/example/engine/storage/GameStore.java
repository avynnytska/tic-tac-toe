package org.example.engine.storage;

import org.example.engine.domain.Game;

import java.util.List;

public interface GameStore {

    void save(Game game);

    List<Game> loadAll();

    GameStore NOOP = new GameStore() {
        @Override public void save(Game game) {}
        @Override public List<Game> loadAll() { return List.of(); }
    };
}
