package com.diploma.mrt.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * In-memory fan-out of per-case pipeline events to subscribed SSE clients.
 *
 * Events are published from the single stage choke point (AuditService.log),
 * so the live timeline reflects real backend transitions whether or not the
 * audit database is enabled. Emitters are cleaned up on completion/timeout/
 * error and dead emitters are dropped on the next publish.
 */
@Component
public class CaseEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(CaseEventPublisher.class);
    private static final long TIMEOUT_MS = 10 * 60 * 1000L;

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emittersByCase = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long caseId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emittersByCase.computeIfAbsent(caseId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(caseId, emitter));
        emitter.onTimeout(() -> remove(caseId, emitter));
        emitter.onError(throwable -> remove(caseId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("caseId", caseId)));
        } catch (IOException exception) {
            remove(caseId, emitter);
        }
        return emitter;
    }

    public void publish(Long caseId, String eventName, Object payload) {
        List<SseEmitter> emitters = emittersByCase.get(caseId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (Exception exception) {
                remove(caseId, emitter);
                log.debug("Dropped dead SSE emitter for caseId={}: {}", caseId, exception.toString());
            }
        }
    }

    private void remove(Long caseId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByCase.get(caseId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emittersByCase.remove(caseId, emitters);
            }
        }
    }
}
