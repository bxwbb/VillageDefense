/*
 *  Village Defense - Protect villagers from hordes of zombies
 *  Copyright (c) 2026 Plugily Projects - maintained by Tigerpanzer_02 and contributors
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is 3rd party modified.
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
public class ChickenJockey implements SimpleEnemySpawner, Listener {

    private final Main plugin;

    public ChickenJockey(Main plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public int getMinWave() {
        return 51;
    }

    @Override
    public ItemStack getDropItem() {
        return new ItemStack(Material.ROTTEN_FLESH);
    }

    /**
     * 敌人的生成频率是多少？数值范围在 0.0 到 1.0 之间
     *
     * @param arena       游戏竞技场
     * @param wave        当前波次
     * @param phase       当前阶段
     * @param spawnAmount 系统默认建议的基础生成数量
     * @return 生成概率（double 类型）
     */
    @Override
    public double getSpawnRate(Arena arena, int wave, int phase, int spawnAmount) {
        return 1d / 3;
    }

    /**
     * 获取最终要生成的敌人数量（经过逻辑计算后）
     *
     * @param arena       游戏竞技场
     * @param wave        当前波次
     * @param phase       当前阶段
     * @param spawnAmount 竞技场提供的原始/基础生成数量
     * @return 最终生成数量
     */
    @Override
    public int getFinalAmount(Arena arena, int wave, int phase, int spawnAmount) {
        return spawnAmount;
    }

    /**
     * 检查当前阶段是否可以生成敌人
     *
     * @param arena       游戏竞技场
     * @param wave        当前波次
     * @param phase       当前阶段
     * @param spawnAmount 竞技场建议的原始生成数量
     * @return true=可以生成, false=不能生成
     */
    @Override
    public boolean checkPhase(Arena arena, int wave, int phase, int spawnAmount) {
        return phase == 5 || phase == 10 || phase == 15;
    }

    public LivingEntity spawn(Location location) {
        Chicken chicken = (Chicken) VersionUtils.spawnEntity(location, EntityType.CHICKEN);
        chicken.setRemoveWhenFarAway(false);
        chicken.setMetadata("PlugilyProjects-VillageDefense-ChickenJockey-Chicken", new FixedMetadataValue(plugin, "true"));

        Zombie zombie = (Zombie) VersionUtils.spawnEntity(location, EntityType.ZOMBIE);
        zombie.setBaby();
        zombie.setRemoveWhenFarAway(false);
        zombie.addPassenger(chicken);

        zombie.getEquipment().setHelmet(new ItemStack(Material.AIR));
        zombie.getEquipment().setHelmetDropChance(0f);
        zombie.getEquipment().setChestplate(new ItemStack(Material.AIR));
        zombie.getEquipment().setChestplateDropChance(0f);
        zombie.getEquipment().setLeggings(new ItemStack(Material.AIR));
        zombie.getEquipment().setLeggingsDropChance(0f);
        zombie.getEquipment().setBoots(new ItemStack(Material.AIR));
        zombie.getEquipment().setBootsDropChance(0f);
        zombie.getEquipment().setItemInMainHand(new ItemStack(Material.AIR));
        zombie.getEquipment().setItemInMainHandDropChance(0F);

        Objects.requireNonNull(zombie.getAttribute(XAttribute.FOLLOW_RANGE.get())).setBaseValue(200D);

        zombie.setMetadata("PlugilyProjects-VillageDefense-Name", new FixedMetadataValue(plugin, "ChickenJockey"));

        return zombie;
    }

    /**
     * 监听小僵尸死亡 → 坐骑小鸡也一同死亡
     */
    @EventHandler
    public void onSkeletonDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead instanceof Skeleton)) {
            return;
        }

        Skeleton skeleton = (Skeleton) dead;
        for (Entity passenger : skeleton.getPassengers()) {
            if (passenger instanceof SkeletonHorse) {
                SkeletonHorse horse = (SkeletonHorse) passenger;
                if (!horse.hasMetadata("PlugilyProjects-VillageDefense-SkeletonHorse")) {
                    continue;
                }

                horse.setTamed(true);
                horse.setRemoveWhenFarAway(false);
                horse.setAgeLock(false);
                horse.setCustomName("§7骷髅马");
                horse.setCustomNameVisible(true);
                horse.setAI(true);
            }
        }
    }

    /**
     * Get the name of the spawner
     *
     * @return the name
     */
    @Override
    public String getName() {
        return "ChickenJockey";
    }
}