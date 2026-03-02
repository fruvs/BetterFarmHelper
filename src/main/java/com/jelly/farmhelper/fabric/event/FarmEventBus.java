package com.jelly.farmhelper.fabric.event;

import com.jelly.farmhelper.fabric.FarmHelperFabric;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class FarmEventBus {
    private final Map<Class<?>, CopyOnWriteArrayList<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

    public <T> AutoCloseable subscribe(Class<T> eventType, Consumer<T> listener) {
        if (eventType == null || listener == null) {
            return () -> { };
        }
        CopyOnWriteArrayList<Consumer<Object>> list = listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
        Consumer<Object> wrapped = event -> listener.accept(eventType.cast(event));
        list.add(wrapped);
        return () -> {
            CopyOnWriteArrayList<Consumer<Object>> active = listeners.get(eventType);
            if (active != null) {
                active.remove(wrapped);
            }
        };
    }

    public void post(Object event) {
        if (event == null) {
            return;
        }
        Class<?> eventClass = event.getClass();
        for (Map.Entry<Class<?>, CopyOnWriteArrayList<Consumer<Object>>> entry : listeners.entrySet()) {
            if (!entry.getKey().isAssignableFrom(eventClass)) {
                continue;
            }
            for (Consumer<Object> listener : entry.getValue()) {
                try {
                    listener.accept(event);
                } catch (Throwable t) {
                    FarmHelperFabric.LOGGER.warn("Unhandled exception in event listener for {}", entry.getKey().getSimpleName(), t);
                }
            }
        }
    }
}
