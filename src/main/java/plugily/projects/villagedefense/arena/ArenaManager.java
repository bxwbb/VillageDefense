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

import org.bukkit.Bukkit;
import org.bukkit.block.Biome;
import org.bukkit.entity.*;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
        // 禁止中途复活时，中途加入者应保持永久旁观，避免影响当前波次难度。
        if (!plugin.getConfigPreferences().getOption("RESPAWN_IN_GAME_JOIN")) {
            plugin.getUserManager().getUser(player).setPermanentSpectator(true);
        }
    }

    @Override
    public void leaveAttempt(@NotNull Player player, @NotNull IPluginArena arena) {
        Arena gameArena = (Arena) arena;
        // 玩家离开时清掉归属于他的狼/铁傀儡，避免残留宠物继续帮其他玩家打波次。
        List<Entity> pets = new ArrayList<>(gameArena.getAlivePetsList());
        pets.stream()
                .filter(Objects::nonNull)
                .filter(pet -> pet.hasMetadata("VD_OWNER_UUID"))
                .filter(pet -> UUID.fromString(pet.getMetadata("VD_OWNER_UUID").get(0).asString()).equals(player.getUniqueId()))
                .forEach(pet -> {
                    if (pet instanceof IronGolem) {
                        gameArena.removeIronGolem((IronGolem) pet);
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
    }

    @Override
    public void stopGame(boolean quickStop, @NotNull IPluginArena arena) {
        int wave = ((Arena) arena).getWave();
        for (Player player : arena.getPlayers()) {
            IUser user = plugin.getUserManager().getUser(player);
            if (!quickStop) {
                // quickStop 多用于管理命令，不写胜负和最高波次，避免污染玩家统计。
                if (user.getStatistic("HIGHEST_WAVE") <= wave) {
                    if (user.isSpectator() && !plugin.getConfigPreferences().getOption("RESPAWN_AFTER_WAVE")) {
                        continue;
                    }
                    user.setStatistic("HIGHEST_WAVE", wave);
                }
                if (plugin.getConfigPreferences().getOption("LIMIT_WAVE_UNLIMITED") && wave >= plugin.getConfig().getInt("Limit.Wave.Game-End", 25)) {
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
        Arena gameArena = ((Arena) arena);
        allEntities.addAll(gameArena.getEnemies());
        allEntities.addAll(gameArena.getAliveEntitiesList());
        for (LivingEntity entity : allEntities) {
            if (ServerVersion.Version.isCurrentHigher(ServerVersion.Version.v1_12)) {
                // 结束阶段先关 AI，减少地图恢复和传送期间实体继续移动造成的边界问题。
                entity.setAI(false);
            }
        }
        super.stopGame(quickStop, arena);
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

        // 有限波次模式下，达到配置终点直接结束游戏，不再进入下一波等待。
        if (plugin.getConfigPreferences().getOption("LIMIT_WAVE_UNLIMITED") && wave >= plugin.getConfig().getInt("Limit.Wave.Game-End", 25)) {
            stopGame(false, arena);
            return;
        }

        new TitleBuilder("IN_GAME_MESSAGES_VILLAGE_WAVE_TITLE_END").asKey().arena(arena).integer(wave).sendArena();

        for (IUser user : plugin.getUserManager().getUsers(arena)) {
            if (!user.isSpectator() && !user.isPermanentSpectator()) {
                Player player = user.getPlayer();
                // END_WAVE 支持把当前波次作为参数传入奖励系统，便于配置按波次发奖励。
                plugin.getRewardsHandler().performReward(player, arena, plugin.getRewardsHandler().getRewardType("END_WAVE"), arena.getWave());
                KitUtils.reStock(user);
            }
            XSound.ENTITY_VILLAGER_YES.play(user.getPlayer());
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
            plugin.getUserManager().addExperience(player, 5);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.HUNGER);
        }
        arena.getVillagerSpawns().getFirst().getWorld().setStorm(false);
        arena.getVillagerSpawns().getFirst().getWorld().setWeatherDuration(0);
        BiomeUtil.setBiome5ChunkRadius(arena.getStartLocation(), Biome.PLAINS);
    }

    private void refreshAllPlayers(Arena arena) {
        int waveStat = arena.getWave() * 10;

        String feelRefreshed = new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_FEEL_REFRESHED").asKey().build();
        String nextWave = new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_WAVE_NEXT_IN").asKey().arena(arena).integer(arena.getTimer()).build();

        for (Player player : arena.getPlayers()) {
            player.sendMessage(nextWave);
            // 波次间隙治疗 25% 最大生命，向上取整防止低血量配置下治疗为 0。
            int healPower = (int) Math.ceil(VersionUtils.getMaxHealth(player) * 0.25); //25% of max health rounded up
            player.setHealth(Math.min(player.getHealth() + healPower, VersionUtils.getMaxHealth(player)));
            player.sendMessage(feelRefreshed);
            plugin.getUserManager().getUser(player).adjustStatistic(plugin.getStatsStorage().getStatisticType("ORBS"), waveStat);
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

        Bukkit.getPluginManager().callEvent(new VillageWaveStartEvent(arena, wave));

        // 数量随玩家数和波次平方增长；超过上限后改为提升单体难度，保护服务器实体数量。

        /*
        a.前期（如1-5波）#发育期
	        原版自然生成僵尸，穿插骷髅
        b.中期（如6-20波）#快速发育+人海战术
            数量增加，生成点开始随机，僵尸随机获得速度药水效果（不包含伤害提升类），小概率僵尸获得武器盔甲，均为铁质以下，开始生成苦力怕，蜘蛛
        c.后期（如21-50波）#伤害提升
        	僵尸获得更高级护甲，药水效果升级，开始生成小僵尸、洞穴蜘蛛
        d.无尽（如51+）
            增加幻翼（速度2），生成恼鬼（少量，太超模了，可以穿墙），
            天空出现地狱门生成恶魂、烈焰人、凋零骷髅（主世界生物召唤下界生物帮忙），
            出现小鸡骑士，蜘蛛骑士，[设计新增生物]，
            骷髅马骑士（骷髅马若存活玩家可骑）
         */

        int zombiesAmount = (int) Math.ceil((arena.getPlayers().size() * 0.5) * (wave * wave) / 2);
        int maxzombies = plugin.getConfig().getInt("Limit.Spawn.Creatures", 75);

        if (zombiesAmount > maxzombies) {
            int multiplier = (int) Math.ceil((zombiesAmount - (double) maxzombies) / plugin.getConfig().getInt("Creatures.Multiplier-Divider", 18));

            if (multiplier < 2) multiplier = 2;

            arena.setArenaOption("CREATURE_DIFFICULTY_MULTIPLIER", multiplier);

            plugin.getDebugger().debug("[{0}] Detected abnormal wave ({1})! Applying zombie limit and difficulty multiplier to {2} | ZombiesAmount: {3} | MaxZombies: {4}",
                    arena.getId(), wave, arena.getArenaOption("CREATURE_DIFFICULTY_MULTIPLIER"), zombiesAmount, maxzombies);

            zombiesAmount = maxzombies;
        }

        // 高波次增加刷怪间隔，避免同一 tick 生成过多实体。
        int zombieIdle = (int) Math.floor((double) wave / 15);

        // 设置刷怪数量和生成间隔
        arena.setArenaOption("ZOMBIES_TO_SPAWN", zombiesAmount);
        arena.setArenaOption("ZOMBIE_IDLE_PROCESS", zombieIdle);

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

            new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_WAVE_STARTED").asKey().arena(arena).integer(wave).player(player).sendPlayer();
        }

        plugin.getDebugger().debug("[{0}] Wave start event finished took {1}ms", arena.getId(), System.currentTimeMillis() - start);
    }

}
