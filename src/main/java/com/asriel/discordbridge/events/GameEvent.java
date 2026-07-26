//所有事件的基底類別
package com.asriel.discordbridge.events;

public abstract class GameEvent {
    private final long timestamp;

    public GameEvent() {
        this.timestamp = System.currentTimeMillis();
    }

    public long getTimestamp() {
        return timestamp;
    }
}