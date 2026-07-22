package com.asriel.discordbridge;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class DungeonMenu implements Listener {

    private final JavaPlugin plugin;
    private final DungeonManager dungeonManager;

    // ==============================
    // 副本名稱設定（可調整）
    // ==============================
    private static final List<String> DUNGEON_NAMES = Arrays.asList(
        "§6Dungeon 1 §7- 殭屍之巢",   // 連接到第 1 關
        "§6Dungeon 2",                 // 尚未開放
        "§6Dungeon 3",
        "§6Dungeon 4",
        "§6Dungeon 5",
        "§6Dungeon 6",
        "§6Dungeon 7",
        "§6Dungeon 8",
        "§6Dungeon 9",
        "§6Dungeon 10"
    );
    // ==============================

    private static final String MENU_TITLE = "§8副本選單";

    public DungeonMenu(JavaPlugin plugin, DungeonManager dungeonManager) {
        this.plugin = plugin;
        this.dungeonManager = dungeonManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openMenu(Player player) {
        int unlockedLevel = getUnlockedLevel(player); // 玩家目前解鎖到第幾關
        Inventory inv = Bukkit.createInventory(null, 27, MENU_TITLE);

        for (int i = 0; i < 10; i++) {
            int dungeonLevel = i + 1;
            ItemStack item = new ItemStack(Material.BOOK);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(DUNGEON_NAMES.get(i));

            List<String> lore = new ArrayList<>();
            if (dungeonLevel <= unlockedLevel) {
                lore.add("§a已解鎖");
                lore.add("§7點擊進入副本");
            } else if (dungeonLevel == unlockedLevel + 1) {
                lore.add("§c尚未解鎖");
                lore.add("§7通關 Dungeon " + unlockedLevel + " 後解鎖");
            } else {
                lore.add("§c尚未解鎖");
                item.setType(Material.GRAY_DYE); // 鎖定的副本用灰色染料表示
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(MENU_TITLE)) return;
        event.setCancelled(true); // 防止拿走物品

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        int slot = event.getSlot();
        if (slot < 0 || slot >= 10) return;

        int dungeonLevel = slot + 1;
        int unlockedLevel = getUnlockedLevel(player);

        if (dungeonLevel > unlockedLevel) {
            player.sendMessage("§c你還沒有解鎖這個副本！");
            return;
        }

        player.closeInventory();

        // ==============================
        // 副本入口對應（之後逐一連接）
        // ==============================
        switch (dungeonLevel) {
            case 1 -> dungeonManager.startDungeon(player, 1); // Dungeon 1 → 第 1 關
            case 2 -> player.sendMessage("§c此副本尚未開放。");
            case 3 -> player.sendMessage("§c此副本尚未開放。");
            case 4 -> player.sendMessage("§c此副本尚未開放。");
            case 5 -> player.sendMessage("§c此副本尚未開放。");
            case 6 -> player.sendMessage("§c此副本尚未開放。");
            case 7 -> player.sendMessage("§c此副本尚未開放。");
            case 8 -> player.sendMessage("§c此副本尚未開放。");
            case 9 -> player.sendMessage("§c此副本尚未開放。");
            case 10 -> player.sendMessage("§c此副本尚未開放。");
        }
        // ==============================
    }

    private int getUnlockedLevel(Player player) {
        // 從玩家資料讀取解鎖進度
        // 預設第一關已解鎖
        return plugin.getConfig().getInt("dungeon-progress." + player.getUniqueId(), 1);
    }

    public void unlockNextLevel(Player player, int completedLevel) {
        int current = getUnlockedLevel(player);
        if (completedLevel >= current) {
            plugin.getConfig().set("dungeon-progress." + player.getUniqueId(), completedLevel + 1);
            plugin.saveConfig();
            player.sendMessage("§a§lDungeon " + (completedLevel + 1) + " 已解鎖！");
        }
    }
}