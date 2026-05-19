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

package plugily.projects.villagedefense.arena.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import plugily.projects.minigamesbox.api.arena.IArenaState;
import plugily.projects.minigamesbox.api.user.IUser;
import plugily.projects.minigamesbox.classic.arena.PluginArena;
import plugily.projects.minigamesbox.classic.arena.managers.PluginScoreboardManager;
import plugily.projects.villagedefense.arena.Arena;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Tigerpanzer_02
 * <p>
 * Created at 19.12.2021
 */
public class ScoreboardManager extends PluginScoreboardManager {

    private final PluginArena arena;

    public ScoreboardManager(PluginArena arena) {
        super(arena);
        this.arena = arena;
    }

    @Override
    public void createScoreboard(IUser user) {
        if (shouldUseBaseApiScoreboard()) {
            super.removeScoreboard(user);
            return;
        }
        super.createScoreboard(user);
    }

    @Override
    public void updateScoreboards() {
        if (shouldUseBaseApiScoreboard()) {
            removeBukkitScoreboards();
            return;
        }
        super.updateScoreboards();
    }

    @Override
    public List<String> getScoreboardLines(Player player) {
        List<String> lines = new ArrayList<>();
        if (arena.getArenaState() == IArenaState.IN_GAME) {
            lines = arena.getPlugin().getLanguageManager().getLanguageList("Scoreboard.Content." + arena.getArenaState().getFormattedName() + (((Arena) arena).isFighting() ? "" : "-Waiting"));
        } else {
            lines = super.getScoreboardLines(player);
        }

        Arena pluginArena = (Arena) arena.getPlugin().getArenaRegistry().getArena(arena.getId());
        if (pluginArena != null && arena.getArenaState() == IArenaState.IN_GAME) {
            List<Map.Entry<Player, Integer>> points = pluginArena.getSortedPlayers();
            List<String> pointsLines = new ArrayList<>();

            pointsLines.add("§e§l积分 TOP 5 玩家");

            int rank = 1;
            for (int i = 0; i < Math.min(5, points.size()); i++) {
                Map.Entry<Player, Integer> entry = points.get(i);
                String name = entry.getKey().getName();
                int score = entry.getValue();

                String rankColor = switch (rank) {
                    case 1 -> "§6§l";
                    case 2 -> "§7§l";
                    case 3 -> "§c§l";
                    default -> "§f";
                };

                pointsLines.add(rankColor + rank + ". §f" + name + " §7| §a" + score);
                rank++;
            }
            lines.addAll(0, pointsLines);
        }

        return lines;
    }

    private boolean shouldUseBaseApiScoreboard() {
        return Bukkit.getPluginManager().isPluginEnabled("BaseAPI")
                && ((Arena) arena).getPlugin().getConfig().getBoolean("Scoreboard.BaseAPI.Enabled", true);
    }

    private void removeBukkitScoreboards() {
        for (Player player : arena.getPlayers()) {
            IUser user = arena.getPlugin().getUserManager().getUser(player);
            if (user != null) {
                super.removeScoreboard(user);
            }
        }
    }
}
