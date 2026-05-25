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

import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import plugily.projects.minigamesbox.classic.arena.managers.PluginMapRestorerManager;
import plugily.projects.minigamesbox.classic.utils.version.ServerVersion;
import plugily.projects.minigamesbox.classic.utils.version.xseries.XEntityType;
import plugily.projects.villagedefense.arena.Arena;
import plugily.projects.villagedefense.arena.managers.doors.DoorManager;
import plugily.projects.villagedefense.arena.managers.doors.DoorManagerLegacy;
import plugily.projects.villagedefense.arena.managers.doors.IDoorManager;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Plajer
 * <p>
 * Created at 14.02.2019
 */
public class MapRestorerManager extends PluginMapRestorerManager {

    public final Arena arena;

    IDoorManager doorManager;

    public MapRestorerManager(Arena arena) {
        super(arena);
        this.arena = arena;
        if (ServerVersion.Version.isCurrentEqualOrHigher(ServerVersion.Version.v1_13)) {
            doorManager = new DoorManager(arena);
        } else {
            doorManager = new DoorManagerLegacy(arena);
        }
    }

    @Override
    public void fullyRestoreArena() {
        clearGameEntities();
        super.fullyRestoreArena();
        arena.setWave(1);
        arena.setFinalWaveCompleted(false);
        arena.clearSpawnedBossWaves();
        arena.clearBossBars();
        arena.getSpawnedEntities().clear();
        arena.getDroppedFleshes().clear();

        doorManager.rebuildDoors();
        clearGameEntities();
    }

    public final void clearGameEntities() {
        clearEnemiesFromArena();
        clearGolemsFromArena();
        clearPillagerFromArena();
        clearVillagersFromArena();
        clearWolvesFromArena();
        clearTrackedSpawnedEntities();
        clearNearbyGameEntities();
        clearDroppedEntities();
    }

    public final void clearEnemiesFromArena() {
        arena.getEnemySpawnManager().applyIdle(0);
        List<org.bukkit.entity.LivingEntity> enemies = new ArrayList<>(arena.getEnemies());
        for (org.bukkit.entity.LivingEntity enemy : enemies) {
            arena.removeEnemy(enemy);
            enemy.remove();
        }
        arena.getEnemies().clear();
        arena.getEnemySpawnManager().getEnemyCheckerLocations().clear();
    }

    public final void clearDroppedEntities() {
        for (Entity entity : arena.getPlugin().getBukkitHelper().getNearbyEntities(arena.getStartLocation(), 200)) {
            if (entity.getType() == XEntityType.EXPERIENCE_ORB.get() || entity.getType() == XEntityType.ITEM.get()) {
                entity.remove();
            }
        }
    }

    public final void clearGolemsFromArena() {
        List<IronGolem> ironGolems = new ArrayList<>(arena.getIronGolems());
        ironGolems.forEach(arena::removeIronGolem);
    }

    public final void clearPillagerFromArena() {
        List<Pillager> pillagers = new ArrayList<>(arena.getPillagers());
        pillagers.forEach(arena::removePillager);
    }

    public final void clearVillagersFromArena() {
        arena.getVillagers().forEach(Entity::remove);
        arena.getVillagers().clear();
    }

    public final void clearWolvesFromArena() {
        List<Wolf> wolves = new ArrayList<>(arena.getWolves());
        wolves.forEach(arena::removeWolf);
    }

    private void clearTrackedSpawnedEntities() {
        List<Entity> spawnedEntities = new ArrayList<>(arena.getSpawnedEntities());
        spawnedEntities.forEach(Entity::remove);
        arena.getSpawnedEntities().clear();
    }

    private void clearNearbyGameEntities() {
        for (Entity entity : arena.getPlugin().getBukkitHelper().getNearbyEntities(arena.getStartLocation(), 200)) {
            if (shouldRemoveNearbyEntity(entity)) {
                entity.remove();
            }
        }
    }

    private boolean shouldRemoveNearbyEntity(Entity entity) {
        if (entity == null || entity.isDead()) {
            return false;
        }
        if (arena.getPlayers().contains(entity)) {
            return false;
        }
        if (entity instanceof Villager || entity instanceof Wolf || entity instanceof IronGolem || entity instanceof Pillager) {
            return true;
        }
        if (!(entity instanceof LivingEntity)) {
            return false;
        }
        return entity.hasMetadata("PlugilyProjects-VillageDefense-Name")
                || entity.hasMetadata("PlugilyProjects-VillageDefense-ChickenJockey-Chicken")
                || entity.hasMetadata("PlugilyProjects-VillageDefense-SkeletonHorse")
                || entity.hasMetadata("NormalRaidCaptain");
    }

    public IDoorManager getDoorManager() {
        return doorManager;
    }
}
