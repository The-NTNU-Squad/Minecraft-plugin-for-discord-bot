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

public class DungeonStatsMenu implements Listener {

    private final JavaPlugin plugin;
    private final DungeonStatsCache statsCache;
    private static final String MENU_TITLE = "§8副本紀錄";

    public DungeonStatsMenu(JavaPlugin plugin, DungeonStatsCache statsCache) {
        this.plugin = plugin;
        this.statsCache = statsCache;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, MENU_TITLE);
        Map<Integer, DungeonStatsCache.LevelStats> stats = statsCache.get(player.getUniqueId());

        for (int i = 0; i < 10; i++) {
            int level = i + 1;
            ItemStack item = new ItemStack(Material.BOOK);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§6Dungeon " + level);

            DungeonStatsCache.LevelStats levelStats = stats.get(level);
            List<String> lore = new ArrayList<>();
            if (levelStats != null && levelStats.playCount > 0) {
                lore.add("§7遊玩次數：§f" + levelStats.playCount);
                lore.add("§7平均花費時間：§f" + formatTime(levelStats.avgClearTimeMs));
            } else {
                lore.add("§7尚未挑戰過");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        // 返回按鈕
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§c返回");
        back.setItemMeta(backMeta);
        inv.setItem(26, back);

        player.openInventory(inv);
    }

    private String formatTime(long ms) {
        if (ms <= 0) return "無資料";
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes + "分" + seconds + "秒";
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(MENU_TITLE)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (event.getSlot() == 26 && plugin instanceof DiscordBridgePlugin) {
            player.closeInventory();
            ((DiscordBridgePlugin) plugin).getDungeonMenu().openMenu(player);
        }
    }
}