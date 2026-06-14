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
import org.bukkit.entity.PiglinBrute;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import plugily.projects.minigamesbox.classic.utils.version.VersionUtils;
import plugily.projects.minigamesbox.classic.utils.version.xseries.XAttribute;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.arena.Arena;
import plugily.projects.villagedefense.arena.managers.spawner.SimpleEnemySpawner;

import java.util.Objects;

public class NormalPiglinBrute implements SimpleEnemySpawner {

    private final Main plugin;

    public NormalPiglinBrute(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public int getMinWave() {
        return 25;
    }

    @Override
    public ItemStack getDropItem() {
        return new ItemStack(Material.GOLD_NUGGET, 2);
    }

    @Override
    public double getSpawnRate(Arena arena, int wave, int phase, int spawnAmount) {
        return Math.min(0.12, 0.03 + wave * 0.003);
    }

    @Override
    public int getFinalAmount(Arena arena, int wave, int phase, int spawnAmount) {
        return wave >= 40 ? 2 : 1;
    }

    @Override
    public int getSpawnWeight(Arena arena, int wave, int phase, int spawnAmount) {
        return 2;
    }

    @Override
    public boolean checkPhase(Arena arena, int wave, int phase, int spawnAmount) {
        return true;
    }

    @Override
    public Creature spawn(Location location) {
        PiglinBrute brute = (PiglinBrute) VersionUtils.spawnEntity(location, EntityType.PIGLIN_BRUTE);
        brute.setAdult();
        brute.setImmuneToZombification(true);
        brute.getEquipment().setHelmet(new ItemStack(Material.AIR));
        brute.getEquipment().setHelmetDropChance(0f);
        brute.getEquipment().setChestplate(new ItemStack(Material.AIR));
        brute.getEquipment().setChestplateDropChance(0f);
        brute.getEquipment().setLeggings(new ItemStack(Material.AIR));
        brute.getEquipment().setLeggingsDropChance(0f);
        brute.getEquipment().setBoots(new ItemStack(Material.AIR));
        brute.getEquipment().setBootsDropChance(0f);
        brute.getEquipment().setItemInMainHand(new ItemStack(Material.GOLDEN_AXE));
        brute.getEquipment().setItemInMainHandDropChance(0F);

        assert XAttribute.FOLLOW_RANGE.get() != null;
        Objects.requireNonNull(brute.getAttribute(XAttribute.FOLLOW_RANGE.get())).setBaseValue(200D);
        brute.setRemoveWhenFarAway(false);
        brute.setMetadata("PlugilyProjects-VillageDefense-Name", new FixedMetadataValue(plugin, "NormalPiglinBrute"));
        return brute;
    }

    @Override
    public String getName() {
        return "NormalPiglinBrute";
    }
}
