
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

package plugily.projects.villagedefense.arena.managers.enemy.spawner;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import plugily.projects.minigamesbox.classic.utils.version.ServerVersion;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.arena.Arena;
import plugily.projects.villagedefense.arena.managers.spawner.EnemySpawner;
import plugily.projects.villagedefense.creatures.v1_9_UP.*;
import plugily.projects.villagedefense.utils.BiomeUtil;

import java.util.*;

/**
 * 敌人刷怪器注册表的 1.8 兼容实现。
 *
 * <p>每个 {@link EnemySpawner} 只负责一种敌人的生成规则。Registry 负责注册可用类型，
 * 并在每轮刷怪时打乱顺序后依次尝试，避免固定顺序导致某些怪物长期优先生成。</p>
 */
public class EnemySpawnerRegistryLegacy {
    final Set<EnemySpawner> enemySpawnerSet = new TreeSet<>(Collections.reverseOrder());
    final Set<CustomRideableCreature> rideableCreatures = new HashSet<>();
    final Main plugin;

    private static final int TIME_STEP = 8;

    public EnemySpawnerRegistryLegacy(Main plugin) {
        this.plugin = plugin;
        registerCreatures();
        registerRideableCreatures();
    }

    public void registerRideableCreatures() {
        // 1.8 路径不注册高版本可骑乘实体，防止触发不存在的 API。
        if (ServerVersion.Version.isCurrentEqualOrLower(ServerVersion.Version.v1_8_8)) {
            return;
        }
    }

    public void registerCreatures() {
        enemySpawnerSet.add(new NormalWitherSkeleton(plugin));
        enemySpawnerSet.add(new NormalBlaze(plugin));
        enemySpawnerSet.add(new NormalGhast(plugin));
        enemySpawnerSet.add(new NormalVex(plugin));
        enemySpawnerSet.add(new NormalPhantom(plugin));
        enemySpawnerSet.add(new NormalCaveSpider(plugin));
        enemySpawnerSet.add(new NormalSpider(plugin));
        enemySpawnerSet.add(new CreeperBomb(plugin));
        enemySpawnerSet.add(new NormalZombie(plugin));
        enemySpawnerSet.add(new SkeletonArcher(plugin));
    }

    /**
     * 在竞技场内生成敌人（僵尸）
     *
     * @param random 随机数实例
     * @param arena  目标竞技场
     */
    public void spawnEnemies(Random random, Arena arena) {
        int spawn = arena.getWave() * arena.getPlayers().size();
        // 生物生成限制
        int zombiesLimit = plugin.getConfig().getInt("Limit.Spawn.Creatures", 75);
        if (zombiesLimit < spawn) {
            // 波次高于实体上限时，降低本轮 spawn 权重，实体强度由 ArenaManager 的倍率补足。
            spawn = (int) Math.ceil(zombiesLimit / 2.0);
        }
        String zombieSpawnCounterOption = "ZOMBIE_SPAWN_COUNTER";
        // 计数器递增
        arena.changeArenaOptionBy(zombieSpawnCounterOption, 1);
        if (arena.getArenaOption(zombieSpawnCounterOption) == 20) {
            // 20 tick 周期计数器，供具体 spawner 做阶段性生成判断。
            arena.setArenaOption(zombieSpawnCounterOption, 0);
        }

//        // 获取怪物生成器，所有怪物的生成都有此控制器实现
        List<EnemySpawner> enemySpawners = new ArrayList<>(enemySpawnerSet);

        /*
        a.前期（如1-5波）#发育期
	        原版自然生成僵尸，穿插骷髅
        b.中期（如6-20波）#快速发育+人海战术
            数量增加，生成点开始随机，僵尸随机获得速度药水效果（不包含伤害提升类），小概率僵尸获得武器盔甲，均为铁质以下，开始生成苦力怕，蜘蛛
        c.后期（如21-50波）#伤害提升
        	僵尸获得更高级护甲，药水效果升级，开始生成小僵尸、洞穴蜘蛛
        d.无尽（如51+）
        	增加幻翼（速度2），生成恼鬼（少量，太超模了，可以穿墙），天空出现地狱门生成恶魂、烈焰人、凋零骷髅（主世界生物召唤下界生物帮忙），出现小鸡骑士，蜘蛛骑士，[设计新增生物]，骷髅马骑士（骷髅马若存活玩家可骑）
        e.其他
            BOSS波：每10波出现精英生物，提供可选的额外药水效果
            劫掠波：村庄外随机生成灾厄大队长（超雄版，索敌范围不变，伤害不变，获得速度2效果），若玩家击杀则获得灾厄BUFF，会给村庄引来劫掠战（白日会出现），打完村民会在村庄中心的箱子里随机放奖励
            寒潮波：天气变为下雪，僵尸数量减少，生成溺尸，流浪者（偏远程），玩家获得缓慢1
            沙尘波：切换时间为黄昏，生成大量尸壳（偏近战）玩家获得饥饿1
         */
        int wave = arena.getWave();

        if (wave % 10 != 0 && wave > 15) {
            if (random.nextInt(100) <= 20) {
                // 触发特殊波次
                /*
                劫掠波：村庄外随机生成灾厄大队长（超雄版，索敌范围不变，伤害不变，获得速度2效果），若玩家击杀则获得灾厄BUFF，会给村庄引来劫掠战（白日会出现），打完村民会在村庄中心的箱子里随机放奖励
                寒潮波：天气变为下雪，僵尸数量减少，生成溺尸，流浪者（偏远程），玩家获得缓慢1
                沙尘波：切换时间为黄昏，生成大量尸壳（偏近战）玩家获得饥饿1
                 */
                int which = random.nextInt(3);
                World world = arena.getVillagerSpawns().getFirst().getWorld();
                switch (which) {
                    case 0:
                        // 寒潮波
                        world.setStorm(true);
                        world.setWeatherDuration(999999);
                        world.setThundering(false);
                        world.setThunderDuration(0);
                        BiomeUtil.setBiome5ChunkRadius(arena.getStartLocation(), Biome.SNOWY_PLAINS);
                        enemySpawners.add(new NormalDrowned(plugin));
                        enemySpawners.add(new NormalStray(plugin));
                        for (Player player : arena.getPlayers()) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10000, 0));
                            player.sendMessage(Component.text("寒潮来袭"));
                        }
                        break;
                    case 1:
                        // 沙尘波
                        smoothToDusk(world);
                        enemySpawners.clear();
                        enemySpawners.add(new NormalHusk(plugin));
                        enemySpawners.add(new SkeletonArcher(plugin));
                        enemySpawners.add(new NormalZombie(plugin));
                        for (Player player : arena.getPlayers()) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 10000, 0));
                            player.sendMessage(Component.text("沙尘来袭"));
                        }
                        break;
                    case 2:
                        // 劫掠波
                        enemySpawners.add(new NormalRaidCaptain(plugin));
                        for (Player player : arena.getPlayers()) {
                            player.sendMessage(Component.text("劫掠队长来袭"));
                        }
                        break;
                }
            }
        }

        for (Player player : arena.getPlayers()) {
            if (player.hasPotionEffect(PotionEffectType.BAD_OMEN)) {
                enemySpawners.clear();
                enemySpawners.add(new NormalPillager(plugin));
                enemySpawners.add(new NormalVindicator(plugin));
                enemySpawners.add(new NormalWitch(plugin));
                enemySpawners.add(new NormalEvoker(plugin));
                enemySpawners.add(new NormalRavager(plugin));
                for (Player arenaPlayer : arena.getPlayers()) {
                    arenaPlayer.sendMessage(Component.text("劫掠来袭!!"));
                }
                playRaidHorn(arena);
                player.removePotionEffect(PotionEffectType.BAD_OMEN);
                break;
                // TODO : 劫掠结束后的奖励宝箱
            }
        }

        // 打乱生成顺序
        Collections.shuffle(enemySpawners);
        for (EnemySpawner enemySpawner : enemySpawners) {
            // spawner 内部会按波次、权重和剩余数量决定是否真正生成。
            plugin.getDebugger().debug("Trying enemy spawn for " + enemySpawner.getName());
            enemySpawner.spawn(random, arena, spawn);
        }

        if (wave % 10 == 0) {
            giveRandomEliteBuff(arena);
        }
    }

    // 播放劫掠号角（全图）
    public static void playRaidHorn(Arena arena) {
        for (Player player : arena.getPlayers()) {
            playRaidHorn(player.getLocation(), player);
        }
    }

    // 在指定位置播放，只给某个玩家听
    public static void playRaidHorn(Location loc, Player player) {
        // 音量 32.0、音高 1.0 和原版一致
        player.playSound(
                loc,
                "event.raid.horn",
                SoundCategory.AMBIENT,
                32.0F,
                1.0F
        );
    }

    /**
     * 丝滑渐变到指定游戏时间
     * @param world 目标世界
     * @param targetTime 目标时间 0~24000
     * @param dayLength 过渡占用的游戏时间段长度(0~24000)
     */
    public void smoothSetTime(World world, long targetTime, long dayLength) {
        if (world == null || dayLength <= 0) return;

        long startTime = world.getTime();
        long diff = targetTime - startTime;
        if (diff == 0) return;

        // 按20tick/s，MC 1游戏刻 = 1真实tick
        long totalRunTicks = dayLength;
        double perTickAdd = (double) diff / totalRunTicks;
        final long[] tickCount = {0};

        new BukkitRunnable() {
            @Override
            public void run() {
                tickCount[0]++;
                if (tickCount[0] >= totalRunTicks) {
                    world.setTime(targetTime);
                    this.cancel();
                    return;
                }
                long nowTime = Math.round(startTime + perTickAdd * tickCount[0]);
                nowTime = Math.max(0, Math.min(24000, nowTime));
                world.setTime(nowTime);
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    /**
     * 丝滑切换到黄昏 12500
     * 默认占用 3000 游戏时长过渡
     */
    public void smoothToDusk(World world) {
        smoothSetTime(world, 12500, 3000);
    }

    public void giveRandomEliteBuff(Arena arena) {
        List<PotionEffectType> goodEffects = Arrays.asList(
                PotionEffectType.STRENGTH,       // 力量 加攻击
                PotionEffectType.RESISTANCE,     // 抗性 减伤害
                PotionEffectType.SPEED,          // 速度
                PotionEffectType.REGENERATION,   // 再生
                PotionEffectType.FIRE_RESISTANCE // 防火
        );

        Random random = new Random();

        List<LivingEntity> validEnemies = new ArrayList<>();
        for (LivingEntity enemy : arena.getEnemies()) {
            if (enemy.isOnGround() && enemy instanceof CustomCreature) {
                validEnemies.add(enemy);
            }
        }

        Collections.shuffle(validEnemies);
        int takeCount = Math.min(5, validEnemies.size());
        List<LivingEntity> eliteCandidates = validEnemies.subList(0, takeCount);

        for (LivingEntity mob : eliteCandidates) {
            PotionEffectType effectType = goodEffects.get(random.nextInt(goodEffects.size()));
            int amp = random.nextInt(2);
            PotionEffect effect = new PotionEffect(effectType, 99999, amp, false, false);
            mob.addPotionEffect(effect);
        }
    }

    /**
     * Get the set of enemy spawners
     *
     * @return the set of enemy spawners
     */
    public Set<EnemySpawner> getEnemySpawnerSet() {
        return enemySpawnerSet;
    }

    public Set<CustomRideableCreature> getRideableCreatures() {
        return rideableCreatures;
    }

    /**
     * Get the rideable creature by its type
     *
     * @param type the tyoe
     * @return the rideable creature
     */
    public Optional<CustomRideableCreature> getRideableCreatureByName(CustomRideableCreature.RideableType type) {
        return rideableCreatures.stream()
                .filter(creature -> creature.getRideableType().equals(type))
                .findFirst();
    }

    /**
     * Get the enemy spawner by its name
     *
     * @param name the name
     * @return the enemy spawner
     */
    public Optional<EnemySpawner> getSpawnerByName(String name) {
        return enemySpawnerSet.stream()
                .filter(enemySpawner -> enemySpawner.getName().equals(name))
                .findFirst();
    }
}
