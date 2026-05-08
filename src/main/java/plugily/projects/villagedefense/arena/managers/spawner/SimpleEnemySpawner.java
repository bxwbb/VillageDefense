
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

package plugily.projects.villagedefense.arena.managers.spawner;

import org.bukkit.Location;
import org.bukkit.entity.Creature;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import plugily.projects.minigamesbox.classic.utils.version.VersionUtils;
import plugily.projects.minigamesbox.classic.utils.version.xseries.XAttribute;
import plugily.projects.villagedefense.arena.Arena;
import plugily.projects.villagedefense.creatures.CreatureUtils;

import java.util.Random;

/**
 * The interface for simple enemy spawner
 */
public interface SimpleEnemySpawner extends EnemySpawner {
    /**
     * Get the minimum wave to spawn the enemies
     *
     * @return the wave
     */
    default int getMinWave() {
        return 1;
    }

    /**
     * Get the maximum wave to spawn the enemies (stop spawning when exceeding this value)
     *
     * @return the wave
     */
    default int getMaxWave() {
        return -1;
    }

    /**
     * Can the enemies be applied some holiday effects?
     *
     * @return true if they can
     */
    default boolean canApplyHolidayEffect() {
        return false;
    }

    /**
     * Can the enemies be applied arena attributes?
     *
     * @return true if they can
     */
    default boolean canApplyAttributes() {
        return true;
    }

    /**
     * How often the enemies will be spawned? Amount between 0.0 and 1.0
     *
     * @param arena       the arena
     * @param wave        the current wave
     * @param phase       the current phase
     * @param spawnAmount the raw amount that the arena suggests
     * @return the spawn rate in double
     */
    double getSpawnRate(Arena arena, int wave, int phase, int spawnAmount);

    /**
     * Get the final amount of enemies to spawn, after some workaround
     *
     * @param arena       the arena
     * @param wave        the current wave
     * @param phase       the current phase
     * @param spawnAmount the raw amount that the arena suggests
     * @return the final amount
     */
    int getFinalAmount(Arena arena, int wave, int phase, int spawnAmount);

    /**
     * Check if the enemies can be spawned on this phase
     *
     * @param arena       the arena
     * @param wave        the current wave
     * @param phase       the current phase
     * @param spawnAmount the raw amount that the arena suggests
     * @return true if they can
     */
    boolean checkPhase(Arena arena, int wave, int phase, int spawnAmount);

    /**
     * Spawn the enemy at the location
     *
     * @param location the location
     * @return the spawned enemy
     */
    @Nullable
    LivingEntity spawn(Location location);

    /**
     * Get the weight of the enemy in the arena.
     * Basically mean this enemy is worth how many normal enemies in the arena.
     *
     * @param arena       the arena
     * @param wave        the current wave
     * @param phase       the current phase
     * @param spawnAmount the raw amount that the arena suggests
     * @return the weight of the enemy
     */
    default int getSpawnWeight(Arena arena, int wave, int phase, int spawnAmount) {
        return 1;
    }

    /**
     * Spawn the enemy at the location of the arena.
     *
     * @param location the location
     * @param arena    the arena
     */
    default void spawn(Location location, Arena arena) {
        LivingEntity livingEntity = spawn(location);
        if (livingEntity == null) {
            return;
        }
        VersionUtils.setMaxHealth(livingEntity, 10.0d + (arena.getWave() * 4));
        livingEntity.setHealth(10.0d + (arena.getWave() * 4));
        livingEntity.getAttribute(XAttribute.ATTACK_DAMAGE.get()).setBaseValue(5.0d + arena.getWave() * 2);
        if (livingEntity instanceof Creature creature) {
            if (canApplyAttributes()) {
                CreatureUtils.applyAttributes(creature, arena);
            }
            if (canApplyHolidayEffect()) {
                arena.getPlugin().getHolidayManager().applyHolidayCreatureEffects(creature);
            }
        }
        arena.getEnemies().add(livingEntity);
    }

    //TODO Simplify creature spawn reduce to one method e.g. spawn; add weight to creatures configurable!
    /**
     * 怪物生成的核心逻辑（默认实现）
     * 随机判断、波次判断、数量计算、概率计算、最终执行生成
     * @param random 随机数工具
     * @param arena  当前游戏竞技场
     * @param spawn  第几个刷新点/刷新序号
     */
    @Override
    default void spawn(Random random, Arena arena, int spawn) {
        // 获取当前波次 & 当前阶段
        int wave = arena.getWave();
        int phase = arena.getArenaOption("ZOMBIE_SPAWN_COUNTER");

        // 调试输出：当前波次、阶段、刷新序号、阶段检查结果
        arena.getPlugin().getDebugger().debug("Current Wave: " + wave + " Current Phase: " + phase + " Current spawn: " + spawn + " CHECK PHASE: " + checkPhase(arena, wave, phase, spawn));

        // —————————————————— 1. 检查是否符合生成阶段 ——————————————————
        // 如果不符合当前阶段条件 → 直接退出，不生成
        if (!checkPhase(arena, wave, phase, spawn)) {
            return;
        }

        // —————————————————— 2. 检查是否在允许生成的波次范围内 ——————————————————
        int maxWave = getMaxWave();
        arena.getPlugin().getDebugger().debug("Current Wave: " + wave + " Max wave: " + maxWave + " CHECK WAVE: " + (wave < getMinWave() || (maxWave > 0 && wave > maxWave)));

        // 如果当前波次 < 最小允许波次，或 > 最大允许波次 → 不生成
        if (wave < getMinWave() || (maxWave > 0 && wave > maxWave)) {
            return;
        }

        // —————————————————— 3. 计算最终生成数据 ——————————————————
        int spawnAmount = getFinalAmount(arena, wave, phase, spawn);   // 最终要生成的数量
        double spawnRate = getSpawnRate(arena, wave, phase, spawn);    // 生成概率（0-1）
        int weight = getSpawnWeight(arena, wave, phase, spawn);        // 生成权重（消耗的配额）

        // 调试输出
        arena.getPlugin().getDebugger().debug("Current Wave: " + wave + " Current Spawn amount: " + spawnAmount + " Current spawnRate: " + spawnRate + " Current Spawn Weight: " + weight);

        // —————————————————— 4. 循环尝试生成怪物 ——————————————————
        for (int i = 0; i < spawnAmount; i++) {
            // 获取当前剩余可生成怪物配额
            int zombiesToSpawn = arena.getArenaOption("ZOMBIES_TO_SPAWN");

            // 调试输出
            arena.getPlugin().getDebugger().debug("Current Wave: " + wave + " Current Spawn amount: " + spawnAmount + " Current i: " + i + " CHECK SPAWN: " + (zombiesToSpawn >= weight && spawnRate != 0 && (spawnRate == 1 || random.nextDouble() < spawnRate)));

            // —————————————————— 生成条件判断 ——————————————————
            // 1. 剩余生成配额足够消耗
            // 2. 生成概率不等于0
            // 3. 概率=1（必刷） 或 随机数小于概率（概率生成）
            if (zombiesToSpawn >= weight && spawnRate != 0 && (spawnRate == 1 || random.nextDouble() < spawnRate)) {

                // 随机获取一个僵尸刷新点
                // 前期不随机
                Location location;
                if (wave <= 5) {
                    location = arena.getZombieSpawns().getFirst();
                } else {
                    location = arena.getRandomZombieSpawnLocation(random);
                }

                // —————————— 真正生成怪物的方法 ——————————
                spawn(location, arena);

                // 生成后扣除对应权重配额
                arena.setArenaOption("ZOMBIES_TO_SPAWN", zombiesToSpawn - weight);
            }
        }
    }
}
