
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import plugily.projects.minigamesbox.classic.utils.version.VersionUtils;
import plugily.projects.minigamesbox.classic.utils.version.xseries.XAttribute;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.arena.Arena;
import plugily.projects.villagedefense.arena.managers.spawner.SimpleEnemySpawner;

import java.util.Objects;
import java.util.Random;

/**
 * @author Tigerpanzer_02
 * <p>
 * Created at 15.01.2022
 */
public class NormalZombie implements SimpleEnemySpawner {

    private int wave = -1;
    private final Main plugin;

    public NormalZombie(Main plugin) {
        this.plugin = plugin;
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
        return 1;
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
        this.wave = wave;
        return true;
    }

    public Creature spawn(Location location) {
        Random random = new Random();

        Zombie zombie = (Zombie) VersionUtils.spawnEntity(location, EntityType.ZOMBIE);
        if (wave > 5 && random.nextInt(100) < 30) {
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 10000, wave > 20 ? 2 : 1, true, true));
        }
        zombie.getEquipment().setHelmet(new ItemStack(Material.AIR));
        if (wave > 5 && random.nextInt(100) < 30) {
            if (wave > 50) {
                if (random.nextInt(10) < 7) {
                    zombie.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
                } else {
                    zombie.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
                }
            } else if (wave > 20) {
                if (random.nextInt(10) < 7) {
                    zombie.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
                } else {
                    zombie.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
                }
            } else {
                if (random.nextInt(10) < 7) {
                    zombie.getEquipment().setHelmet(new ItemStack(Material.LEATHER_HELMET));
                } else {
                    zombie.getEquipment().setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
                }
            }
        }
        zombie.getEquipment().setHelmetDropChance(0f);
        zombie.getEquipment().setChestplate(new ItemStack(Material.AIR));
        if (wave > 5 && random.nextInt(100) < 15) {
            if (wave > 50) {
                if (random.nextInt(10) < 7) {
                    zombie.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
                } else {
                    zombie.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                }
            } else if (wave > 20) {
                if (random.nextInt(10) < 7) {
                    zombie.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                } else {
                    zombie.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
                }
            } else {
                if (random.nextInt(10) < 7) {
                    zombie.getEquipment().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
                } else {
                    zombie.getEquipment().setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
                }
            }
        }
        zombie.getEquipment().setChestplateDropChance(0f);
        zombie.getEquipment().setLeggings(new ItemStack(Material.AIR));
        if (wave > 5 && random.nextInt(100) < 15) {
            if (wave > 50) {
                if (random.nextInt(10) < 7) {
                    zombie.getEquipment().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
                } else {
                    zombie.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
                }
            } else if (wave > 20) {
                if (random.nextInt(10) < 7) {
                    zombie.getEquipment().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
                } else {
                    zombie.getEquipment().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
                }
            } else {
                if (random.nextInt(10) < 7) {
                    zombie.getEquipment().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
                } else {
                    zombie.getEquipment().setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));
                }
            }
        }
        zombie.getEquipment().setLeggingsDropChance(0f);
        zombie.getEquipment().setBoots(new ItemStack(Material.AIR));
        if (wave > 5 && random.nextInt(100) < 30) {
            if (wave > 50) {
                if (random.nextInt(10) < 7) {
                    zombie.getEquipment().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
                } else {
                    zombie.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
                }
            } else if (wave > 20) {
                if (random.nextInt(10) < 7) {
                    zombie.getEquipment().setBoots(new ItemStack(Material.IRON_BOOTS));
                } else {
                    zombie.getEquipment().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
                }
            } else {
                if (random.nextInt(10) < 7) {
                    zombie.getEquipment().setBoots(new ItemStack(Material.LEATHER_BOOTS));
                } else {
                    zombie.getEquipment().setBoots(new ItemStack(Material.CHAINMAIL_BOOTS));
                }
            }
        }
        zombie.getEquipment().setBootsDropChance(0f);
        zombie.getEquipment().setItemInMainHand(new ItemStack(Material.STICK));
        zombie.getEquipment().setItemInMainHandDropChance(0F);
        assert XAttribute.FOLLOW_RANGE.get() != null;
        Objects.requireNonNull(zombie.getAttribute(XAttribute.FOLLOW_RANGE.get())).setBaseValue(200D);
        zombie.setAdult();
        if (wave > 20) {
            if (random.nextInt(10) > 4) {
                zombie.setBaby();
            }
        }
        zombie.setRemoveWhenFarAway(false);
        zombie.setMetadata("PlugilyProjects-VillageDefense-Name", new FixedMetadataValue(plugin, "NormalZombie"));
        return zombie;
    }

    /**
     * Get the name of the spawner
     *
     * @return the name
     */
    @Override
    public String getName() {
        return "NormalZombie";
    }
}
