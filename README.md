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
./gradlew :api-gateway:test                # ProxyControllerIT
```

Highlights:
* `GameEngineConcurrencyTest` — 32 threads racing on the same cell; exactly one wins.
* `GamePersistenceTest` / `SessionPersistenceTest` — simulate a restart by building a fresh service against the same H2 and asserting the prior game/session is recovered.
* `FullSimulationIT` — boots both engine and session Spring contexts on random ports and drives a full game over HTTP.
* `ProxyControllerIT` — boots the gateway with stubbed upstream services and verifies the routing.

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
│       ├── ApiGatewayApplication, GatewayProperties
│       ├── ProxyController     /api/engine/**, /api/sessions/**, SSE
│       └── CorsConfig
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

At the time of implementation, Spring Cloud Gateway was not used because of Spring Boot 4 compatibility concerns. This project therefore implements the gateway as a small **Spring MVC reverse proxy** (`ProxyController`) — it forwards REST calls with `RestClient` and streams SSE by piping the upstream connection into an `SseEmitter`. CORS is handled at the gateway, so individual services don't need their own CORS config.

When Spring Cloud Gateway becomes SB4-compatible, the proxy controller can be replaced with declarative routes in `application.yml`:

```yaml
spring:
  cloud:
    gateway:
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

SSE end-to-end: `SessionService.simulate` publishes `state`, `move`, and `finished` events through `SessionEventBroker`. The gateway proxies these by streaming line-by-line from the upstream connection into a new `SseEmitter`. The UI subscribes with `EventSource` and renders each move as it arrives.

### Move pacing

`session.move-delay-ms` (default `1500` in production, `0` in tests) inserts a short pause between moves so the UI animation reads naturally. Tests override to `0` to keep them fast.

## Technical Debt / Trade-offs

**Write-through persistence can diverge from memory.** The services keep active state in memory and write every mutation to H2. If a mutation succeeds in memory but the following database write fails, the two states can diverge until restart or manual recovery. A production version should either make the database the primary source of truth with transactions and optimistic locking, or add retry/rollback logic around failed writes.

**SSE subscriptions are in-memory.** `SessionEventBroker` keeps active `SseEmitter` connections in the session-service process. This is fine for a single instance, but multiple session-service instances would need sticky sessions or an external pub/sub layer so simulation events reach the same instance that owns the browser stream.

**The gateway is a custom MVC proxy.** Spring Cloud Gateway was not used because of Spring Boot 4 compatibility concerns at the time this project was built. The custom proxy keeps the assignment runnable, but a production setup should prefer a standard gateway once the dependency stack is compatible.

**H2 is file-based for local recovery.** Runtime DB files are created under each service's `data/` directory. This is useful for demonstrating restart recovery, but production deployments should use managed persistent storage and migration tooling.

## Discussion

SSE was chosen for real-time updates because the game is automated and the browser only needs one-way server-to-client events. This is simpler than WebSockets while still satisfying the optional real-time update requirement.

Alternative approaches:

* **Polling**: simpler, but less real-time and creates repeated `GET /sessions/{id}` traffic.
* **WebSockets**: useful for bidirectional interactive gameplay, but unnecessary for an automated simulation where the UI only listens.
* **External pub/sub**: Redis Pub/Sub, Kafka, or RabbitMQ would support multiple session-service instances, but would add infrastructure beyond the assignment scope.

## Possible extensions

* Replace the proxy controller with **Spring Cloud Gateway** once it supports Spring Boot 4.
* Add **Eureka** for service discovery; the gateway would resolve services by name instead of hard-coded URLs.
* Smarter `MoveStrategy` (minimax) instead of random.
* Add **optimistic locking** (`@Version`) to the JPA entities for full transactional concurrency control across multiple service instances.
