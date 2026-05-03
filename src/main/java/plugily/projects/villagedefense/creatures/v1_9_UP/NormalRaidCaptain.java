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

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Creature;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import plugily.projects.minigamesbox.classic.utils.version.VersionUtils;
import plugily.projects.minigamesbox.classic.utils.version.xseries.XAttribute;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.arena.Arena;
import plugily.projects.villagedefense.arena.managers.spawner.SimpleEnemySpawner;

import java.util.Objects;

/**
 * @author Tigerpanzer_02
 * Created at 15.01.2022
 */
public class NormalRaidCaptain implements SimpleEnemySpawner, Listener {

    private final Main plugin;

    public NormalRaidCaptain(Main plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public ItemStack getDropItem() {
        return new ItemStack(Material.ROTTEN_FLESH);
    }

    @Override
    public double getSpawnRate(Arena arena, int wave, int phase, int spawnAmount) {
        return 1;
    }

    @Override
    public int getFinalAmount(Arena arena, int wave, int phase, int spawnAmount) {
        return 1;
    }

    @Override
    public boolean checkPhase(Arena arena, int wave, int phase, int spawnAmount) {
        return true;
    }

    @Override
    public Creature spawn(Location location) {
        Pillager pillager = (Pillager) VersionUtils.spawnEntity(location, EntityType.PILLAGER);

        // 灾厄队长旗帜
        ItemStack banner = new ItemStack(Material.WHITE_BANNER);
        BannerMeta meta = (BannerMeta) banner.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("§c灾厄队长"));
            banner.setItemMeta(meta);
        }

        // 装备设置
        pillager.getEquipment().setHelmet(banner);
        pillager.getEquipment().setHelmetDropChance(0.0f);

        pillager.getEquipment().setChestplate(new ItemStack(Material.AIR));
        pillager.getEquipment().setChestplateDropChance(0.0f);
        pillager.getEquipment().setLeggings(new ItemStack(Material.AIR));
        pillager.getEquipment().setLeggingsDropChance(0.0f);
        pillager.getEquipment().setBoots(new ItemStack(Material.AIR));
        pillager.getEquipment().setBootsDropChance(0.0f);

        pillager.getEquipment().setItemInMainHand(new ItemStack(Material.CROSSBOW));
        pillager.getEquipment().setItemInMainHandDropChance(0.0f);

        // 属性
        Objects.requireNonNull(pillager.getAttribute(XAttribute.FOLLOW_RANGE.get())).setBaseValue(200D);
        pillager.setRemoveWhenFarAway(false);

        // 标记
        pillager.setMetadata("NormalRaidCaptain", new FixedMetadataValue(plugin, true));
        return pillager;
    }

    // 击杀给予灾厄效果
    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (!e.getEntity().hasMetadata("NormalRaidCaptain")) return;

        Player killer = e.getEntity().getKiller();
        if (killer == null) return;

        // 给予 灾厄 Bad Omen
        killer.addPotionEffect(new PotionEffect(PotionEffectType.BAD_OMEN, 20 * 60, 0, false, false));
        killer.sendMessage("§4§l你杀死了灾厄队长，获得了灾厄效果！");
    }

    @Override
    public String getName() {
        return "NormalRaidCaptain";
    }
}