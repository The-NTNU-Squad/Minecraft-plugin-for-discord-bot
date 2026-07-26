//副本通關事件
package com.asriel.discordbridge.events;

import org.bukkit.entity.Player;

public class DungeonCompleteEvent extends GameEvent {
    private final Player player;
    private final int level;
    private final long clearTimeMs; // 通關花了多少毫秒

    public DungeonCompleteEvent(Player player, int level, long clearTimeMs) {
        super();
        this.player = player;
        this.level = level;
        this.clearTimeMs = clearTimeMs;
    }

    public Player getPlayer() { return player; }
    public int getLevel() { return level; }
    public long getClearTimeMs() { return clearTimeMs; }
}