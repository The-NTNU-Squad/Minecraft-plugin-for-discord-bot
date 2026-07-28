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
import com.asriel.discordbridge.events.EventBus;
import com.asriel.discordbridge.events.DungeonStartEvent;
import com.asriel.discordbridge.events.DungeonCompleteEvent;

public class DungeonManager implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, DungeonSession> activeSessions = new HashMap<>();

    // ==============================
    // 副本等級設定（數值調整區）
    // ==============================
    private static final Map<Integer, DungeonConfig> DUNGEON_CONFIGS = new LinkedHashMap<>();
    static {
        // 格式：等級, new DungeonConfig(血量倍率, 攻擊倍率, 波間隔秒數, 第1波, 第2波, 第3波...)
        DUNGEON_CONFIGS.put(1, new DungeonConfig(1.0, 1.0, 10,
            new WaveConfig(new MobSpawn(EntityType.HUSK, 5)),
            new WaveConfig(new MobSpawn(EntityType.HUSK, 4), new MobSpawn(EntityType.SKELETON, 3)),
            new WaveConfig(new MobSpawn(EntityType.SKELETON, 6))
        ));
        DUNGEON_CONFIGS.put(2, new DungeonConfig(1.2, 1.2, 30,
            new WaveConfig(new MobSpawn(EntityType.SKELETON, 6))
        ));
        DUNGEON_CONFIGS.put(3, new DungeonConfig(1.5, 1.3, 25,
            new WaveConfig(new MobSpawn(EntityType.SPIDER, 8))
        ));
        DUNGEON_CONFIGS.put(4, new DungeonConfig(2.0, 1.5, 25,
            new WaveConfig(new MobSpawn(EntityType.CREEPER, 5))
        ));
        DUNGEON_CONFIGS.put(5, new DungeonConfig(2.5, 2.0, 25,
            new WaveConfig(new MobSpawn(EntityType.WITCH, 4))
        ));
        DUNGEON_CONFIGS.put(6, new DungeonConfig(3.0, 2.5, 20,
            new WaveConfig(new MobSpawn(EntityType.VINDICATOR, 5))
        ));
        DUNGEON_CONFIGS.put(7, new DungeonConfig(3.5, 2.8, 20,
            new WaveConfig(new MobSpawn(EntityType.PILLAGER, 6))
        ));
        DUNGEON_CONFIGS.put(8, new DungeonConfig(4.0, 3.0, 20,
            new WaveConfig(new MobSpawn(EntityType.EVOKER, 3))
        ));
        DUNGEON_CONFIGS.put(9, new DungeonConfig(5.0, 4.0, 15,
            new WaveConfig(new MobSpawn(EntityType.WITCH, 2))
        ));
        DUNGEON_CONFIGS.put(10, new DungeonConfig(6.0, 5.0, 15,
            new WaveConfig(new MobSpawn(EntityType.IRON_GOLEM, 1))
        ));
    }
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
        Location originalLocation = player.getLocation(); // 記錄原本位置

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

        EventBus.getInstance().publish(new DungeonStartEvent(player, level));

        Bukkit.getScheduler().runTask(plugin, () -> {
            DungeonSession session = new DungeonSession(
                player.getUniqueId(), level, new ArrayList<>(),
                originalLocation, chunkX, chunkZ, config.waves.size()
            );
            activeSessions.put(player.getUniqueId(), session);

            placeBarriers(world, chunkX, chunkZ, session);

            player.sendMessage("§b怪物將在 " + INVINCIBLE_SECONDS + " 秒後生成...");

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!activeSessions.containsKey(player.getUniqueId())) return;

                int playerY = player.getLocation().getBlockY();
                startWaves(player, session, world, config, level, blockX, playerY, blockZ);
            }, INVINCIBLE_SECONDS * 20L);
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
        String key = x + "," + y + "," + z;
        if (!session.originalBlocks.containsKey(key)) { // 只記錄第一次
            session.originalBlocks.put(key, world.getBlockAt(x, y, z).getType());
        }
        world.getBlockAt(x, y, z).setType(Material.BARRIER);
    }

    private List<UUID> spawnMobs(World world, WaveConfig wave, DungeonConfig config, int level,
                                int centerX, int playerY, int centerZ) {
        List<UUID> mobUUIDs = new ArrayList<>();

        for (MobSpawn mobSpawn : wave.mobs) {
            for (int i = 0; i < mobSpawn.count; i++) {

                double offsetX = (Math.random() - 0.5) * 10;
                double offsetZ = (Math.random() - 0.5) * 10;

                int mobX = centerX + (int) offsetX;
                int mobZ = centerZ + (int) offsetZ;
                int surfaceY = world.getHighestBlockYAt(mobX, mobZ);
                Location mobLoc = new Location(world, mobX, surfaceY + 1, mobZ);
                LivingEntity mob = (LivingEntity) world.spawnEntity(mobLoc, mobSpawn.entityType);

                double maxHp = mob.getMaxHealth() * config.healthMultiplier;
                mob.setMaxHealth(maxHp);
                mob.setHealth(maxHp);
                var attackAttr = mob.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE);
                if (attackAttr != null) {
                    attackAttr.setBaseValue(attackAttr.getBaseValue() * config.attackMultiplier);
                }

                mob.setCustomName("§c[Lv." + level + "] " + mob.getType().name());
                mob.setCustomNameVisible(true);
                mob.setGlowing(true);
                mobUUIDs.add(mob.getUniqueId());
            }
        }
        return mobUUIDs;
    }

    private void startWaves(Player player, DungeonSession session, World world, DungeonConfig config,
                            int level, int centerX, int centerY, int centerZ) {

        spawnWave(player, session, world, config, level, centerX, centerY, centerZ);

        if (session.maxWaves <= 1) return;

        long intervalTicks = config.waveIntervalSeconds * 20L;

        session.waveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!activeSessions.containsKey(player.getUniqueId())) {
                if (session.waveTask != null) session.waveTask.cancel();
                return;
            }

            spawnWave(player, session, world, config, level, centerX, centerY, centerZ);

            if (session.currentWave >= session.maxWaves) {
                session.waveTask.cancel();
            }
        }, intervalTicks, intervalTicks);
    }

    private void spawnWave(Player player, DungeonSession session, World world, DungeonConfig config,
                            int level, int centerX, int centerY, int centerZ) {

        WaveConfig wave = config.waves.get(session.currentWave); // currentWave 目前是「下一波的 index」（從 0 開始）
        session.currentWave++;

        List<UUID> newMobs = spawnMobs(world, wave, config, level, centerX, centerY, centerZ);
        session.mobUUIDs.addAll(newMobs);

        player.sendMessage("§c§l▶ 第 " + session.currentWave + " / " + session.maxWaves + " 波怪物出現！");
        player.sendMessage("§e目前場上怪物：§f" + session.mobUUIDs.size());
    }   

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        UUID mobId = event.getEntity().getUniqueId();

        for (Map.Entry<UUID, DungeonSession> entry : activeSessions.entrySet()) {
            DungeonSession session = entry.getValue();
            if (session.mobUUIDs.contains(mobId)) {
                session.mobUUIDs.remove(mobId);

                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    boolean allWavesSpawned = session.currentWave >= session.maxWaves;
                    if (session.mobUUIDs.isEmpty() && allWavesSpawned) {
                        completeDungeon(player, session);
                    } else if (session.mobUUIDs.isEmpty()) {
                        player.sendMessage("§e目前怪物已清空，等待下一波...");
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

            if (session.waveTask != null) {
                session.waveTask.cancel();
            }

            player.sendMessage("§c你在副本中死亡，副本已結束。");

            Bukkit.getScheduler().runTask(plugin, () -> {
                clearBarriers(Bukkit.getWorlds().get(0), session);
                player.teleport(session.origin);
            });
        }
    }

    private void completeDungeon(Player player, DungeonSession session) {
        activeSessions.remove(player.getUniqueId());
        long chunkKey = ((long) session.chunkX << 32) | (session.chunkZ & 0xFFFFFFFFL);

        if (session.waveTask != null) {
            session.waveTask.cancel();
        }

        long clearTimeMs = System.currentTimeMillis() - session.startTime;
        EventBus.getInstance().publish(new DungeonCompleteEvent(player, session.level, clearTimeMs));

        Bukkit.getScheduler().runTask(plugin, () -> {
            clearBarriers(Bukkit.getWorlds().get(0), session);
            player.teleport(session.origin);
        });
    }

    private void clearBarriers(World world, DungeonSession session) {
        plugin.getLogger().info("開始清除 barrier，共 " + session.originalBlocks.size() + " 個方塊");
        for (Map.Entry<String, Material> entry : session.originalBlocks.entrySet()) {
            String[] parts = entry.getKey().split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            world.getBlockAt(x, y, z).setType(entry.getValue());
        }
    }

    public boolean isInDungeon(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    
    // 單一種怪物的生成設定
    static class MobSpawn {
        EntityType entityType;
        int count;

        MobSpawn(EntityType entityType, int count) {
            this.entityType = entityType;
            this.count = count;
        }
    }

    // 一波裡面可以有多種怪物組合
    static class WaveConfig {
        List<MobSpawn> mobs;

        WaveConfig(MobSpawn... mobs) {
            this.mobs = Arrays.asList(mobs);
        }
    }

    // 副本設定資料類別
    static class DungeonConfig {
        double healthMultiplier;
        double attackMultiplier;
        int waveIntervalSeconds;
        List<WaveConfig> waves;   // 波數 = waves.size()

        DungeonConfig(double healthMultiplier, double attackMultiplier, int waveIntervalSeconds, WaveConfig... waves) {
            this.healthMultiplier = healthMultiplier;
            this.attackMultiplier = attackMultiplier;
            this.waveIntervalSeconds = waveIntervalSeconds;
            this.waves = Arrays.asList(waves);
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
        long startTime = System.currentTimeMillis();
        Map<String, Material> originalBlocks = new HashMap<>();

        int currentWave = 0;              // 目前已生成到第幾波
        final int maxWaves;               // 總波數
        org.bukkit.scheduler.BukkitTask waveTask;  // 波數計時器，方便中途取消

        DungeonSession(UUID playerUUID, int level, List<UUID> mobUUIDs, Location origin,
                    int chunkX, int chunkZ, int maxWaves) {
            this.playerUUID = playerUUID;
            this.level = level;
            this.mobUUIDs = new ArrayList<>(mobUUIDs);
            this.origin = origin;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.maxWaves = maxWaves;
        }
    }
}