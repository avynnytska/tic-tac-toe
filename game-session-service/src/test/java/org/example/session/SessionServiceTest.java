package org.example.session;

import org.example.session.client.EngineClient;
import org.example.session.client.EngineGameState;
import org.example.session.domain.GameStatus;
import org.example.session.domain.Player;
import org.example.session.domain.Session;
import org.example.session.exception.EngineCommunicationException;
import org.example.session.exception.SessionAlreadyRunningException;
import org.example.session.exception.SessionNotFoundException;
import org.example.session.storage.SessionStore;
import org.example.session.service.SessionService;
import org.example.session.sse.SessionEventBroker;
import org.example.session.strategy.RandomMoveStrategy;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionServiceTest {

    private final EngineClient engine = mock(EngineClient.class);
    private final SessionEventBroker broker = new SessionEventBroker();
    private final SessionService service = new SessionService(engine, new RandomMoveStrategy(), broker, SessionStore.NOOP, 0L);

    @Test
    void createSession_initializesGameInEngine() {
        AtomicReference<UUID> capturedGameId = new AtomicReference<>();
        when(engine.createGame(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            capturedGameId.set(id);
            return new EngineGameState(id, emptyBoard(), Player.X, GameStatus.IN_PROGRESS, null);
        });

        Session s = service.createSession();

        assertNotNull(s.getId());
        assertEquals(s.getId(), s.getGameId());
        assertEquals(s.getGameId(), capturedGameId.get());
        assertEquals(GameStatus.IN_PROGRESS, s.getStatus());
    }

    @Test
    void getSession_unknown_throws() {
        assertThrows(SessionNotFoundException.class, () -> service.getSession(UUID.randomUUID()));
    }

    @Test
    void createSession_whenEngineReturnsEmptyResponse_throws() {
        when(engine.createGame(any(UUID.class))).thenReturn(null);

        assertThrows(EngineCommunicationException.class, service::createSession);
    }

    @Test
    void simulate_runsUntilFinished() {
        when(engine.createGame(any(UUID.class)))
                .thenAnswer(inv -> new EngineGameState(inv.getArgument(0), emptyBoard(), Player.X, GameStatus.IN_PROGRESS, null));
        Session session = service.createSession();

        when(engine.getGame(session.getGameId()))
                .thenReturn(new EngineGameState(session.getGameId(), emptyBoard(), Player.X, GameStatus.IN_PROGRESS, null));

        String[][] b1 = emptyBoard();
        b1[0][0] = "X";
        String[][] b2 = emptyBoard();
        b2[0][0] = "X"; b2[1][0] = "O";
        String[][] b3 = emptyBoard();
        b3[0][0] = "X"; b3[1][0] = "O"; b3[0][1] = "X";
        when(engine.applyMove(eq(session.getGameId()), any(Player.class), anyInt(), anyInt()))
                .thenReturn(
                        new EngineGameState(session.getGameId(), b1, Player.O, GameStatus.IN_PROGRESS, null),
                        new EngineGameState(session.getGameId(), b2, Player.X, GameStatus.IN_PROGRESS, null),
                        new EngineGameState(session.getGameId(), b3, Player.O, GameStatus.X_WON, Player.X)
                );

        Session finished = service.simulate(session.getId());
        assertEquals(GameStatus.X_WON, finished.getStatus());
        assertEquals(Player.X, finished.getWinner());
        assertEquals(3, finished.getMoves().size());
    }

    @Test
    void simulate_whenSessionAlreadyRunning_throwsConflict() throws Exception {
        when(engine.createGame(any(UUID.class)))
                .thenAnswer(inv -> new EngineGameState(inv.getArgument(0), emptyBoard(), Player.X, GameStatus.IN_PROGRESS, null));
        Session session = service.createSession();
        when(engine.getGame(session.getGameId()))
                .thenReturn(new EngineGameState(session.getGameId(), emptyBoard(), Player.X, GameStatus.IN_PROGRESS, null));

        CountDownLatch moveStarted = new CountDownLatch(1);
        CountDownLatch releaseMove = new CountDownLatch(1);
        String[][] finalBoard = emptyBoard();
        finalBoard[0][0] = "X";
        when(engine.applyMove(eq(session.getGameId()), eq(Player.X), anyInt(), anyInt()))
                .thenAnswer(inv -> {
                    moveStarted.countDown();
                    assertTrue(releaseMove.await(5, TimeUnit.SECONDS));
                    return new EngineGameState(session.getGameId(), finalBoard, Player.O, GameStatus.X_WON, Player.X);
                });

        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<Session> running = pool.submit(() -> service.simulate(session.getId()));
        assertTrue(moveStarted.await(5, TimeUnit.SECONDS));

        assertThrows(SessionAlreadyRunningException.class, () -> service.simulate(session.getId()));

        releaseMove.countDown();
        assertEquals(GameStatus.X_WON, running.get(5, TimeUnit.SECONDS).getStatus());
        pool.shutdownNow();
    }

    private static String[][] emptyBoard() {
        return new String[3][3];
    }
}
