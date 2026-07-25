package com.asriel.discordbridge;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.entity.PlayerDeathEvent;
import java.util.*;
import java.util.Random;
import java.util.Set;

public class DungeonManager implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, DungeonSession> activeSessions = new HashMap<>();
    private final Set<Long> usedChunks = new HashSet<>();
    private DungeonMenu dungeonMenu;

    public void setDungeonMenu(DungeonMenu dungeonMenu) {
        this.dungeonMenu = dungeonMenu;
    }

    // ==============================
    // 副本等級設定（數值調整區）
    // ==============================
    private static final Map<Integer, DungeonConfig> DUNGEON_CONFIGS = new LinkedHashMap<>();
    static {
        // 格式：等級, new DungeonConfig(怪物種類, 怪物數量, 怪物血量倍率, 怪物攻擊倍率)
        DUNGEON_CONFIGS.put(1, new DungeonConfig(EntityType.ZOMBIE,    5,  1.0, 1.0));
        DUNGEON_CONFIGS.put(2, new DungeonConfig(EntityType.SKELETON,  6,  1.2, 1.2));
        DUNGEON_CONFIGS.put(3, new DungeonConfig(EntityType.SPIDER,    8,  1.5, 1.3));
        DUNGEON_CONFIGS.put(4, new DungeonConfig(EntityType.CREEPER,   5,  2.0, 1.5));
        DUNGEON_CONFIGS.put(5, new DungeonConfig(EntityType.WITCH,     4,  2.5, 2.0));
    }
    // ==============================

    // ==============================
    // Chunk 分配設定（可調整）
    // ==============================
    private static final int DUNGEON_BASE_X = 100000; // 副本起始 X 座標
    private static final int DUNGEON_BASE_Z = 0;      // 副本起始 Z 座標
    private static final int CHUNK_SPACING  = 2;      // 每個副本之間的 chunk 間距
    // ==============================

    // ==============================
    // 屏障設定（可調整）
    // ==============================
    private static final int BARRIER_MIN_Y = -64;  // 屏障最低高度（配合 1.18+ 地圖下限）
    private static final int BARRIER_MAX_Y = 319;  // 屏障最高高度
    private static final int PLAYER_SPAWN_Y = 319; // 玩家傳送高度
    private static final int INVINCIBLE_SECONDS = 10; // 無敵秒數
    // ==============================

    public DungeonManager(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void startDungeon(Player player, int level) {
        if (activeSessions.containsKey(player.getUniqueId())) {
            player.sendMessage("§c你已經在副本中了！");
            return;
        }

        DungeonConfig config = DUNGEON_CONFIGS.get(level);
        if (config == null) {
            player.sendMessage("§c副本等級不存在。");
            return;
        }

        World world = Bukkit.getWorlds().get(0); // 使用主世界

        // ==============================
        // 副本 Chunk 範圍設定（可調整）
        // ==============================
        Random random = new Random();
        int playerChunkX = player.getLocation().getBlockX() >> 4;
        int playerChunkZ = player.getLocation().getBlockZ() >> 4;
        int chunkX = playerChunkX + random.nextInt(33) - 16; // -16 到 +16 個 chunk
        int chunkZ = playerChunkZ + random.nextInt(33) - 16;
        // ==============================

        // 強制載入 chunk 讓 MC 自然生成地形
        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        world.loadChunk(chunk);

        int blockX = chunkX * 16 + 8; // chunk 中心 X
        int blockZ = chunkZ * 16 + 8; // chunk 中心 Z

        // 傳送玩家到 319 格高
        Location spawnLoc = new Location(world, blockX, PLAYER_SPAWN_Y, blockZ);
        player.teleport(spawnLoc);

        // 給予無敵效果
        player.addPotionEffect(new PotionEffect(
            PotionEffectType.DAMAGE_RESISTANCE,
            INVINCIBLE_SECONDS * 20, // 秒數轉 tick
            4,     // 等級 5（0-indexed），等同完全無敵
            false,
            true
        ));
        player.sendMessage("§b你有 " + INVINCIBLE_SECONDS + " 秒的無敵保護！");

        Bukkit.getScheduler().runTask(plugin, () -> {
            // 先建立 session 但怪物清單為空
            DungeonSession session = new DungeonSession(
                player.getUniqueId(), level, new ArrayList<>(),
                player.getLocation(), chunkX, chunkZ
            );
            activeSessions.put(player.getUniqueId(), session);

            placeBarriers(world, chunkX, chunkZ, session);

            player.sendMessage("§a已進入第 " + level + " 關副本！");
            player.sendMessage("§b怪物將在 " + INVINCIBLE_SECONDS + " 秒後生成...");

            // 等無敵結束後生成怪物，並用玩家當時的 Y 座標
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!activeSessions.containsKey(player.getUniqueId())) return; // 玩家已離開副本

                int playerY = player.getLocation().getBlockY();
                List<UUID> mobUUIDs = spawnMobs(world, config, level, blockX, playerY, blockZ);
                session.mobUUIDs.addAll(mobUUIDs);
                player.sendMessage("§e剩餘怪物：§f" + mobUUIDs.size());
            }, INVINCIBLE_SECONDS * 20L); // 等無敵秒數結束
        });
    }

    private void placeBarriers(World world, int chunkX, int chunkZ, DungeonSession session) {
        int minX = chunkX * 16;
        int maxX = minX + 15;
        int minZ = chunkZ * 16;
        int maxZ = minZ + 15;

        for (int y = BARRIER_MIN_Y; y <= BARRIER_MAX_Y; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                recordAndPlace(world, minX, y, z, session);
                recordAndPlace(world, maxX, y, z, session);
            }
            for (int x = minX; x <= maxX; x++) {
                recordAndPlace(world, x, y, minZ, session);
                recordAndPlace(world, x, y, maxZ, session);
            }
        }
    }

    private void recordAndPlace(World world, int x, int y, int z, DungeonSession session) {
        Location loc = new Location(world, x, y, z);
        session.originalBlocks.put(loc, world.getBlockAt(loc).getType());
        world.getBlockAt(loc).setType(Material.BARRIER);
    }

    private List<UUID> spawnMobs(World world, DungeonConfig config, int level, int centerX, int playerY, int centerZ) {
        List<UUID> mobUUIDs = new ArrayList<>();
        for (int i = 0; i < config.mobCount; i++) {

            // ==============================
            // 怪物生成位置偏移（可調整範圍）
            // ==============================
            double offsetX = (Math.random() - 0.5) * 10;
            double offsetZ = (Math.random() - 0.5) * 10;
            // ==============================

            // 找到地表高度生成怪物
            Location mobLoc = new Location(world, centerX + offsetX, playerY, centerZ + offsetZ);
            LivingEntity mob = (LivingEntity) world.spawnEntity(mobLoc, config.entityType);

            // ==============================
            // 套用血量和攻擊倍率
            // ==============================
            double maxHp = mob.getMaxHealth() * config.healthMultiplier;
            mob.setMaxHealth(maxHp);
            mob.setHealth(maxHp);
            var attackAttr = mob.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE);
            if (attackAttr != null) {
                attackAttr.setBaseValue(attackAttr.getBaseValue() * config.attackMultiplier);
            }
            // ==============================

            mob.setCustomName("§c[Lv." + level + "] " + mob.getType().name());
            plugin.getLogger().info("生成怪物: " + config.entityType + " 在 " + mobLoc);
            mob.setCustomNameVisible(true);
            mob.setGlowing(true);
            mobUUIDs.add(mob.getUniqueId());
        }
        return mobUUIDs;
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        UUID mobId = event.getEntity().getUniqueId();

        for (Map.Entry<UUID, DungeonSession> entry : activeSessions.entrySet()) {
            DungeonSession session = entry.getValue();
            if (session.mobUUIDs.contains(mobId)) {
                session.mobUUIDs.remove(mobId);
                plugin.getLogger().info("副本怪物死亡，剩餘: " + session.mobUUIDs.size());

                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    if (session.mobUUIDs.isEmpty()) {
                        completeDungeon(player, session);
                    } else {
                        player.sendMessage("§e剩餘怪物：§f" + session.mobUUIDs.size());
                    }
                }
                break;
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (activeSessions.containsKey(player.getUniqueId())) {
            DungeonSession session = activeSessions.get(player.getUniqueId());
            activeSessions.remove(player.getUniqueId());


            player.sendMessage("§c你在副本中死亡，副本已結束。");

            // 先清除 barrier 再傳送玩家
            Bukkit.getScheduler().runTask(plugin, () -> {
                clearBarriers(Bukkit.getWorlds().get(0), session);
                player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            });
        }
    }

    private void completeDungeon(Player player, DungeonSession session) {
        activeSessions.remove(player.getUniqueId());
        long chunkKey = ((long) session.chunkX << 32) | (session.chunkZ & 0xFFFFFFFFL);
        usedChunks.remove(chunkKey);

        player.sendMessage("§a§l副本通關！");

        // ==============================
        // 通關獎勵（之後在這裡新增）
        // ==============================
        if (dungeonMenu != null) {
            dungeonMenu.unlockNextLevel(player, session.level);
        }
        // ==============================

        // 先清除 barrier 再傳送玩家
        Bukkit.getScheduler().runTask(plugin, () -> {
            clearBarriers(Bukkit.getWorlds().get(0), session);
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        });
    }

    private void clearBarriers(World world, DungeonSession session) {
        for (Map.Entry<Location, Material> entry : session.originalBlocks.entrySet()) {
            world.getBlockAt(entry.getKey()).setType(entry.getValue());
        }
    }

    public boolean isInDungeon(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    // 副本設定資料類別
    static class DungeonConfig {
        EntityType entityType;
        int mobCount;
        double healthMultiplier;
        double attackMultiplier;

        DungeonConfig(EntityType entityType, int mobCount, double healthMultiplier, double attackMultiplier) {
            this.entityType = entityType;
            this.mobCount = mobCount;
            this.healthMultiplier = healthMultiplier;
            this.attackMultiplier = attackMultiplier;
        }
    }

    // 副本進行中的狀態
    static class DungeonSession {
        UUID playerUUID;
        int level;
        List<UUID> mobUUIDs;
        Location origin;
        int chunkX;
        int chunkZ;
        Map<Location, Material> originalBlocks = new HashMap<>();
        
        DungeonSession(UUID playerUUID, int level, List<UUID> mobUUIDs, Location origin, int chunkX, int chunkZ) {
            this.playerUUID = playerUUID;
            this.level = level;
            this.mobUUIDs = new ArrayList<>(mobUUIDs);
            this.origin = origin;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }
}