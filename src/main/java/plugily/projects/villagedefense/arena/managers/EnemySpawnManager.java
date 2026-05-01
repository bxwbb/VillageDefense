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

package plugily.projects.villagedefense.arena.managers;

import org.bukkit.Location;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Villager;
import plugily.projects.minigamesbox.classic.utils.version.VersionUtils;
import plugily.projects.villagedefense.arena.Arena;

import java.util.*;

/**
 * 单个 Arena 的刷怪节流和卡怪检测。
 *
 * <p>具体生成哪类敌人由 EnemySpawnerRegistry 决定；本类只负责何时允许刷怪，
 * 以及已经生成的敌人是否需要传送或清理。</p>
 *
 * @author Plajer
 * <p>
 * Created at 06.01.2019
 */
public class EnemySpawnManager {
    private final Arena arena;
    private int localIdleProcess = 0;
    private final List<Creature> glitchedEnemies = new ArrayList<>();
    private final Map<Creature, Location> enemyCheckerLocations = new HashMap<>();

    public EnemySpawnManager(Arena arena) {
        this.arena = arena;
    }

    public void applyIdle(int idle) {
        // idle 表示接下来多少次主循环跳过刷怪，用于高波次削峰。
        localIdleProcess = idle;
    }

    /**
     * 递增 ZOMBIE_GLITCH_CHECKER（僵尸BUG检测计数器）的值，并尝试检测
     * 当该计数器数值 ≥ 60 时，是否有怪物卡在重生点上
     * <p>
     * BUG检测程序同时会清理竞技场内已死亡的怪物和村民实体
     */
    public void spawnGlitchCheck() {
        arena.changeArenaOptionBy("ZOMBIE_GLITCH_CHECKER", 1);
        if (arena.getArenaOption("ZOMBIE_GLITCH_CHECKER") >= 60) {
            // 定期清理集合中已死亡的村民，防止胜负判断读到旧引用。
            Iterator<Villager> villagerIterator = arena.getVillagers().iterator();
            while (villagerIterator.hasNext()) {
                Villager villager = villagerIterator.next();
                if (villager.isDead()) {
                    villagerIterator.remove();
                    arena.removeVillager(villager);
                }
            }
            arena.setArenaOption("ZOMBIE_GLITCH_CHECKER", 0);

            Iterator<Creature> creatureIterator = arena.getEnemies().iterator();
            while (creatureIterator.hasNext()) {
                Creature creature = creatureIterator.next();
                if (creature.isDead()) {
                    // Bukkit 实体已死亡时同步 Arena 集合。
                    creatureIterator.remove();
                    arena.removeEnemy(creature);
                    continue;
                }
                if (glitchedEnemies.contains(creature) && creature.getLocation().distance(enemyCheckerLocations.get(creature)) <= 1) {
                    // 第二次仍未移动，认为卡死，直接移除以避免波次无法结束。
                    creatureIterator.remove();
                    arena.removeEnemy(creature);
                    enemyCheckerLocations.remove(creature);
                    creature.remove();
                }

                Location checkerLoc = enemyCheckerLocations.get(creature);
                if (checkerLoc == null) {
                    // 第一次记录位置，下一次检测再判断是否移动。
                    enemyCheckerLocations.put(creature, creature.getLocation());
                } else if (creature.getLocation().distance(checkerLoc) <= 1) {
                    // 第一次疑似卡住时先传回随机刷怪点，给 AI 一次恢复机会。
                    VersionUtils.teleport(creature, arena.getRandomZombieSpawnLocation(arena.getPlugin().getRandom()));
                    enemyCheckerLocations.put(creature, creature.getLocation());
                    glitchedEnemies.add(creature);
                }
            }
        }
    }

    public Map<Creature, Location> getEnemyCheckerLocations() {
        return enemyCheckerLocations;
    }

    /**
     * 在竞技场内生成一批敌人（僵尸）。
     * <p>
     * 敌人的种类与数量取决于
     * 随机值与当前波次等级
     */
    public void spawnEnemies() {
        if (checkForIdle()) {
            // Registry 会按版本和波次把生成尝试分发给各个 EnemySpawner。
            arena.getPlugin().getEnemySpawnerRegistry().spawnEnemies(arena.getPlugin().getRandom(), arena);
        }
    }

    private boolean checkForIdle() {
        //Idling to ~~save server stability~~ protect against hordes of enemies
        if (localIdleProcess > 0) {
            localIdleProcess--;
            return false;
        }

        applyIdle(arena.getArenaOption("ZOMBIE_IDLE_PROCESS"));
        // idle 归零，本轮可以刷怪，并重置下一轮 idle。
        return true;
    }

}
