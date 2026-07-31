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
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.entity.EntityTransformEvent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class DungeonManager implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, DungeonSession> activeSessions = new HashMap<>();

    // ==============================
    // 副本等級設定（數值調整區）
    // ==============================
    private static final Map<Integer, DungeonConfig> DUNGEON_CONFIGS = new LinkedHashMap<>();
    static {
        // 格式：等級, new DungeonConfig(血量倍率, 攻擊倍率, 波間隔秒數, 第1波, 第2波, 第3波...)
        DUNGEON_CONFIGS.put(1, new DungeonConfig(1.0, 1.0, 20,
            new WaveConfig(new MobSpawn(EntityType.HUSK, 5)),
            new WaveConfig(new MobSpawn(EntityType.SKELETON, 5, Material.IRON_HELMET, null, null, null, Material.BOW))
        ));
        DUNGEON_CONFIGS.put(2, new DungeonConfig(1, 1, 20,
            new WaveConfig(new MobSpawn(EntityType.HUSK, 5)),
            new WaveConfig(
                new MobSpawn(EntityType.HUSK, 4),
                new MobSpawn(EntityType.SKELETON, 3, Material.IRON_HELMET, null, null, null, Material.BOW)
            ),
            new WaveConfig(
                new MobSpawn(EntityType.HUSK, 4),
                new MobSpawn(EntityType.SKELETON, 3, Material.IRON_HELMET, null, null, null, Material.BOW),
                new MobSpawn(EntityType.SPIDER, 4)
            )
        ));
        DUNGEON_CONFIGS.put(3, new DungeonConfig(1, 1, 20,
            new WaveConfig(
                new MobSpawn(EntityType.DROWNED, 3, Material.IRON_HELMET, Material.IRON_CHESTPLATE,
                             Material.IRON_LEGGINGS, Material.IRON_BOOTS, Material.STONE_SWORD),
                new MobSpawn(EntityType.WITCH, 3)
            ),
            new WaveConfig(
                new MobSpawn(EntityType.GUARDIAN, 4)
            ),
            new WaveConfig(
                new MobSpawn(EntityType.ELDER_GUARDIAN, 1)
            )

        ));
        DUNGEON_CONFIGS.put(4, new DungeonConfig(1, 1, 30,
            new WaveConfig(
                new MobSpawn(EntityType.PILLAGER, 5),
                new MobSpawn(EntityType.VINDICATOR, 3)
            ),
            new WaveConfig(
                new MobSpawn(EntityType.VINDICATOR, 5),
                new MobSpawn(EntityType.EVOKER, 3)
            ),
            new WaveConfig(
                new MobSpawn(EntityType.RAVAGER, 2),
                new MobSpawn(EntityType.EVOKER, 3)
            ),
            new WaveConfig(
                new MobSpawn(EntityType.RAVAGER, 4)
            )
        ));
        DUNGEON_CONFIGS.put(5, new DungeonConfig(1, 1, 60,
            new WaveConfig(new MobSpawn(EntityType.WITHER, 1)),
            new WaveConfig(new MobSpawn(EntityType.WITHER, 1))
        ));
    }

    // ==============================
    // 副本金幣獎勵設定（可調整）
    // ==============================
    private static final Map<Integer, Integer> COIN_REWARDS = new HashMap<>();
    static {
        COIN_REWARDS.put(1, 5);
        COIN_REWARDS.put(2, 10);
        COIN_REWARDS.put(3, 20);
        COIN_REWARDS.put(4, 40);
        COIN_REWARDS.put(5, 50);
        // 6~10 關自行補上
    }
// ============================== 
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


                // ==============================
                // 套用裝備
                // ==============================
                EntityEquipment equipment = mob.getEquipment();
                if (equipment != null) {
                    if (mobSpawn.helmet != null) equipment.setHelmet(new ItemStack(mobSpawn.helmet));
                    if (mobSpawn.chestplate != null) equipment.setChestplate(new ItemStack(mobSpawn.chestplate));
                    if (mobSpawn.leggings != null) equipment.setLeggings(new ItemStack(mobSpawn.leggings));
                    if (mobSpawn.boots != null) equipment.setBoots(new ItemStack(mobSpawn.boots));
                    if (mobSpawn.mainHand != null) equipment.setItemInMainHand(new ItemStack(mobSpawn.mainHand));

                    // 防止裝備掉落（可選，不想要這行就刪掉）
                    equipment.setHelmetDropChance(0f);
                    equipment.setChestplateDropChance(0f);
                    equipment.setLeggingsDropChance(0f);
                    equipment.setBootsDropChance(0f);
                    equipment.setItemInMainHandDropChance(0f);
                }
                // ==============================

                mob.setCustomName("§c[Lv." + level + "] " + mob.getType().name());          
                mob.setGlowing(true);
                mobUUIDs.add(mob.getUniqueId());
            }
        }
        return mobUUIDs;
    }

    private void startWaves(Player player, DungeonSession session, World world, DungeonConfig config,
                            int level, int centerX, int centerY, int centerZ) {

        // 把生怪需要的上下文存起來，之後 onMobDeath 提早觸發時才拿得到
        session.world = world;
        session.config = config;
        session.centerX = centerX;
        session.centerY = centerY;
        session.centerZ = centerZ;

        spawnWave(player, session); // 第一波立即生成

        scheduleWave(player, session); // 排程下一波的計時器（若還有剩餘波數）
    }

    // 排定「下一波」的計時器；如果所有波數都生完了就不排
    private void scheduleWave(Player player, DungeonSession session) {
        if (session.currentWave >= session.maxWaves) return; // 已經是最後一波，不用再排

        long intervalTicks = session.config.waveIntervalSeconds * 20L;

        session.waveTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!activeSessions.containsKey(player.getUniqueId())) return;

            spawnWave(player, session);
            scheduleWave(player, session); // 生完後再排下一次（如果還有剩）
        }, intervalTicks);
    }

    private void spawnWave(Player player, DungeonSession session) {
        WaveConfig wave = session.config.waves.get(session.currentWave);
        session.currentWave++;

        List<UUID> newMobs = spawnMobs(session.world, wave, session.config, session.level,
                                        session.centerX, session.centerY, session.centerZ);
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
                        // 最後一波也清空了，過關
                        completeDungeon(player, session);
                    } else if (session.mobUUIDs.isEmpty()) {
                        // 場上清空但還有波數沒生，提早觸發下一波
                        if (session.waveTask != null) {
                            session.waveTask.cancel();
                        }
                        player.sendMessage("§a怪物已清空，提前召喚下一波！");
                        spawnWave(player, session);
                        scheduleWave(player, session);
                    } else {
                        player.sendMessage("§e剩餘怪物：§f" + session.mobUUIDs.size());
                    }
                }
                break;
            }
        }
    }

    @EventHandler
    public void onEntityTransform(EntityTransformEvent event) {
        if (!(event.getTransformedEntity() instanceof LivingEntity newMob)) return;

        UUID oldId = event.getEntity().getUniqueId();
        UUID newId = newMob.getUniqueId();

        for (DungeonSession session : activeSessions.values()) {
            if (session.mobUUIDs.contains(oldId)) {
                session.mobUUIDs.remove(oldId);
                session.mobUUIDs.add(newId);

                // 變形後重新套用副本標記，避免變成沒有標記的野生殭屍
                newMob.setCustomName("§c[Lv." + session.level + "] " + newMob.getType().name());
                newMob.setCustomNameVisible(true);
                newMob.setGlowing(true);

                // 建議：重新套用血量倍率，因為新生成的實體是用預設血量
                if (session.config != null) {
                    double maxHp = newMob.getMaxHealth() * session.config.healthMultiplier;
                    newMob.setMaxHealth(maxHp);
                    newMob.setHealth(Math.min(newMob.getHealth(), maxHp));
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

        if (session.waveTask != null) {
            session.waveTask.cancel();
        }

        long clearTimeMs = System.currentTimeMillis() - session.startTime;
        EventBus.getInstance().publish(new DungeonCompleteEvent(player, session.level, clearTimeMs));

        // 發送金幣獎勵
        int coinsEarned = COIN_REWARDS.getOrDefault(session.level, 0);
        if (coinsEarned > 0) {
            sendDungeonReward(player, session.level, coinsEarned);
        }

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

    // 非同步發送副本獎勵到後端，避免卡住主執行緒
    private void sendDungeonReward(Player player, int level, int coinsEarned) {
        String backendUrl = plugin.getConfig().getString("backend-url");
        String token = plugin.getConfig().getString("backend-api-token");
        String mcUsername = player.getName(); // 在主執行緒先抓好，避免非同步中操作 Player 物件

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int statusCode = -1;
            String responseBody = "";

            try {
                JSONObject body = new JSONObject();
                body.put("mc_username", mcUsername);
                body.put("dungeon_level", level);
                body.put("coins_earned", coinsEarned);

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(backendUrl + "/api/dungeon/complete"))
                    .header("Content-Type", "application/json")
                    .header("X-Plugin-Secret", token)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                    .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                statusCode = response.statusCode();
                responseBody = response.body();

            } catch (Exception e) {
                plugin.getLogger().warning("[Dungeon] 發送金幣獎勵失敗: " + e.getMessage());
            }

            int finalStatusCode = statusCode;
            String finalResponseBody = responseBody;

            // 切回主執行緒才能操作玩家
            Bukkit.getScheduler().runTask(plugin, () -> {
                handleRewardResponse(player, coinsEarned, finalStatusCode, finalResponseBody);
            });
        });
    }

    // 解析後端回應並顯示訊息給玩家
    private void handleRewardResponse(Player player, int coinsEarned, int statusCode, String responseBody) {
        if (statusCode == 200) {
            try {
                JSONParser parser = new JSONParser();
                JSONObject json = (JSONObject) parser.parse(responseBody);
                long totalCoins = (Long) json.get("coin_balance");

                player.sendMessage("§6§l━━━━━━━━━━━━━━━━");
                player.sendMessage("§a副本完成！獲得 §e" + coinsEarned + " §a金幣");
                player.sendMessage("§7目前總金幣：§f" + totalCoins);
                player.sendMessage("§6§l━━━━━━━━━━━━━━━━");

            } catch (Exception e) {
                player.sendMessage("§a副本完成！獲得 §e" + coinsEarned + " §a金幣");
                plugin.getLogger().warning("[Dungeon] 解析獎勵回應失敗: " + e.getMessage());
            }

        } else if (statusCode == 404) {
            player.sendMessage("§c你的帳號尚未綁定網站，本次副本獎勵未發放。");
            player.sendMessage("§7請先到網站綁定 Minecraft 帳號。");

        } else {
            player.sendMessage("§c金幣獎勵發放失敗（請聯繫管理員）。");
            plugin.getLogger().warning("[Dungeon] 獎勵請求失敗，狀態碼: " + statusCode + "，內容: " + responseBody);
        }
    }

    
    // 單一種怪物的生成設定
    static class MobSpawn {
        EntityType entityType;
        int count;
        Material helmet;      // 頭盔，不需要就傳 null
        Material chestplate;  // 胸甲，不需要就傳 null
        Material leggings;    // 護腿，不需要就傳 null
        Material boots;       // 靴子，不需要就傳 null
        Material mainHand;    // 主手武器，不需要就傳 null

        // 只給數量，沒有裝備（原本的用法照樣能用，不會壞掉）
        MobSpawn(EntityType entityType, int count) {
            this(entityType, count, null, null, null, null, null);
        }

        // 完整裝備版本
        MobSpawn(EntityType entityType, int count, Material helmet, Material chestplate,
                Material leggings, Material boots, Material mainHand) {
            this.entityType = entityType;
            this.count = count;
            this.helmet = helmet;
            this.chestplate = chestplate;
            this.leggings = leggings;
            this.boots = boots;
            this.mainHand = mainHand;
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

        World world;
        DungeonConfig config;
        int centerX;
        int centerY;
        int centerZ;

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