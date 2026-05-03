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
import org.bukkit.entity.Creature;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Evoker;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import plugily.projects.minigamesbox.classic.utils.version.VersionUtils;
import plugily.projects.minigamesbox.classic.utils.version.xseries.XAttribute;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.arena.Arena;
import plugily.projects.villagedefense.arena.managers.spawner.SimpleEnemySpawner;

import java.util.Objects;

/**
 * @author Tigerpanzer_02
 * <p>
 * Created at 15.01.2022
 */
public class NormalEvoker implements SimpleEnemySpawner {

    private final Main plugin;

    public NormalEvoker(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public ItemStack getDropItem() {
        return new ItemStack(Material.TOTEM_OF_UNDYING);
    }

    @Override
    public double getSpawnRate(Arena arena, int wave, int phase, int spawnAmount) {
        if (wave < 10) {
            return 0.0;
        }
        return Math.min(0.25, 0.1 + wave * 0.015);
    }

    @Override
    public int getFinalAmount(Arena arena, int wave, int phase, int spawnAmount) {
        return 1 + (wave >= 15 ? 1 : 0);
    }

    @Override
    public boolean checkPhase(Arena arena, int wave, int phase, int spawnAmount) {
        return true;
    }

    public Creature spawn(Location location) {
        Evoker evoker = (Evoker) VersionUtils.spawnEntity(location, EntityType.EVOKER);
        evoker.getEquipment().setHelmet(new ItemStack(Material.AIR));
        evoker.getEquipment().setHelmetDropChance(0f);
        evoker.getEquipment().setChestplate(new ItemStack(Material.AIR));
        evoker.getEquipment().setChestplateDropChance(0f);
        evoker.getEquipment().setLeggings(new ItemStack(Material.AIR));
        evoker.getEquipment().setLeggingsDropChance(0f);
        evoker.getEquipment().setBoots(new ItemStack(Material.AIR));
        evoker.getEquipment().setBootsDropChance(0f);

        assert XAttribute.FOLLOW_RANGE.get() != null;
        Objects.requireNonNull(evoker.getAttribute(XAttribute.FOLLOW_RANGE.get())).setBaseValue(200D);
        evoker.setRemoveWhenFarAway(false);
        evoker.setMetadata("PlugilyProjects-VillageDefense-Name", new FixedMetadataValue(plugin, "NormalEvoker"));
        return evoker;
    }

    @Override
    public String getName() {
        return "NormalEvoker";
    }
}