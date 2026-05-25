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
import org.bukkit.entity.Witch;
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
public class NormalWitch implements SimpleEnemySpawner {

    private final Main plugin;

    public NormalWitch(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public ItemStack getDropItem() {
        return new ItemStack(Material.GLASS_BOTTLE);
    }

    @Override
    public double getSpawnRate(Arena arena, int wave, int phase, int spawnAmount) {
        if (wave < 7) {
            return 0.0;
        }
        return Math.min(0.4, 0.2 + wave * 0.018);
    }

    @Override
    public int getFinalAmount(Arena arena, int wave, int phase, int spawnAmount) {
        return (int) (spawnAmount * 0.4 + wave / 5.0);
    }

    @Override
    public boolean checkPhase(Arena arena, int wave, int phase, int spawnAmount) {
        return true;
    }

    public Creature spawn(Location location) {
        Witch witch = (Witch) VersionUtils.spawnEntity(location, EntityType.WITCH);
        witch.getEquipment().setHelmet(new ItemStack(Material.AIR));
        witch.getEquipment().setHelmetDropChance(0f);
        witch.getEquipment().setChestplate(new ItemStack(Material.AIR));
        witch.getEquipment().setChestplateDropChance(0f);
        witch.getEquipment().setLeggings(new ItemStack(Material.AIR));
        witch.getEquipment().setLeggingsDropChance(0f);
        witch.getEquipment().setBoots(new ItemStack(Material.AIR));
        witch.getEquipment().setBootsDropChance(0f);

        assert XAttribute.FOLLOW_RANGE.get() != null;
        Objects.requireNonNull(witch.getAttribute(XAttribute.FOLLOW_RANGE.get())).setBaseValue(200D);
        witch.setRemoveWhenFarAway(false);
        witch.setMetadata("PlugilyProjects-VillageDefense-Name", new FixedMetadataValue(plugin, "NormalWitch"));
        return witch;
    }

    @Override
    public String getName() {
        return "NormalWitch";
    }
}