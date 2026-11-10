package dev.hidly.nubi.notification.service;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// 연결을 서버 메모리에 보관, 서버를 여러 대로 늘리면 Redis pub/sub으로 다른 서버에 전달
@Component
public class NotificationEmitters {
    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter connect(Long organizationId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        emitters.computeIfAbsent(organizationId, id -> ConcurrentHashMap.newKeySet()).add(emitter);

        Runnable remove = () -> emitters.computeIfPresent(organizationId, (id, set) -> {
            set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());

        send(emitter, "connect", "connected");
        return emitter;
    }

    public void send(Long organizationId, Object data) {
        emitters.getOrDefault(organizationId, Set.of()).forEach(emitter -> send(emitter, "notification", data));
    }

    private void send(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);
        }
    }
}
