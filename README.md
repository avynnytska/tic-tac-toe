# Distributed Tic Tac Toe

Four Spring Boot microservices that play Tic Tac Toe automatically and stream the game to a browser UI. The UI also includes an extra **Play Manually** mode, so a user can create a session and submit moves directly through the engine API in addition to the required automated simulation.

```
┌──────────────┐     ┌─────────────┐     ┌─────────────────┐     ┌────────────────┐
│  ui-service  │ ──► │ api-gateway │ ──► │ session-service │ ──► │ engine-service │
│    :8080     │     │    :8000    │     │      :8082      │     │      :8081     │
└──────────────┘     └─────────────┘     └─────────────────┘     └────────────────┘
                          │                      │                       │
                          │   SSE stream         │   H2 (sessions)       │   H2 (games)
                          ◄──────────────────────┘                       │
                                                                        ─┘
```

* **game-engine-service** — owns the board, validates moves, detects wins/draws. Persists each game to H2.
* **game-session-service** — creates sessions, picks moves with `RandomMoveStrategy`, drives the game against the engine, streams progress over SSE. Persists sessions and moves to H2.
* **api-gateway** — single entry point on port 8000. Routes `/api/engine/**` and `/api/sessions/**` to the right service. Proxies SSE.
* **ui-service** — static HTML/JS that talks only to the gateway.

## Tech

* Java 21 · Spring Boot 4.0.6 · Gradle multi-module build
* Spring Cloud 2025.1.x · Spring Cloud Gateway Server WebFlux
* H2 file-based databases for both engine (`./data/engine-db`) and session (`./data/session-db`) — state survives restarts
* Server-Sent Events for live UI updates (proxied through the gateway)
* `RestClient` for all inter-service HTTP calls

## Running

You need four terminals (one per service):

```bash
./gradlew :game-engine-service:bootRun     # :8081
./gradlew :game-session-service:bootRun    # :8082
./gradlew :api-gateway:bootRun             # :8000
./gradlew :ui-service:bootRun              # :8080
```

Then open <http://localhost:8080>.

* Click **Start Simulation** to run the required automated microservice-vs-microservice game.
* Click **Play Manually** to use the optional manual mode, where the UI submits moves to the engine through the gateway.

## Testing

```bash
./gradlew test          # all unit + integration tests in every module
./gradlew :game-engine-service:test
./gradlew :game-session-service:test       # includes FullSimulationIT + SessionPersistenceTest
./gradlew :api-gateway:test                # ApiGatewayRoutingIT
```

Highlights:
* `GameEngineConcurrencyTest` — 32 threads racing on the same cell; exactly one wins.
* `GamePersistenceTest` / `SessionPersistenceTest` — simulate a restart by building a fresh service against the same H2 and asserting the prior game/session is recovered.
* `FullSimulationIT` — boots both engine and session Spring contexts on random ports and drives a full game over HTTP.
* `ApiGatewayRoutingIT` — boots Spring Cloud Gateway with stubbed upstream services and verifies the routing.

## API summary

Through the **gateway** (recommended):

| Method | Path                                     | Goes to → |
| ------ | ---------------------------------------- | --------- |
| POST   | `/api/engine/games`                      | engine    |
| POST   | `/api/engine/games/{gameId}/move`        | engine    |
| GET    | `/api/engine/games/{gameId}`             | engine    |
| POST   | `/api/sessions`                          | session   |
| POST   | `/api/sessions/{id}/simulate`            | session   |
| POST   | `/api/sessions/{id}/simulate-async`      | session   |
| GET    | `/api/sessions/{id}`                     | session   |
| GET    | `/api/sessions/{id}/stream` (SSE)        | session   |

The services are also reachable directly on `:8081` (engine) and `:8082` (session) without `/api` prefix.

Error codes: `404` not found, `409` conflict, `422` invalid move, `400` validation, `502` upstream engine error.

## Project layout

```
TicTacToe/
├── settings.gradle              includes the 4 modules
├── build.gradle                 shared subprojects config
├── game-engine-service/
│   └── src/main/java/org/example/engine/
│       ├── domain/        Game, GameStatus, Player
│       ├── service/       GameEngineService (rules + write-through)
│       ├── storage/       GameStore, JpaGameStore, GameRepository, entity/GameEntity
│       ├── mapper/        GameMapper
│       ├── web/           controller, dto, error
│       └── exception/
├── game-session-service/
│   └── src/main/java/org/example/session/
│       ├── domain/        Session, Move, ...
│       ├── service/       SessionService (simulation loop + write-through)
│       ├── storage/       SessionStore, JpaSessionStore, SessionRepository, entity/*
│       ├── mapper/        SessionMapper, MoveMapper
│       ├── strategy/      MoveStrategy + RandomMoveStrategy
│       ├── client/        EngineClient (RestClient wrapper)
│       ├── sse/           SessionEventBroker (SseEmitter registry)
│       └── web/           controller, dto, error
├── api-gateway/
│   └── src/main/java/org/example/gateway/
│       └── ApiGatewayApplication
└── ui-service/
    └── src/main/resources/static/   index.html, app.js, style.css
```

## Design notes

### Concurrency

* `GameEngineService.applyMove` synchronizes on the game instance and is covered by `GameEngineConcurrencyTest` (32 threads, exactly 1 success).
* `ConcurrentHashMap` for the per-game/per-session store.
* `CopyOnWriteArrayList` for SSE emitter lists per session.

### Persistence (H2 + JPA write-through)

The in-memory `ConcurrentHashMap` remains the source of truth at runtime, but every mutation is written through to H2. On startup, `@PostConstruct loadFromStorage()` rebuilds the in-memory state from the database. This gives:

* No JPA transactions on the hot path of move handling — keeps the synchronization model simple.
* Full recovery after a restart (verified by `GamePersistenceTest` and `SessionPersistenceTest`).
* A `GameStore` / `SessionStore` interface with a `NOOP` implementation, so unit tests don't need a database.

The default DB URL is file-based (`jdbc:h2:file:./data/...;AUTO_SERVER=TRUE`). Tests use `jdbc:h2:mem:...` overrides.

### API Gateway

The gateway uses **Spring Cloud Gateway Server WebFlux** with declarative routes in `application.yml`. It forwards `/api/engine/**` to the engine service and `/api/sessions/**` to the session service. SSE is proxied by the gateway as a normal streaming HTTP response.

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: engine
              uri: http://localhost:8081
              predicates: [Path=/api/engine/**]
              filters: [StripPrefix=2]
            - id: session
              uri: http://localhost:8082
              predicates: [Path=/api/sessions/**]
              filters: [StripPrefix=1]
```

### Real-time updates

SSE end-to-end: `SessionService.simulate` publishes `state`, `move`, and `finished` events through `SessionEventBroker`. Spring Cloud Gateway proxies the upstream streaming response to the UI, which subscribes with `EventSource` and renders each move as it arrives.

### Move pacing

`session.move-delay-ms` (default `1500` in production, `0` in tests) inserts a short pause between moves so the UI animation reads naturally. Tests override to `0` to keep them fast.

## Technical Debt / Trade-offs

**Runtime state is memory-first.** Services keep active games/sessions in memory and write changes to H2. If an in-memory update succeeds but the database write fails, state can temporarily diverge. A production version should use transactional persistence, retry handling, or make the database the primary source of truth.

**SSE broker is single-instance.** `SessionEventBroker` stores active browser connections in memory. This works for one `game-session-service` instance, but horizontal scaling would require sticky sessions or an external event broker.

**H2 is suitable for the assignment, not production.** File-based H2 demonstrates restart recovery locally, but production should use managed persistent storage and schema migrations.

## Discussion

SSE was chosen because the UI only needs one-way live updates from the session service during automated simulation. It is simpler than WebSockets and more responsive than polling.

The gateway is used as the single frontend entry point, so the UI does not need to know internal service ports and CORS is configured in one place.

## Possible extensions

* Add **Eureka** for service discovery, so the gateway resolves services by name instead of fixed URLs.
* Add a smarter `MoveStrategy`, for example minimax instead of random moves.
* Add optimistic locking with `@Version` for stronger persistence-level concurrency control.
* Replace in-memory SSE delivery with Redis Pub/Sub, Kafka, or RabbitMQ for multi-instance session-service deployments.
* Add Flyway or Liquibase for database migrations.
