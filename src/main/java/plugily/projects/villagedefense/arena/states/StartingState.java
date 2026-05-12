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

package plugily.projects.villagedefense.arena.states;

import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import plugily.projects.minigamesbox.api.user.IUser;
import plugily.projects.minigamesbox.classic.arena.PluginArena;
import plugily.projects.minigamesbox.classic.arena.states.PluginStartingState;
import plugily.projects.minigamesbox.classic.utils.version.VersionUtils;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.arena.Arena;
import plugily.projects.villagedefense.creatures.CreatureUtils;

/**
 * @author Plajer
 * <p>
 * Created at 03.06.2019
 */
public class StartingState extends PluginStartingState {

    @Override
    public void handleCall(PluginArena arena) {
        super.handleCall(arena);
        Arena pluginArena = (Arena) getPlugin().getArenaRegistry().getArena(arena.getId());
        if (pluginArena == null) {
            return;
        }
        // 如果计时器刚开始启动或者强制开始
        if (arena.getTimer() == 0 || arena.isForceStart()) {
            pluginArena.clearVillagers();
            pluginArena.spawnVillagers();

            pluginArena.getShopManager().resetPlayerData();

            // 设置村民血量为200并给恢复一
            pluginArena.getVillagers().forEach(villager -> {
                VersionUtils.setMaxHealth(villager, 200);
                villager.setHealth(200);
//                villager.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, 10000, 43, true, false, false));
//                villager.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 10000, 0, true, false, false));
                villager.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 0, 255, true, false, false));
            });
            pluginArena.getVillagers().getFirst().setProfession(Villager.Profession.CLERIC);

            for (Villager villager : pluginArena.getVillagers()) {
                villager.setCustomName(CreatureUtils.getHealthNameTag(villager));
            }

            int orbsStartingAmount = getPlugin().getConfig().getInt("Orbs.Start.Amount", 20);

            for (Player player : arena.getPlayers()) {
                IUser user = getPlugin().getUserManager().getUser(player);
                // 设置玩家初始金币数量
                user.setStatistic("ORBS", orbsStartingAmount);
            }
            setArenaTimer(getPlugin().getConfig().getInt("Time-Manager.Cooldown-Before-Next-Wave", 25));
            pluginArena.setFighting(false);

            for (Player player : pluginArena.getPlayers()) {
                pluginArena.playerPoints.put(player, 0);
            }
        }
    }


}
