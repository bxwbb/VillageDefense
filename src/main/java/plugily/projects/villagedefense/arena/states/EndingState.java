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

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import plugily.projects.minigamesbox.api.user.IUser;
import plugily.projects.minigamesbox.classic.arena.PluginArena;
import plugily.projects.minigamesbox.classic.arena.states.PluginEndingState;
import plugily.projects.villagedefense.arena.Arena;

import java.util.List;

/**
 * @author Plajer
 * <p>
 * Created at 03.06.2019
 */
public class EndingState extends PluginEndingState {

    public static final String giftName = "xinghan1";

    @Override
    public void handleCall(PluginArena arena) {
        super.handleCall(arena);
        Arena pluginArena = (Arena) getPlugin().getArenaRegistry().getArena(arena.getId());
        if (pluginArena == null) return;
        if (arena.getTimer() <= 0) {
            for (Player player : arena.getPlayers()) {
                IUser user = getPlugin().getUserManager().getUser(player);
                user.setStatistic("ORBS", 0);
                performPointRewards(pluginArena, player);
            }
        }
    }

    private void performPointRewards(Arena arena, Player player) {
        if (!getPlugin().getConfig().getBoolean("Points.End-Rewards.Enabled", true)) {
            return;
        }

        int point = arena.getPlayerPoints(player);
        ConfigurationSection configRewards = getPlugin().getConfig().getConfigurationSection("Points.End-Rewards.Levels");
        boolean cumulative = getPlugin().getConfig().getBoolean("Points.End-Rewards.Cumulative", true);
        if (configRewards == null) {
            return;
        }
        performConfiguredPointRewards(configRewards, point, player, cumulative);
    }

    private void performConfiguredPointRewards(ConfigurationSection rewards, int point, Player player, boolean cumulative) {
        int bestScore = Integer.MIN_VALUE;
        List<String> bestCommands = null;

        for (String key : rewards.getKeys(false)) {
            int score = rewards.getInt(key + ".Score");
            List<String> commands = rewards.getStringList(key + ".Commands");
            if (point < score) {
                continue;
            }
            if (cumulative) {
                dispatchCommands(commands, player);
                continue;
            }
            if (score > bestScore) {
                bestScore = score;
                bestCommands = commands;
            }
        }

        if (!cumulative && bestCommands != null) {
            dispatchCommands(bestCommands, player);
        }
    }

    private void dispatchCommands(List<String> commands, Player player) {
        for (String command : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
        }
    }
}
