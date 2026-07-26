//核心，負責註冊和發送事件
package com.asriel.discordbridge.events;

import java.util.*;
import java.util.function.Consumer;

public class EventBus {

    private static final EventBus INSTANCE = new EventBus();
    private final Map<Class<?>, List<Consumer<GameEvent>>> listeners = new HashMap<>();

    private EventBus() {}

    public static EventBus getInstance() {
        return INSTANCE;
    }

    // 註冊監聽器
    @SuppressWarnings("unchecked")
    public <T extends GameEvent> void subscribe(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>())
                 .add((Consumer<GameEvent>) listener);
    }

    // 發送事件
    public void publish(GameEvent event) {
        List<Consumer<GameEvent>> eventListeners = listeners.get(event.getClass());
        if (eventListeners != null) {
            for (Consumer<GameEvent> listener : eventListeners) {
                listener.accept(event);
            }
        }
    }
}