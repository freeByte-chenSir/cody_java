package com.cody.sdk.events;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Event dispatch for SDK consumers.
 *
 *
 * Supports registering typed event listeners and broadcasting events.
 */
public class EventManager {

    private final List<Consumer<String>> textListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> errorListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> doneListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> rawListeners = new CopyOnWriteArrayList<>();

    public void onText(Consumer<String> listener) { textListeners.add(listener); }
    public void onError(Consumer<String> listener) { errorListeners.add(listener); }
    public void onDone(Runnable listener) { doneListeners.add(listener); }
    public void onRaw(Consumer<String> listener) { rawListeners.add(listener); }

    public void emitText(String text) { for (var l : textListeners) l.accept(text); }
    public void emitError(String error) { for (var l : errorListeners) l.accept(error); }
    public void emitDone() { for (var l : doneListeners) l.run(); }
    public void emitRaw(String raw) { for (var l : rawListeners) l.accept(raw); }

    public void clear() {
        textListeners.clear();
        errorListeners.clear();
        doneListeners.clear();
        rawListeners.clear();
    }
}
