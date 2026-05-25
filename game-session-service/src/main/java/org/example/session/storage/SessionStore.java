package org.example.session.storage;

import org.example.session.domain.Session;

import java.util.List;

public interface SessionStore {

    void save(Session session);

    List<Session> loadAll();

    SessionStore NOOP = new SessionStore() {
        @Override public void save(Session session) {}
        @Override public List<Session> loadAll() { return List.of(); }
    };
}
