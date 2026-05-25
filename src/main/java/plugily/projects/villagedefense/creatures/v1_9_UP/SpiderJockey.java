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
public class SpiderJockey implements SimpleEnemySpawner {

    private final Main plugin;

    public SpiderJockey(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public int getMinWave() {
        return 55;
    }

    @Override
    public ItemStack getDropItem() {
        return new ItemStack(Material.STRING);
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
        Spider spider = (Spider) VersionUtils.spawnEntity(location, EntityType.SPIDER);
        spider.setRemoveWhenFarAway(false);

        Skeleton skeleton = (Skeleton) VersionUtils.spawnEntity(location, EntityType.SKELETON);
        skeleton.setRemoveWhenFarAway(false);

        skeleton.addPassenger(spider);

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

        skeleton.setMetadata("PlugilyProjects-VillageDefense-Name", new FixedMetadataValue(plugin, "SpiderJockey"));

        return skeleton;
    }

    /**
     * Get the name of the spawner
     *
     * @return the name
     */
    @Override
    public String getName() {
        return "SpiderJockey";
    }
}