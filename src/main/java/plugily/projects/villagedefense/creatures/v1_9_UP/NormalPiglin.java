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
import org.bukkit.entity.Piglin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import plugily.projects.minigamesbox.classic.utils.version.VersionUtils;
import plugily.projects.minigamesbox.classic.utils.version.xseries.XAttribute;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.arena.Arena;
import plugily.projects.villagedefense.arena.managers.spawner.SimpleEnemySpawner;

import java.util.Objects;
import java.util.Random;

public class NormalPiglin implements SimpleEnemySpawner {

    private final Main plugin;
    private final Random random = new Random();

    public NormalPiglin(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public int getMinWave() {
        return 25;
    }

    @Override
    public ItemStack getDropItem() {
        return new ItemStack(Material.GOLD_NUGGET);
    }

    @Override
    public double getSpawnRate(Arena arena, int wave, int phase, int spawnAmount) {
        return Math.min(0.28, 0.08 + wave * 0.006);
    }

    @Override
    public int getFinalAmount(Arena arena, int wave, int phase, int spawnAmount) {
        return Math.max(1, (int) (spawnAmount * 0.35));
    }

    @Override
    public boolean checkPhase(Arena arena, int wave, int phase, int spawnAmount) {
        return true;
    }

    @Override
    public Creature spawn(Location location) {
        Piglin piglin = (Piglin) VersionUtils.spawnEntity(location, EntityType.PIGLIN);
        piglin.setAdult();
        piglin.setImmuneToZombification(true);
        piglin.getEquipment().setHelmet(new ItemStack(Material.AIR));
        piglin.getEquipment().setHelmetDropChance(0f);
        piglin.getEquipment().setChestplate(new ItemStack(Material.AIR));
        piglin.getEquipment().setChestplateDropChance(0f);
        piglin.getEquipment().setLeggings(new ItemStack(Material.AIR));
        piglin.getEquipment().setLeggingsDropChance(0f);
        piglin.getEquipment().setBoots(new ItemStack(Material.AIR));
        piglin.getEquipment().setBootsDropChance(0f);
        piglin.getEquipment().setItemInMainHand(new ItemStack(random.nextBoolean() ? Material.GOLDEN_SWORD : Material.CROSSBOW));
        piglin.getEquipment().setItemInMainHandDropChance(0F);

        assert XAttribute.FOLLOW_RANGE.get() != null;
        Objects.requireNonNull(piglin.getAttribute(XAttribute.FOLLOW_RANGE.get())).setBaseValue(200D);
        piglin.setRemoveWhenFarAway(false);
        piglin.setMetadata("PlugilyProjects-VillageDefense-Name", new FixedMetadataValue(plugin, "NormalPiglin"));
        return piglin;
    }

    @Override
    public String getName() {
        return "NormalPiglin";
    }
}
