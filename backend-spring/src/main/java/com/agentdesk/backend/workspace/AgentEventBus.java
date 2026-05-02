package com.agentdesk.backend.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AgentEventBus {

    private static final long SSE_TIMEOUT_MS = 30_000L;
    private static final int MAX_EVENTS_PER_THREAD = 200;

    private final ObjectMapper objectMapper;
    private final AtomicLong sequence = new AtomicLong();
    private final Map<UUID, List<AgentEvent>> eventsByThread = new LinkedHashMap<>();
    private final Map<UUID, List<SseEmitter>> emittersByThread = new LinkedHashMap<>();

    public AgentEventBus(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public synchronized AgentEvent publish(UUID threadId, String type, Object data) {
        AgentEvent event = new AgentEvent(
                sequence.incrementAndGet(),
                threadId,
                type,
                objectMapper.valueToTree(data),
                Instant.now()
        );
        List<AgentEvent> events = eventsByThread.computeIfAbsent(threadId, ignored -> new ArrayList<>());
        events.add(event);
        if (events.size() > MAX_EVENTS_PER_THREAD) {
            events.remove(0);
        }
        for (SseEmitter emitter : List.copyOf(emittersByThread.getOrDefault(threadId, List.of()))) {
            try {
                send(emitter, event);
            } catch (IOException exception) {
                removeEmitter(threadId, emitter);
            }
        }
        return event;
    }

    public synchronized SseEmitter subscribe(UUID threadId, Long lastEventId, boolean replayOnly) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emittersByThread.computeIfAbsent(threadId, ignored -> new ArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(threadId, emitter));
        emitter.onTimeout(() -> removeEmitter(threadId, emitter));
        try {
            emitter.send(SseEmitter.event().name("ready").data(Map.of("thread_id", threadId.toString())));
            long cursor = lastEventId == null ? 0L : lastEventId;
            for (AgentEvent event : eventsByThread.getOrDefault(threadId, List.of())) {
                if (event.id() > cursor) {
                    send(emitter, event);
                }
            }
            if (replayOnly) {
                emitter.complete();
            }
        } catch (IOException exception) {
            removeEmitter(threadId, emitter);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    private void send(SseEmitter emitter, AgentEvent event) throws IOException {
        emitter.send(SseEmitter.event()
                .id(Long.toString(event.id()))
                .name(event.type())
                .data(event));
    }

    private synchronized void removeEmitter(UUID threadId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByThread.get(threadId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emittersByThread.remove(threadId);
            }
        }
    }
}
