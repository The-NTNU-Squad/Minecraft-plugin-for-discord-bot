//副本開始事件
package com.asriel.discordbridge.events;

import org.bukkit.entity.Player;

public class DungeonStartEvent extends GameEvent {
    private final Player player;
    private final int level;

    public DungeonStartEvent(Player player, int level) {
        super();
        this.player = player;
        this.level = level;
    }

    public Player getPlayer() { return player; }
    public int getLevel() { return level; }
}