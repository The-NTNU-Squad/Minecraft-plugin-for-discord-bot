package com.asriel.discordbridge;

import java.util.*;

public class DungeonStatsCache {

    public static class LevelStats {
        public int playCount = 0;
        public long avgClearTimeMs = 0;
    }

    private final Map<UUID, Map<Integer, LevelStats>> cache = new HashMap<>();

    public Map<Integer, LevelStats> get(UUID uuid) {
        return cache.computeIfAbsent(uuid, k -> new HashMap<>());
    }

    public void setStats(UUID uuid, Map<Integer, LevelStats> stats) {
        cache.put(uuid, stats);
    }
}