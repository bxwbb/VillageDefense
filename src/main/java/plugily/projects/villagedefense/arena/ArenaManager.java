/*
 *  Village Defense - Protect villagers from hordes of zombies
 *  Copyright (c) 2026 Plugily Projects - maintained by Tigerpanzer_02 and contributors
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package plugily.projects.villagedefense.arena;

import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import plugily.projects.minigamesbox.api.arena.IPluginArena;
import plugily.projects.minigamesbox.api.user.IUser;
import plugily.projects.minigamesbox.classic.arena.PluginArenaManager;
import plugily.projects.minigamesbox.classic.handlers.language.MessageBuilder;
import plugily.projects.minigamesbox.classic.handlers.language.TitleBuilder;
import plugily.projects.minigamesbox.classic.utils.version.ServerVersion;
import plugily.projects.minigamesbox.classic.utils.version.VersionUtils;
import plugily.projects.minigamesbox.classic.utils.version.xseries.XSound;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.api.event.wave.VillageWaveEndEvent;
import plugily.projects.villagedefense.api.event.wave.VillageWaveStartEvent;
import plugily.projects.villagedefense.creatures.CreatureUtils;
import plugily.projects.villagedefense.kits.KitUtils;
import plugily.projects.villagedefense.utils.BiomeUtil;

import java.util.*;

/**
 * Village Defense 的竞技场流程管理器。
 *
 * <p>父类 {@link PluginArenaManager} 处理通用的加入、离开、停止和重置流程；
 * 本类只追加村庄守卫的规则：清理玩家宠物、结算波次、发放波次奖励、复活玩家。</p>
 *
 * @author Plajer
 * <p>
 * Created at 13.05.2018
 */
public class ArenaManager extends PluginArenaManager {

    private final Main plugin;

    public ArenaManager(Main plugin) {
        super(plugin);
        this.plugin = plugin;
    }

    @Override
    public void additionalSpectatorSettings(Player player, IPluginArena arena) {
        super.additionalSpectatorSettings(player, arena);
        if (arena instanceof Arena gameArena) {
            gameArena.initializePlayerRoundData(player);
        }
        // 禁止中途复活时，中途加入者应保持永久旁观，避免影响当前波次难度。
        if (!plugin.getConfigPreferences().getOption("RESPAWN_IN_GAME_JOIN")) {
            plugin.getUserManager().getUser(player).setPermanentSpectator(true);
        }
    }

    @Override
    public void leaveAttempt(@NotNull Player player, @NotNull IPluginArena arena) {
        Arena gameArena = (Arena) arena;
        gameArena.clearPlayerRoundData(player);
        gameArena.hideBossBars(player);
        if (plugin.getSkillManager() != null) {
            plugin.getSkillManager().clearPlayerState(player);
        }
        // 玩家离开时清掉归属于他的狼/铁傀儡，避免残留宠物继续帮其他玩家打波次。
        List<Entity> pets = new ArrayList<>(gameArena.getAlivePetsList());
        pets.stream()
                .filter(Objects::nonNull)
                .filter(pet -> pet.hasMetadata("VD_OWNER_UUID"))
                .filter(pet -> UUID.fromString(pet.getMetadata("VD_OWNER_UUID").get(0).asString()).equals(player.getUniqueId()))
                .forEach(pet -> {
                    if (pet instanceof IronGolem) {
                        gameArena.removeIronGolem((IronGolem) pet);
                    } else if (pet instanceof Pillager) {
                        gameArena.removePillager((Pillager) pet);
                    } else {
                        gameArena.removeWolf((Wolf) pet);
                    }
                });
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            // 先解除骑乘关系，再让父类做背包、状态、传送等通用清理。
            vehicle.eject();
        }
        super.leaveAttempt(player, arena);
        publishNetworkRooms();
    }

    @Override
    public void stopGame(boolean quickStop, @NotNull IPluginArena arena) {
        Arena gameArena = ((Arena) arena);
        gameArena.clearPendingTimedRespawns();
        int wave = gameArena.getWave();
        for (Player player : arena.getPlayers()) {
            if (plugin.getSkillManager() != null) {
                plugin.getSkillManager().clearPlayerState(player);
            }
            IUser user = plugin.getUserManager().getUser(player);
            if (!quickStop) {
                // quickStop 多用于管理命令，不写胜负和最高波次，避免污染玩家统计。
                if (user.getStatistic("HIGHEST_WAVE") <= wave) {
                    if (user.isSpectator() && !plugin.getConfigPreferences().getOption("RESPAWN_AFTER_WAVE")) {
                        continue;
                    }
                    user.setStatistic("HIGHEST_WAVE", wave);
                }
                if (gameArena.isFinalWaveCompleted()) {
                    plugin.getUserManager().addStat(user, plugin.getStatsStorage().getStatisticType("WINS"));
                    XSound.ENTITY_VILLAGER_YES.play(player);
                } else {
                    plugin.getUserManager().addStat(user, plugin.getStatsStorage().getStatisticType("LOSES"));
                    XSound.ENTITY_VILLAGER_NO.play(player);
                }
                plugin.getUserManager().addExperience(player, wave);
            }
        }
        List<LivingEntity> allEntities = new ArrayList<>();
        allEntities.addAll(gameArena.getEnemies());
        allEntities.addAll(gameArena.getAliveEntitiesList());
        gameArena.clearBossBars();
        for (LivingEntity entity : allEntities) {
            if (ServerVersion.Version.isCurrentHigher(ServerVersion.Version.v1_12)) {
                // 结束阶段先关 AI，减少地图恢复和传送期间实体继续移动造成的边界问题。
                entity.setAI(false);
            }
        }
        super.stopGame(quickStop, arena);
        publishNetworkRooms();
    }

    /**
     * End wave in game.
     * Calls VillageWaveEndEvent event
     *
     * @param arena End wave on which arena
     * @see VillageWaveEndEvent
     */
    public void endWave(@NotNull Arena arena) {
        int wave = arena.getWave();

        new TitleBuilder("IN_GAME_MESSAGES_VILLAGE_WAVE_TITLE_END").asKey().arena(arena).integer(wave).sendArena();
        giveWaveEndRewards(arena, wave);

        // 达到当前模式终点后直接结束游戏，不再进入下一波等待。
        if (arena.isFinalWave(wave)) {
            arena.setFinalWaveCompleted(true);
            stopGame(false, arena);
            return;
        }

        arena.setTimer(plugin.getConfig().getInt("Time-Manager.Cooldown-Before-Next-Wave", 25));
        arena.getEnemySpawnManager().getEnemyCheckerLocations().clear();
        arena.setWave(wave + 1);

        // 事件触发时 arena.getWave() 已经是下一波编号，监听器要按这个语义使用。
        Bukkit.getPluginManager().callEvent(new VillageWaveEndEvent(arena, arena.getWave()));

        refreshAllPlayers(arena);
        refreshPets(arena);
        removeAllAssists(arena);

        if (plugin.getConfigPreferences().getOption("RESPAWN_AFTER_WAVE")) {
            ArenaUtils.bringDeathPlayersBack(arena);
        }

        for (Player player : arena.getPlayersLeft()) {
            arena.showBossBars(player);
            plugin.getUserManager().addExperience(player, 5);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.HUNGER);
        }
        arena.getVillagerSpawns().getFirst().getWorld().setStorm(false);
        arena.getVillagerSpawns().getFirst().getWorld().setWeatherDuration(0);
        BiomeUtil.setBiome5ChunkRadius(arena.getStartLocation(), Biome.PLAINS);
        if (arena.getWaveType().equals(Arena.WaveType.THEFT)) {
            for (Location bonusPoint : arena.getBonusPoints()) {
                spawnRewardChest(bonusPoint);
            }
        }
    }

    private void giveWaveEndRewards(Arena arena, int wave) {
        int waveEndPoints = getConfiguredWaveEndPoints();
        int waveEndOrbs = getConfiguredWaveEndOrbs(wave);

        for (IUser user : plugin.getUserManager().getUsers(arena)) {
            if (!user.isSpectator() && !user.isPermanentSpectator()) {
                Player player = user.getPlayer();
                // END_WAVE 支持把当前波次作为参数传入奖励系统，便于配置按波次发奖励。
                plugin.getRewardsHandler().performReward(player, arena, plugin.getRewardsHandler().getRewardType("END_WAVE"), wave);
                if (waveEndPoints > 0) {
                    arena.addPlayerPoints(player, waveEndPoints);
                }
                if (waveEndOrbs > 0) {
                    int orbsReward = plugin.getSkillManager() == null ? waveEndOrbs : plugin.getSkillManager().applyEconomyMultiplier(player, waveEndOrbs);
                    user.adjustStatistic(plugin.getStatsStorage().getStatisticType("ORBS"), orbsReward);
                }
                KitUtils.reStock(user);
            }
            XSound.ENTITY_VILLAGER_YES.play(user.getPlayer());
        }
    }

    private void refreshAllPlayers(Arena arena) {
        String feelRefreshed = new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_FEEL_REFRESHED").asKey().build();
        String nextWave = new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_WAVE_NEXT_IN").asKey().arena(arena).integer(arena.getTimer()).build();

        for (Player player : arena.getPlayers()) {
            player.sendMessage(nextWave);
            // 波次间隙治疗 25% 最大生命，向上取整防止低血量配置下治疗为 0。
            int healPower = (int) Math.ceil(VersionUtils.getMaxHealth(player) * 0.25); //25% of max health rounded up
            player.setHealth(Math.min(player.getHealth() + healPower, VersionUtils.getMaxHealth(player)));
            player.sendMessage(feelRefreshed);
        }
    }

    // 刷新宠物
    private void refreshPets(Arena arena) {
        int healPower = 0; //1 heart
        for (Villager villager : arena.getVillagers()) {
            villager.setCustomName(CreatureUtils.getHealthNameTag(villager));
        }
        healPower = 3; //1.5 hearts
        List<LivingEntity> pets = new ArrayList<>(arena.getAlivePetsList());
        for (LivingEntity pet : pets) {
            //double heal for golems
            double multiplier = pet instanceof IronGolem ? 2.0 : 1.0;
            pet.setHealth(Math.min(pet.getHealth() + (healPower * multiplier), VersionUtils.getMaxHealth(pet)));
        }
    }

    private void removeAllAssists(Arena arena) {
        List<LivingEntity> allEntities = new ArrayList<>();
        allEntities.addAll(arena.getPlayers());
        allEntities.addAll(arena.getAlivePetsList());
    }

    /**
     * 开启新的一波
     * Calls VillageWaveStartEvent event
     *
     * @param arena start wave on this arena
     * @see VillageWaveStartEvent
     */
    public void startWave(@NotNull Arena arena) {
        plugin.getDebugger().debug("[{0}] Wave start event called", arena.getId());
        long start = System.currentTimeMillis();

        int wave = arena.getWave();
        arena.markWaveStarted();

        Bukkit.getPluginManager().callEvent(new VillageWaveStartEvent(arena, wave));

        // 数量从配置分段读取；超过上限后改为提升单体难度，保护服务器实体数量。

        /*
        a.前期（如1-5波）#发育期
	        原版自然生成僵尸，穿插骷髅
        b.中期（如6-20波）#快速发育+人海战术
            数量增加，生成点开始随机，僵尸随机获得速度药水效果（不包含伤害提升类），小概率僵尸获得武器盔甲，均为铁质以下，开始生成苦力怕，蜘蛛
        c.后期（如21-50波）#伤害提升
        	僵尸获得更高级护甲，药水效果升级，开始生成小僵尸、洞穴蜘蛛
        d.无尽（如51+）
            增加幻翼（速度2），生成恼鬼（少量，太超模了，可以穿墙），
            天空出现地狱门生成烈焰人、凋零骷髅（主世界生物召唤下界生物帮忙），
            出现小鸡骑士，蜘蛛骑士，[设计新增生物]，
            骷髅马骑士（骷髅马若存活玩家可骑）
         */

        int zombiesAmount = getConfiguredZombiesAmount(wave);
        int maxzombies = plugin.getConfig().getInt("Limit.Spawn.Creatures", 75);
        arena.setArenaOption("CREATURE_DIFFICULTY_MULTIPLIER", 1);

        if (zombiesAmount > maxzombies) {
            int multiplier = (int) Math.ceil((zombiesAmount - (double) maxzombies) / plugin.getConfig().getInt("Creatures.Multiplier-Divider", 18));

            if (multiplier < 2) multiplier = 2;

            arena.setArenaOption("CREATURE_DIFFICULTY_MULTIPLIER", multiplier);

            plugin.getDebugger().debug("[{0}] Detected abnormal wave ({1})! Applying zombie limit and difficulty multiplier to {2} | ZombiesAmount: {3} | MaxZombies: {4}",
                    arena.getId(), wave, arena.getArenaOption("CREATURE_DIFFICULTY_MULTIPLIER"), zombiesAmount, maxzombies);

            zombiesAmount = maxzombies;
        }

        int zombieIdle = getConfiguredSpawnIntervalTicks(wave);

        // 设置刷怪数量和生成间隔
        arena.setArenaOption("ZOMBIES_TO_SPAWN", zombiesAmount);
        arena.setArenaOption("ZOMBIE_IDLE_PROCESS", zombieIdle);
        spawnModeBoss(arena, wave);

        if (zombieIdle > 0) {
            plugin.getDebugger().debug("[{0}] Spawn idle process initiated to prevent server overload! Value: {1}", arena.getId(), zombieIdle);
        }

        if (plugin.getConfigPreferences().getOption("RESPAWN_AFTER_WAVE")) {
            ArenaUtils.bringDeathPlayersBack(arena);
        }

        new TitleBuilder("IN_GAME_MESSAGES_VILLAGE_WAVE_TITLE_START").asKey().arena(arena).integer(wave).sendArena();

        for (IUser user : plugin.getUserManager().getUsers(arena)) {
            Player player = user.getPlayer();
            plugin.getRewardsHandler().performReward(player, arena, plugin.getRewardsHandler().getRewardType("START_WAVE"));

            for (PotionEffectType potionEffectType : arena.getShopManager().potionEffectData.keySet()) {
                if (arena.getShopManager().potionEffectData.get(potionEffectType).maxLevel != 0) {
                    player.addPotionEffect(new PotionEffect(potionEffectType, 10000, arena.getShopManager().potionEffectData.get(potionEffectType).maxLevel - 1, true, false, true));
                }
            }

            new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_WAVE_STARTED").asKey().arena(arena).integer(wave).player(player).sendPlayer();
        }

        plugin.getDebugger().debug("[{0}] Wave start event finished took {1}ms", arena.getId(), System.currentTimeMillis() - start);
    }

    private int getConfiguredZombiesAmount(int wave) {
        return getConfiguredWaveValue(wave, "Creatures.Spawn-Amount", 100);
    }

    private int getConfiguredSpawnIntervalTicks(int wave) {
        return getConfiguredWaveValue(wave, "Creatures.Spawn-Interval-Ticks", 20);
    }

    private int getConfiguredWaveEndPoints() {
        return Math.max(0, plugin.getConfig().getInt("Points.Wave-End", 0));
    }

    private int getConfiguredWaveEndOrbs(int wave) {
        return getConfiguredWaveValue(wave, "Orbs.Wave-End", 1);
    }

    private void spawnModeBoss(Arena arena, int wave) {
        String bossesPath = "Game-Modes." + arena.getGameMode().name() + ".Bosses";
        ConfigurationSection bosses = plugin.getConfig().getConfigurationSection(bossesPath);
        if (bosses == null) {
            return;
        }

        for (String bossId : bosses.getKeys(false)) {
            String bossPath = bossesPath + "." + bossId;
            if (!plugin.getConfig().getBoolean(bossPath + ".Enabled", true)
                    || wave != plugin.getConfig().getInt(bossPath + ".Wave", arena.getFinalWave())
                    || !arena.markBossSpawned(arena.getGameMode().name() + ":" + bossId + ":" + wave)) {
                continue;
            }
            spawnConfiguredBoss(arena, bossPath);
        }
    }

    private void spawnConfiguredBoss(Arena arena, String bossPath) {
        EntityType bossType = getConfiguredBossType(bossPath);
        Location location = getBossSpawnLocation(arena);
        Entity entity = location.getWorld().spawnEntity(location, bossType);
        if (!(entity instanceof LivingEntity)) {
            entity.remove();
            return;
        }
        LivingEntity boss = (LivingEntity) entity;
        double health = Math.max(1.0d, plugin.getConfig().getDouble(bossPath + ".Health", 200.0d));
        VersionUtils.setMaxHealth(boss, health);
        boss.setHealth(health);
        String bossName = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString(bossPath + ".Name", "&c&lBoss"));
        boss.setCustomName(bossName);
        boss.setCustomNameVisible(true);
        arena.getEnemies().add(boss);
        arena.addBoss(boss);
        if (shouldCreateBossBar(bossType)) {
            BossBar bossBar = Bukkit.createBossBar(bossName, BarColor.RED, BarStyle.SEGMENTED_10);
            arena.addBossBar(boss, bossBar);
        }
    }

    private boolean shouldCreateBossBar(EntityType bossType) {
        return bossType != EntityType.WITHER && bossType != EntityType.ENDER_DRAGON;
    }

    private EntityType getConfiguredBossType(String bossPath) {
        String configuredType = plugin.getConfig().getString(bossPath + ".Type", "WITHER");
        try {
            return EntityType.valueOf(configuredType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return EntityType.WITHER;
        }
    }

    private Location getBossSpawnLocation(Arena arena) {
        List<Location> zombieSpawns = arena.getZombieSpawns();
        if (!zombieSpawns.isEmpty()) {
            return zombieSpawns.get(0);
        }
        return arena.getStartLocation();
    }

    private int getConfiguredWaveValue(int wave, String path, int fallback) {
        ConfigurationSection ranges = plugin.getConfig().getConfigurationSection(path + ".Ranges");
        if (ranges != null) {
            for (String range : ranges.getKeys(false)) {
                if (isWaveInRange(wave, range)) {
                    return Math.max(0, ranges.getInt(range));
                }
            }
        }
        return Math.max(0, plugin.getConfig().getInt(path + ".Default", fallback));
    }

    private boolean isWaveInRange(int wave, String range) {
        String value = range.replace(" ", "");
        if (value.endsWith("+")) {
            return wave >= parseWaveBound(value.substring(0, value.length() - 1), Integer.MAX_VALUE);
        }
        if (value.contains("-")) {
            String[] bounds = value.split("-", 2);
            int min = parseWaveBound(bounds[0], Integer.MIN_VALUE);
            int max = parseWaveBound(bounds[1], Integer.MAX_VALUE);
            return wave >= min && wave <= max;
        }
        return wave == parseWaveBound(value, Integer.MIN_VALUE);
    }

    private int parseWaveBound(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void publishNetworkRooms() {
        if (plugin.getNetworkRoomManager() != null) {
            plugin.getNetworkRoomManager().requestRoomPublish(2L);
        }
    }

        private static final Random RANDOM = new Random();

        /**
         * 在指定坐标生成奖励宝箱 + 粒子音效 + 随机战利品
         * @param loc 生成坐标
         */
        public static void spawnRewardChest(Location loc) {
            // 1. 设置方块为宝箱
            loc.getBlock().setType(Material.CHEST);

            // 2. 播放粒子效果（环绕烟花粒子）
            loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.add(0.5, 0.5, 0.5),
                    80, 0.5, 0.5, 0.5, 0.15);
            loc.subtract(0.5, 0.5, 0.5);

            // 3. 播放音效
            loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.1f);

            // 4. 获取宝箱库存
            Inventory chestInv = ((org.bukkit.block.Chest) loc.getBlock().getState()).getInventory();
            chestInv.clear();

            // 5. 随机生成奖励物品
            fillChestRandomLoot(chestInv);
        }

        // 填充随机战利品：药水、金苹果、附魔金苹果、稀有食物
        private static void fillChestRandomLoot(Inventory inv) {
            // 随机药水类型池
            PotionEffectType[] potionTypes = {
                    PotionEffectType.INSTANT_HEALTH,
                    PotionEffectType.SPEED,
                    PotionEffectType.STRENGTH,
                    PotionEffectType.REGENERATION,
                    PotionEffectType.INVISIBILITY,
                    PotionEffectType.FIRE_RESISTANCE
            };

            // 随机往箱子塞 6~12 个物品
            int itemCount = 6 + RANDOM.nextInt(7);

            for (int i = 0; i < itemCount; i++) {
                int rand = RANDOM.nextInt(100);
                ItemStack item;

                if (rand < 25) {
                    // 金苹果
                    item = new ItemStack(Material.GOLDEN_APPLE, 1 + RANDOM.nextInt(3));
                } else if (rand < 40) {
                    // 附魔金苹果
                    item = new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1);
                } else if (rand < 70) {
                    // 随机药水
                    item = new ItemStack(Material.POTION);
                    PotionMeta meta = (PotionMeta) item.getItemMeta();
                    if (meta != null) {
                        PotionEffectType type = potionTypes[RANDOM.nextInt(potionTypes.length)];
                        meta.addCustomEffect(new PotionEffect(type, 100 * RANDOM.nextInt(5), RANDOM.nextInt(3)), true);
                        item.setItemMeta(meta);
                    }
                } else if (rand < 85) {
                    // 腐肉、面包、胡萝卜等补给
                    Material[] foods = {Material.BREAD, Material.CARROT, Material.GOLDEN_CARROT, Material.COOKED_BEEF};
                    item = new ItemStack(foods[RANDOM.nextInt(foods.length)], 2 + RANDOM.nextInt(5));
                } else {
                    // 末影珍珠、不死图腾小概率
                    Material[] rare = {Material.ENDER_PEARL, Material.TOTEM_OF_UNDYING};
                    item = new ItemStack(rare[RANDOM.nextInt(rare.length)], 1);
                }

                // 随机格子放入
                int slot = RANDOM.nextInt(inv.getSize());
                inv.setItem(slot, item);
            }
        }

}
