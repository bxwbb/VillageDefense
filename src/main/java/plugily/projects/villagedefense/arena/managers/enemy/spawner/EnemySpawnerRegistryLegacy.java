
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

import plugily.projects.minigamesbox.classic.utils.version.ServerVersion;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.arena.Arena;
import plugily.projects.villagedefense.arena.managers.spawner.EnemySpawner;
import plugily.projects.villagedefense.creatures.v1_9_UP.CustomRideableCreature;
import plugily.projects.villagedefense.creatures.v1_9_UP.NormalZombie;

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
        enemySpawnerSet.add(new NormalZombie());
    }

    /**
     * 在竞技场内生成敌人（僵尸）
     *
     * @param random 随机数实例
     * @param arena  目标竞技场
     */
    public void spawnEnemies(Random random, Arena arena) {
        int spawn = arena.getWave();
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

        // 打乱生成顺序
        Collections.shuffle(enemySpawners);
        for (EnemySpawner enemySpawner : enemySpawners) {
            // spawner 内部会按波次、权重和剩余数量决定是否真正生成。
            plugin.getDebugger().debug("Trying enemy spawn for " + enemySpawner.getName());
            System.out.println("生成 : " + enemySpawner.getClass());
            enemySpawner.spawn(random, arena, spawn);
        }

        if (wave % 10 == 0) {
            // 其他波次生成
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
