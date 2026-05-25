package org.example.session.event;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SessionEventBroker {

    private final ConcurrentHashMap<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID sessionId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(sessionId, emitter));
        emitter.onTimeout(() -> remove(sessionId, emitter));
        emitter.onError(e -> remove(sessionId, emitter));
        try {
            emitter.send(SseEmitter.event().name("ready").data("ok"));
        } catch (IOException e) {
            remove(sessionId, emitter);
        }
        return emitter;
    }

    public void publish(UUID sessionId, String eventName, Object payload) {
        List<SseEmitter> list = emitters.get(sessionId);
        if (list == null) return;
        for (SseEmitter em : list) {
            try {
                em.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException e) {
                remove(sessionId, em);
            }
        }
    }

    public void complete(UUID sessionId) {
        List<SseEmitter> list = emitters.remove(sessionId);
        if (list == null) return;
        for (SseEmitter em : list) {
            try { em.complete(); } catch (Exception ignored) {}
        }
    }

    private void remove(UUID sessionId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(sessionId);
        if (list != null) list.remove(emitter);
    }
}
