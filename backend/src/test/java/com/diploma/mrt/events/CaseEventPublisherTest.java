package com.diploma.mrt.events;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CaseEventPublisherTest {

    private final CaseEventPublisher publisher = new CaseEventPublisher();

    @Test
    void publishWithoutSubscribersIsNoOp() {
        assertDoesNotThrow(() -> publisher.publish(1L, "stage", Map.of("action", "INFERENCE_STARTED")));
    }

    @Test
    void subscribeReturnsEmitterWithConfiguredTimeout() {
        SseEmitter emitter = publisher.subscribe(42L);
        assertNotNull(emitter);
        assertEquals(10 * 60 * 1000L, emitter.getTimeout());
    }

    @Test
    void publishToSubscribedCaseDoesNotThrow() {
        publisher.subscribe(7L);
        assertDoesNotThrow(() -> publisher.publish(7L, "stage", Map.of("action", "INFERENCE_COMPLETED")));
    }
}
