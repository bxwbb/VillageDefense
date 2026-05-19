
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

import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.metadata.MetadataValue;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.arena.Arena;
import plugily.projects.villagedefense.arena.managers.spawner.EnemySpawner;
import plugily.projects.villagedefense.creatures.v1_9_UP.CustomCreature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * @author Tigerpanzer_02
 * <p>
 * Created at 22.01.2022
 */
public class CreatureTargetManager {
    private final Arena arena;
    private final Main plugin;

    public CreatureTargetManager(Arena arena) {
        this.arena = arena;
        this.plugin = arena.getPlugin();
    }

    public void targetCreatures() {
        for (LivingEntity livingEntity : arena.getEnemies()) {
            if (livingEntity instanceof Creature creature) {
                LivingEntity creatureTarget = creature.getTarget();
                if (creatureTarget == null || !isAllowedEnemyTarget(creatureTarget)) {
                    setTarget(creature);
                    continue;
                }
                if (creatureTarget.getLocation().distance(creature.getLocation()) > 10 && creatureTarget instanceof Player player && !player.getGameMode().equals(GameMode.SPECTATOR)) {
                    setTarget(creature);
                }
            }
        }
        for (Pillager pillager : arena.getPillagers()) {
            List<LivingEntity> enemies = arena.getEnemies();
            if (enemies.isEmpty()) pillager.setTarget(null);
            enemies.stream()
                    .filter(enemy -> enemy != null && !enemy.isDead())
                    .min(Comparator.comparingDouble(
                            e -> e.getLocation().distanceSquared(pillager.getLocation())
                    )).ifPresent(pillager::setTarget);
        }
    }

    public void targetRideableCreatures() {
        List<Creature> creatures = new ArrayList<>();
        creatures.addAll(arena.getWolves());
        creatures.addAll(arena.getIronGolems());
        creatures.addAll(arena.getPillagers());
        for (Creature creature : creatures) {
            plugin.getServer().getMobGoals().removeAllGoals(creature, GoalType.TARGET);
            if (arena.getEnemies().isEmpty() || !arena.isFighting()) {
                creature.setTarget(null);
                return;
            }
//            LivingEntity creatureTarget = creature.getTarget();
//            if (creatureTarget == null) {
//                creature.setTarget(arena.getEnemies().get(arena.getEnemies().size() > 1 ? plugin.getRandom().nextInt(arena.getEnemies().size() - 1) : 0));
//                continue;
//            }
//            if (creatureTarget instanceof Player) {
//                creature.setTarget(arena.getEnemies().get(arena.getEnemies().size() > 1 ? plugin.getRandom().nextInt(arena.getEnemies().size() - 1) : 0));
//            }
            creature.setTarget(arena.getEnemies().get(arena.getEnemies().size() > 1 ? plugin.getRandom().nextInt(arena.getEnemies().size() - 1) : 0));
        }
    }

    private void setTarget(Creature creature) {
        LivingEntity nearestEntity = getNearestEntity(creature);
        if (nearestEntity == null) {
            creature.setTarget(null);
            return;
        }
        creature.setTarget(nearestEntity);
        plugin.getDebugger().debug("Arena {0} set Target {1} for Entity at Location {2}", arena.getId(), nearestEntity.getType(), creature.getLocation().toString());
    }

    public void unTargetCreature(Creature creature) {
        creature.setTarget(null);
    }

    public CustomCreature getCustomCreatureFromCreature(Creature creature) {
        List<MetadataValue> metadataValueList = creature.getMetadata("PlugilyProjects-VillageDefense-Name");
        if (metadataValueList.isEmpty()) {
            plugin.getDebugger().debug("Arena {0} Couldn't find creature meta data", arena.getId());
            return null;
        }
        for (MetadataValue metadataValue : metadataValueList) {
            Optional<EnemySpawner> spawnerByName = plugin.getEnemySpawnerRegistry().getSpawnerByName(metadataValue.asString());
            if (spawnerByName.isEmpty()) {
                continue;
            }
            EnemySpawner enemySpawner = spawnerByName.get();
            if (enemySpawner instanceof CustomCreature) {
                return (CustomCreature) enemySpawner;
            }
        }
        plugin.getDebugger().debug("Arena {0} Couldn't find creature spawner", arena.getId());
        return null;
    }

    /**
     * 获取生物应该攻击的最近目标实体
     * 敌人只允许选择本局村民或存活玩家，避免怪物互相误伤后转仇恨。
     *
     * @param creature 要设置目标的生物
     * @return 最近的合法目标实体（LivingEntity），无目标返回null
     */
    public LivingEntity getNearestEntity(Creature creature) {

        // 获取当前生物所在的位置，用于计算距离
        Location location = creature.getLocation();

        // 存储候选目标实体列表
        List<Entity> entities = new ArrayList<>();
        entities.addAll(arena.getVillagers());
        entities.addAll(arena.getPlayersLeft());

        // 如果依然没有任何目标，返回null
        if (entities.isEmpty()) {
            plugin.getDebugger().debug("Arena {0} found no entity to target", arena.getId());
            return null;
        }

        // 初始化最近目标为列表第一个实体
        Entity nearestEntity = entities.get(0);

        // 遍历所有候选目标，找到【离生物最近】的实体
        for (Entity entity : entities) {
            // 当前生物到候选实体的距离
            double distance = location.distance(entity.getLocation());
            // 如果比当前记录的最近实体更近 → 更新最近目标
            if (distance < location.distance(nearestEntity.getLocation())) {
                nearestEntity = entity;
            }
        }

        // 调试输出：找到的目标位置、生物位置、距离
        plugin.getDebugger().debug("Arena {0} found at {1} the nearest villager for creature at {2} with distance of {3}",
                arena.getId(),
                nearestEntity.getLocation(),
                creature.getLocation(),
                location.distance(nearestEntity.getLocation()));

        // 返回找到的最近目标（强制转为 LivingEntity）
        return (LivingEntity) nearestEntity;
    }

    private boolean isAllowedEnemyTarget(LivingEntity target) {
        if (target instanceof Villager villager) {
            return arena.getVillagers().contains(villager);
        }
        if (target instanceof Player player) {
            return arena.getPlayersLeft().contains(player);
        }
        return false;
    }

    public void unTargetPlayerFromZombies(Player player, Arena arena) {
        for (LivingEntity zombie : arena.getEnemies()) {
            if (zombie instanceof Creature creature) {
                LivingEntity target = creature.getTarget();

                if (!player.equals(target)) {
                    continue;
                }
                //set new target so zombies won't stay still waiting for nothing
                creature.setTarget(null);
                setTarget(creature);
            }
        }
    }

}
