
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
import plugily.projects.minigamesbox.classic.utils.version.VersionUtils;
import plugily.projects.minigamesbox.classic.utils.version.xseries.XAttribute;
import plugily.projects.villagedefense.arena.Arena;
import plugily.projects.villagedefense.arena.managers.spawner.SimpleEnemySpawner;

/**
 * @author Tigerpanzer_02
 * <p>
 * Created at 15.01.2022
 */
public class NormalZombie implements SimpleEnemySpawner {

    public NormalZombie() {
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
        if (wave <= 8) {
            return 1;
        } else {
            return 1d / 3;
        }
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
        if (wave <= 3) {
            return wave * 2;
        } else {
            return spawnAmount;
        }
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
        return true;
    }

    public Creature spawn(Location location) {
        Zombie zombie = (Zombie) VersionUtils.spawnEntity(location, EntityType.ZOMBIE);
        zombie.getEquipment().setHelmet(new ItemStack(Material.AIR));
        zombie.getEquipment().setHelmetDropChance(0f);
        zombie.getEquipment().setChestplate(new ItemStack(Material.AIR));
        zombie.getEquipment().setChestplateDropChance(0f);
        zombie.getEquipment().setLeggings(new ItemStack(Material.AIR));
        zombie.getEquipment().setLeggingsDropChance(0f);
        zombie.getEquipment().setBoots(new ItemStack(Material.AIR));
        zombie.getEquipment().setBootsDropChance(0f);
        zombie.getEquipment().setItemInMainHand(new ItemStack(Material.STICK));
        zombie.getEquipment().setItemInMainHandDropChance(0F);
        assert XAttribute.FOLLOW_RANGE.get() != null;
//        VersionUtils.setMaxHealth(zombie, 70);
//        zombie.setHealth(70);
        zombie.setRemoveWhenFarAway(false);
//        zombie.setMetadata("PlugilyProjects-VillageDefense-Name", new FixedMetadataValue(plugin, key));
        return zombie;
    }

    /**
     * Get the name of the spawner
     *
     * @return the name
     */
    @Override
    public String getName() {
        return "普通僵尸";
    }
}
