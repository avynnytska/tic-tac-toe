package org.example.session.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.session.client.EngineClient;
import org.example.session.client.EngineGameState;
import org.example.session.domain.Move;
import org.example.session.domain.Player;
import org.example.session.domain.Session;
import org.example.session.exception.EngineCommunicationException;
import org.example.session.exception.SessionAlreadyRunningException;
import org.example.session.exception.SessionNotFoundException;
import org.example.session.storage.SessionStore;
import org.example.session.sse.SessionEventBroker;
import org.example.session.strategy.MoveStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class SessionService {

    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> runningSessions = ConcurrentHashMap.newKeySet();
    private final EngineClient engineClient;
    private final MoveStrategy strategy;
    private final SessionEventBroker broker;
    private final SessionStore sessionStore;
    private final long moveDelayMs;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public SessionService(EngineClient engineClient,
                          MoveStrategy strategy,
                          SessionEventBroker broker,
                          SessionStore sessionStore,
                          @Value("${session.move-delay-ms:0}") long moveDelayMs) {
        this.engineClient = engineClient;
        this.strategy = strategy;
        this.broker = broker;
        this.sessionStore = sessionStore;
        this.moveDelayMs = moveDelayMs;
    }

    @PostConstruct
    public void loadFromStorage() {
        sessionStore.loadAll().forEach(s -> sessions.putIfAbsent(s.getId(), s));
    }

    public Session createSession() {
        UUID sessionId = UUID.randomUUID();
        UUID gameId = sessionId;
        EngineGameState gameState = requireEngineState(engineClient.createGame(gameId), "create game");
        Session session = new Session(sessionId, gameId);
        updateSessionFromEngineGameState(session, gameState);
        sessions.put(sessionId, session);
        sessionStore.save(session);
        return session;
    }

    public Session getSession(UUID id) {
        return Optional.ofNullable(sessions.get(id))
                .orElseThrow(() -> new SessionNotFoundException(id));
    }

    public Session simulate(UUID sessionId) {
        acquireSimulationLock(sessionId);
        try {
            return runSimulation(sessionId);
        } finally {
            runningSessions.remove(sessionId);
        }
    }

    private Session runSimulation(UUID sessionId) {
        Session session = getSession(sessionId);
        EngineGameState gameState = requireEngineState(engineClient.getGame(session.getGameId()), "get game");
        broker.publish(sessionId, "state", gameState);

        while (!gameState.status().isFinished()) {
            Player current = gameState.nextPlayer();
            int[] move = strategy.nextMove(gameState.board(), current);
            gameState = requireEngineState(
                    engineClient.applyMove(session.getGameId(), current, move[0], move[1]),
                    "apply move"
            );
            Move recorded = new Move(current, move[0], move[1], Instant.now());
            recordMove(session, recorded, gameState);
            broker.publish(sessionId, "move", new MovePayload(recorded, gameState));

            if (moveDelayMs > 0) {
                try {
                    Thread.sleep(moveDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        broker.publish(sessionId, "finished", gameState);
        broker.complete(sessionId);
        return session;
    }

    public void simulateAsync(UUID sessionId) {
        getSession(sessionId);
        acquireSimulationLock(sessionId);
        executor.submit(() -> {
            try {
                runSimulation(sessionId);
            } catch (Exception e) {
                broker.publish(sessionId, "failed", Map.of("message", e.getMessage()));
                broker.complete(sessionId);
            } finally {
                runningSessions.remove(sessionId);
            }
        });
    }

    private void acquireSimulationLock(UUID sessionId) {
        if (!runningSessions.add(sessionId)) {
            throw new SessionAlreadyRunningException(sessionId);
        }
    }

    private EngineGameState requireEngineState(EngineGameState state, String operation) {
        if (state == null) {
            throw new EngineCommunicationException("Engine returned empty response during " + operation);
        }
        return state;
    }

    private void recordMove(Session session, Move move, EngineGameState state) {
        session.addMove(move);
        updateSessionFromEngineGameState(session, state);
        sessionStore.save(session);
    }

    private void updateSessionFromEngineGameState(Session session, EngineGameState state) {
        session.setBoard(Objects.requireNonNull(state.board(), "Engine game board must not be null"));
        session.setStatus(Objects.requireNonNull(state.status(), "Engine game status must not be null"));
        session.setWinner(state.winner());
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    public record MovePayload(Move move, EngineGameState state) {}
}
