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
import org.bukkit.entity.Vindicator;
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
public class NormalVindicator implements SimpleEnemySpawner {

    private final Main plugin;

    public NormalVindicator(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public int getMinWave() {
        return 10;
    }

    @Override
    public ItemStack getDropItem() {
        return new ItemStack(Material.EMERALD);
    }

    @Override
    public double getSpawnRate(Arena arena, int wave, int phase, int spawnAmount) {
        return Math.min(0.28, 0.06 + wave * 0.01);
    }

    @Override
    public int getFinalAmount(Arena arena, int wave, int phase, int spawnAmount) {
        return Math.max(1, (int) (spawnAmount * 0.35));
    }

    @Override
    public boolean checkPhase(Arena arena, int wave, int phase, int spawnAmount) {
        return true;
    }

    public Creature spawn(Location location) {
        Vindicator vindicator = (Vindicator) VersionUtils.spawnEntity(location, EntityType.VINDICATOR);
        vindicator.getEquipment().setHelmet(new ItemStack(Material.AIR));
        vindicator.getEquipment().setHelmetDropChance(0f);
        vindicator.getEquipment().setChestplate(new ItemStack(Material.AIR));
        vindicator.getEquipment().setChestplateDropChance(0f);
        vindicator.getEquipment().setLeggings(new ItemStack(Material.AIR));
        vindicator.getEquipment().setLeggingsDropChance(0f);
        vindicator.getEquipment().setBoots(new ItemStack(Material.AIR));
        vindicator.getEquipment().setBootsDropChance(0f);

        assert XAttribute.FOLLOW_RANGE.get() != null;
        Objects.requireNonNull(vindicator.getAttribute(XAttribute.FOLLOW_RANGE.get())).setBaseValue(200D);
        vindicator.setRemoveWhenFarAway(false);
        vindicator.setMetadata("PlugilyProjects-VillageDefense-Name", new FixedMetadataValue(plugin, "NormalVindicator"));
        return vindicator;
    }

    @Override
    public String getName() {
        return "NormalVindicator";
    }
}
