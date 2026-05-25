package org.example.session.storage;

import lombok.RequiredArgsConstructor;
import org.example.session.domain.Move;
import org.example.session.domain.Session;
import org.example.session.mapper.MoveMapper;
import org.example.session.mapper.SessionMapper;
import org.example.session.storage.entity.SessionEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JpaSessionStore implements SessionStore {

    private final SessionRepository repository;
    private final SessionMapper sessionMapper;
    private final MoveMapper moveMapper;

    @Override
    @Transactional
    public void save(Session session) {
        SessionEntity entity = repository.findById(session.getId()).orElseGet(SessionEntity::new);
        sessionMapper.updateEntity(session, entity);

        // Sync moves: append any new ones (existing moves stay)
        int alreadyPersisted = entity.getMoves().size();
        List<Move> all = session.getMoves();
        for (int i = alreadyPersisted; i < all.size(); i++) {
            Move m = all.get(i);
            entity.getMoves().add(moveMapper.toEntity(m, entity));
        }
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Session> loadAll() {
        return repository.findAll().stream()
                .map(entity -> sessionMapper.toDomain(entity, moveMapper))
                .toList();
    }
}
