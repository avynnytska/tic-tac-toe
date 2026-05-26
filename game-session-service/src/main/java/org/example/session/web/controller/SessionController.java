package org.example.session.web.controller;

import lombok.RequiredArgsConstructor;
import org.example.session.domain.Session;
import org.example.session.mapper.SessionMapper;
import org.example.session.service.SessionService;
import org.example.session.sse.SessionEventBroker;
import org.example.session.web.dto.SessionResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService service;
    private final SessionEventBroker broker;
    private final SessionMapper mapper;

    @PostMapping
    public ResponseEntity<SessionResponse> create() {
        Session session = service.createSession();
        return ResponseEntity
                .created(URI.create("/sessions/" + session.getId()))
                .body(mapper.toResponse(session));
    }

    @PostMapping("/{sessionId}/simulate")
    public ResponseEntity<SessionResponse> simulate(@PathVariable UUID sessionId) {
        Session s = service.simulate(sessionId);
        return ResponseEntity.ok(mapper.toResponse(s));
    }

    @PostMapping("/{sessionId}/simulate-async")
    public ResponseEntity<Void> simulateAsync(@PathVariable UUID sessionId) {
        service.simulateAsync(sessionId);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionResponse> get(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(mapper.toResponse(service.getSession(sessionId)));
    }

    @GetMapping(value = "/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID sessionId) {
        service.getSession(sessionId); // 404 if missing
        return broker.subscribe(sessionId);
    }
}
