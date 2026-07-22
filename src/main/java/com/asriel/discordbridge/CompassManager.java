package com.asriel.discordbridge;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;

public class CompassManager implements Listener {

    private final JavaPlugin plugin;
    private final DungeonMenu dungeonMenu;
    private final NamespacedKey COMPASS_KEY;

    public CompassManager(JavaPlugin plugin, DungeonMenu dungeonMenu) {
        this.plugin = plugin;
        this.dungeonMenu = dungeonMenu;
        this.COMPASS_KEY = new NamespacedKey(plugin, "dungeon_compass");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // 建立副本指南針
    public ItemStack createCompass() {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        meta.setDisplayName("§b§l副本指南針");
        meta.setLore(Arrays.asList(
            "§7右鍵開啟副本選單",
            "§8無法丟棄或存入箱子"
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        // 加上特殊標記識別這是副本指南針
        meta.getPersistentDataContainer().set(COMPASS_KEY, PersistentDataType.BYTE, (byte) 1);
        compass.setItemMeta(meta);
        return compass;
    }

    // 給予玩家指南針
    public void giveCompass(Player player) {
        // 檢查是否已有指南針
        for (ItemStack item : player.getInventory().getContents()) {
            if (isDungeonCompass(item)) {
                player.sendMessage("§c你已經有副本指南針了！");
                return;
            }
        }
        player.getInventory().addItem(createCompass());
        player.sendMessage("§a已獲得副本指南針！");
    }

    // 判斷是否為副本指南針
    private boolean isDungeonCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
            .has(COMPASS_KEY, PersistentDataType.BYTE);
    }

    // 右鍵觸發開啟選單
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
            event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (!isDungeonCompass(item)) return;

        event.setCancelled(true);
        dungeonMenu.openMenu(event.getPlayer());
    }

    // 防止丟出指南針
    @EventHandler
    public void onPlayerDrop(PlayerDropItemEvent event) {
        if (isDungeonCompass(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c副本指南針無法丟棄！");
        }
    }

    // 防止放進箱子
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        if (isDungeonCompass(cursor) || isDungeonCompass(current)) {
            // 如果點擊發生在非玩家背包的介面就取消
            if (event.getView().getTopInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
                event.setCancelled(true);
            }
        }
    }
}