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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
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
public class SkeletonHorseJockey implements SimpleEnemySpawner, Listener {

    private final Main plugin;

    public SkeletonHorseJockey(Main plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public int getMinWave() {
        return 60;
    }

    @Override
    public ItemStack getDropItem() {
        return new ItemStack(Material.BONE);
    }

    @Override
    public double getSpawnRate(Arena arena, int wave, int phase, int spawnAmount) {
        return 1d / 3;
    }

    @Override
    public int getFinalAmount(Arena arena, int wave, int phase, int spawnAmount) {
        return spawnAmount;
    }

    @Override
    public boolean checkPhase(Arena arena, int wave, int phase, int spawnAmount) {
        return phase == 5 || phase == 10 || phase == 15;
    }

    public LivingEntity spawn(Location location) {
        SkeletonHorse skeletonHorse = (SkeletonHorse) VersionUtils.spawnEntity(location, EntityType.SKELETON_HORSE);
        skeletonHorse.setRemoveWhenFarAway(false);
        skeletonHorse.setTamed(false);
        skeletonHorse.setAgeLock(true);
        skeletonHorse.setMetadata("PlugilyProjects-VillageDefense-SkeletonHorse", new FixedMetadataValue(plugin, "true"));

        Skeleton skeleton = (Skeleton) VersionUtils.spawnEntity(location, EntityType.SKELETON);
        skeleton.setRemoveWhenFarAway(false);
        skeleton.addPassenger(skeletonHorse);

        skeleton.getEquipment().setHelmet(new ItemStack(Material.AIR));
        skeleton.getEquipment().setHelmetDropChance(0f);
        skeleton.getEquipment().setChestplate(new ItemStack(Material.AIR));
        skeleton.getEquipment().setChestplateDropChance(0f);
        skeleton.getEquipment().setLeggings(new ItemStack(Material.AIR));
        skeleton.getEquipment().setLeggingsDropChance(0f);
        skeleton.getEquipment().setBoots(new ItemStack(Material.AIR));
        skeleton.getEquipment().setBootsDropChance(0f);
        skeleton.getEquipment().setItemInMainHand(new ItemStack(Material.AIR));
        skeleton.getEquipment().setItemInMainHandDropChance(0F);

        Objects.requireNonNull(skeleton.getAttribute(XAttribute.FOLLOW_RANGE.get())).setBaseValue(200D);

        skeleton.setMetadata("PlugilyProjects-VillageDefense-Name", new FixedMetadataValue(plugin, "SkeletonHorseJockey"));

        return skeleton;
    }

    /**
     * 监听骷髅死亡 -> 让骷髅马变可骑乘/驯服/友好
     */
    @EventHandler
    public void onSkeletonDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead instanceof Skeleton)) return;

        Skeleton skeleton = (Skeleton) dead;
        for (Entity passenger : skeleton.getPassengers()) {
            if (passenger instanceof SkeletonHorse) {
                SkeletonHorse horse = (SkeletonHorse) passenger;
                if (!horse.hasMetadata("PlugilyProjects-VillageDefense-SkeletonHorse")) continue;
                horse.setTamed(true);
                horse.setRemoveWhenFarAway(false);
                horse.setAgeLock(false);
                horse.setCustomName("§7骷髅马");
                horse.setCustomNameVisible(true);
                horse.setAI(true);
            }
        }
    }

    @Override
    public String getName() {
        return "SkeletonHorseJockey";
    }
}