package com.asriel.discordbridge;

import java.util.*;

public class PlayerWalletCache {

    public static class PendingItem {
        public final int deliveryId;
        public final String command;

        public PendingItem(int deliveryId, String command) {
            this.deliveryId = deliveryId;
            this.command = command;
        }
    }

    public static class WalletData {
        public long coinBalance = 0;
        public int unlockedLevel = 1;
        public List<PendingItem> pendingItems = new ArrayList<>();
    }

    private final Map<UUID, WalletData> cache = new HashMap<>();

    public WalletData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, k -> new WalletData());
    }

    public void setCoinBalance(UUID uuid, long balance) {
        get(uuid).coinBalance = balance;
    }

    public void setPendingItems(UUID uuid, List<PendingItem> items) {
        get(uuid).pendingItems = items;
    }

    public void removePendingItem(UUID uuid, int deliveryId) {
        get(uuid).pendingItems.removeIf(i -> i.deliveryId == deliveryId);
    }
}