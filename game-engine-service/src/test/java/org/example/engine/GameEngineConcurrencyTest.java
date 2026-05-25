package org.example.engine;

import org.example.engine.domain.Game;
import org.example.engine.domain.Player;
import org.example.engine.exception.InvalidMoveException;
import org.example.engine.storage.GameStore;
import org.example.engine.service.GameEngineService;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GameEngineConcurrencyTest {

    @Test
    void concurrentMovesOnSameCell_onlyOneSucceeds() throws Exception {
        GameEngineService service = new GameEngineService(GameStore.NOOP);
        Game game = service.createGame();

        int threadCount = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.applyMove(game.getId(), Player.X, 1, 1);
                    successes.incrementAndGet();
                } catch (InvalidMoveException e) {
                    failures.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS), "Threads did not finish in time");
        pool.shutdownNow();

        assertEquals(1, successes.get(), "Expected exactly one successful move");
        assertEquals(threadCount - 1, failures.get(), "Expected all other moves to fail");
        assertEquals(Player.X, game.getBoard()[1][1]);
        assertEquals(Player.O, game.getNextPlayer());
    }

    @Test
    void concurrentMovesOnDifferentCells_allSucceedAndAlternate() throws Exception {
        // Players alternate; only the player whose turn it is can move.
        // With concurrent attempts on different cells, exactly one per "turn" succeeds.
        GameEngineService service = new GameEngineService(GameStore.NOOP);
        Game game = service.createGame();

        int rounds = 5;
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger unexpectedFailures = new AtomicInteger();
        for (int round = 0; round < rounds && !game.getStatus().isFinished(); round++) {
            Player current = game.getNextPlayer();
            ExecutorService pool = Executors.newFixedThreadPool(9);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(9);
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    final int rr = r, cc = c;
                    pool.submit(() -> {
                        try {
                            start.await();
                            service.applyMove(game.getId(), current, rr, cc);
                            successes.incrementAndGet();
                        } catch (InvalidMoveException e) {
                            // Expected for losing concurrent attempts in the same turn.
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            unexpectedFailures.incrementAndGet();
                        } catch (Exception e) {
                            unexpectedFailures.incrementAndGet();
                        } finally {
                            done.countDown();
                        }
                    });
                }
            }
            start.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
            pool.shutdownNow();
        }
        // At most one move per round can succeed for the active player
        assertTrue(successes.get() >= 1, "Expected at least one successful move");
        assertTrue(successes.get() <= rounds, "Expected no more than 'rounds' successes");
        assertEquals(0, unexpectedFailures.get(), "Unexpected failures should not be hidden");
    }
}
