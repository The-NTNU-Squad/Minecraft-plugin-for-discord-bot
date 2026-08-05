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
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent;

import java.util.*;

public class DungeonMenu implements Listener {

    private final JavaPlugin plugin;
    private final DungeonManager dungeonManager;
    private final PlayerWalletCache walletCache;
    private final DungeonStatsCache statsCache;
    private final DungeonStatsMenu statsMenu;

    private static final List<String> DUNGEON_NAMES = Arrays.asList(
        "§6Dungeon 1 §7- 殭屍之巢",
        "§6Dungeon 2", "§6Dungeon 3", "§6Dungeon 4", "§6Dungeon 5",
        "§6Dungeon 6", "§6Dungeon 7", "§6Dungeon 8", "§6Dungeon 9", "§6Dungeon 10"
    );

    private static final String MENU_TITLE = "§8副本選單";

    private static final int[] PENDING_ITEM_SLOTS = {
        21, 22, 23, 30, 31, 32, 39, 40, 41, 48, 49, 50
    };
    private static final int WALLET_SLOT = 28;
    private static final int WEBSITE_SLOT = 34;
    private static final int STATS_SLOT = 13;

    public DungeonMenu(JavaPlugin plugin, DungeonManager dungeonManager, PlayerWalletCache walletCache,
                        DungeonStatsCache statsCache, DungeonStatsMenu statsMenu) {
        this.plugin = plugin;
        this.dungeonManager = dungeonManager;
        this.walletCache = walletCache;
        this.statsCache = statsCache;
        this.statsMenu = statsMenu;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openMenu(Player player) {
        int unlockedLevel = getUnlockedLevel(player);
        Inventory inv = Bukkit.createInventory(null, 54, MENU_TITLE);

        // 背景填滿灰色玻璃板
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        // 上方 2 排：10 個副本關卡
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
                item.setType(Material.GRAY_DYE);
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        // 查看副本紀錄按鈕（放在填色、關卡之後都可以，位置不影響結果）
        ItemStack statsItem = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta statsMeta = statsItem.getItemMeta();
        statsMeta.setDisplayName("§e§l查看副本紀錄");
        statsMeta.setLore(Arrays.asList("§7點擊查看遊玩次數與花費時間"));
        statsItem.setItemMeta(statsMeta);
        inv.setItem(STATS_SLOT, statsItem);

        // 左區塊：金幣顯示
        PlayerWalletCache.WalletData wallet = walletCache.get(player.getUniqueId());
        ItemStack coinItem = new ItemStack(Material.EMERALD);
        ItemMeta coinMeta = coinItem.getItemMeta();
        coinMeta.setDisplayName("§a§l我的金幣");
        coinMeta.setLore(Arrays.asList(
            "§7目前金幣：§e" + wallet.coinBalance,
            "§8（登入或副本結束時會更新，非即時）"
        ));
        coinItem.setItemMeta(coinMeta);
        inv.setItem(WALLET_SLOT, coinItem);

        // 中間區塊：待領物品
        List<PlayerWalletCache.PendingItem> pendingItems = wallet.pendingItems;
        if (pendingItems.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta emptyMeta = empty.getItemMeta();
            emptyMeta.setDisplayName("§7目前沒有待領物品");
            empty.setItemMeta(emptyMeta);
            inv.setItem(PENDING_ITEM_SLOTS[PENDING_ITEM_SLOTS.length / 2], empty);
        } else {
            for (int i = 0; i < pendingItems.size() && i < PENDING_ITEM_SLOTS.length; i++) {
                PlayerWalletCache.PendingItem pending = pendingItems.get(i);
                ItemStack item = new ItemStack(Material.CHEST);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName("§e待領物品 #" + pending.deliveryId);
                meta.setLore(Arrays.asList("§7點擊領取"));
                item.setItemMeta(meta);
                inv.setItem(PENDING_ITEM_SLOTS[i], item);
            }
        }

        // 右區塊：網站連結
        ItemStack linkItem = new ItemStack(Material.MAP);
        ItemMeta linkMeta = linkItem.getItemMeta();
        linkMeta.setDisplayName("§b§l前往網站商城");
        linkMeta.setLore(Arrays.asList("§7點擊在聊天視窗取得連結"));
        linkItem.setItemMeta(linkMeta);
        inv.setItem(WEBSITE_SLOT, linkItem);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(MENU_TITLE)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        // 副本關卡
        if (slot < 10) {
            int dungeonLevel = slot + 1;
            int unlockedLevel = getUnlockedLevel(player);
            if (dungeonLevel > unlockedLevel) {
                player.sendMessage("§c你還沒有解鎖這個副本！");
                return;
            }
            player.closeInventory();
            dungeonManager.startDungeon(player, dungeonLevel);
            return;
        }

        // 查看副本紀錄（搬到這裡，slot 跟 player 都已經宣告過了）
        if (slot == STATS_SLOT) {
            player.closeInventory();
            if (plugin instanceof DiscordBridgePlugin) {
                ((DiscordBridgePlugin) plugin).refreshDungeonStats(player, () -> statsMenu.openMenu(player));
            }
            return;
        }

        // 金幣顯示：不可點擊
        if (slot == WALLET_SLOT) return;

        // 網站連結
        if (slot == WEBSITE_SLOT) {
            String websiteUrl = plugin.getConfig().getString("website-url", "https://your-website.com");
            TextComponent msg = new TextComponent("§b點擊前往網站商城：");
            TextComponent link = new TextComponent("§n" + websiteUrl);
            link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, websiteUrl));
            msg.addExtra(link);
            player.spigot().sendMessage(msg);
            return;
        }

        // 待領物品
        for (int i = 0; i < PENDING_ITEM_SLOTS.length; i++) {
            if (slot == PENDING_ITEM_SLOTS[i]) {
                PlayerWalletCache.WalletData wallet = walletCache.get(player.getUniqueId());
                if (i < wallet.pendingItems.size()) {
                    PlayerWalletCache.PendingItem pending = wallet.pendingItems.get(i);
                    if (plugin instanceof DiscordBridgePlugin) {
                        ((DiscordBridgePlugin) plugin).claimPendingItem(player, pending.deliveryId, pending.command);
                    }
                    player.closeInventory();
                }
                return;
            }
        }
    }

    private int getUnlockedLevel(Player player) {
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