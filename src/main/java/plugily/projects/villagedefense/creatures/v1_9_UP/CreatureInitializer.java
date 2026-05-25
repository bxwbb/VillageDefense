
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

package plugily.projects.villagedefense.creatures.v1_9_UP;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import plugily.projects.minigamesbox.classic.utils.version.VersionUtils;
import plugily.projects.villagedefense.creatures.BaseCreatureInitializer;
import plugily.projects.villagedefense.creatures.CreatureUtils;

public class CreatureInitializer implements BaseCreatureInitializer {

    @Override
    public Villager spawnVillager(Location location) {
        LivingEntity livingEntity = CreatureUtils.getPlugin().getEnemySpawnerRegistry().getRideableCreatureByName(CustomRideableCreature.RideableType.VILLAGER).get().spawn(location);
        if (livingEntity instanceof Villager) {
            return (Villager) livingEntity;
        }
        throw new ClassCastException("Villager livingEntity isn't a villager");
    }

    @Override
    public Wolf spawnWolf(Location location) {
        LivingEntity livingEntity = CreatureUtils.getPlugin().getEnemySpawnerRegistry().getRideableCreatureByName(CustomRideableCreature.RideableType.WOLF).get().spawn(location);
        if (livingEntity instanceof Wolf) {
            return (Wolf) livingEntity;
        }
        throw new ClassCastException("Wolf livingEntity isn't a wolf");
    }

    @Override
    public IronGolem spawnGolem(Location location) {
        LivingEntity livingEntity = CreatureUtils.getPlugin().getEnemySpawnerRegistry().getRideableCreatureByName(CustomRideableCreature.RideableType.IRON_GOLEM).get().spawn(location);
        if (livingEntity instanceof IronGolem) {
            return (IronGolem) livingEntity;
        }
        throw new ClassCastException("IronGolem livingEntity isn't a iron golem");
    }

    @Override
    public Pillager spawnPillager(Location location) {
        LivingEntity livingEntity = CreatureUtils.getPlugin().getEnemySpawnerRegistry().getRideableCreatureByName(CustomRideableCreature.RideableType.PILLAGER).get().spawn(location);
        if (livingEntity instanceof Pillager) {
            return (Pillager) livingEntity;
        }
        throw new ClassCastException("Pillager livingEntity isn't a pillager");
    }
}
