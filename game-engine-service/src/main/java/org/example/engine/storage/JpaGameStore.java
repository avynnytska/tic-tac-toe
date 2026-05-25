package org.example.engine.storage;

import lombok.RequiredArgsConstructor;
import org.example.engine.domain.Game;
import org.example.engine.mapper.GameMapper;
import org.example.engine.storage.entity.GameEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JpaGameStore implements GameStore {

    private final GameRepository repository;
    private final GameMapper mapper;

    @Override
    public void save(Game game) {
        repository.save(mapper.toEntity(game));
    }

    @Override
    public List<Game> loadAll() {
        return repository.findAll().stream().map(mapper::toDomain).toList();
    }
}
