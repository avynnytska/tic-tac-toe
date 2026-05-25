package org.example.engine.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.engine.domain.Game;
import org.example.engine.mapper.GameMapper;
import org.example.engine.service.GameEngineService;
import org.example.engine.web.dto.CreateGameRequest;
import org.example.engine.web.dto.GameResponse;
import org.example.engine.web.dto.MoveRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/games")
@RequiredArgsConstructor
public class GameController {

    private final GameEngineService service;
    private final GameMapper mapper;

    @PostMapping
    public ResponseEntity<GameResponse> create(@RequestBody(required = false) CreateGameRequest req) {
        UUID requestedId = req == null ? null : req.gameId();
        Game game = service.createGame(requestedId);
        return ResponseEntity
                .created(URI.create("/games/" + game.getId()))
                .body(mapper.toResponse(game));
    }

    @PostMapping("/{gameId}/move")
    public ResponseEntity<GameResponse> move(@PathVariable UUID gameId, @Valid @RequestBody MoveRequest req) {
        Game game = service.applyMove(gameId, req.player(), req.row(), req.col());
        return ResponseEntity.ok(mapper.toResponse(game));
    }

    @GetMapping("/{gameId}")
    public ResponseEntity<GameResponse> get(@PathVariable UUID gameId) {
        return ResponseEntity.ok(mapper.toResponse(service.getGame(gameId)));
    }
}
