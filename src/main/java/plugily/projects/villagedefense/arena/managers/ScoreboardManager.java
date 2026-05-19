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

import org.bukkit.entity.Player;
import plugily.projects.minigamesbox.api.arena.IArenaState;
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
    public List<String> getScoreboardLines(Player player) {
        List<String> lines;
        if (arena.getArenaState() == IArenaState.IN_GAME) {
            lines = arena.getPlugin().getLanguageManager().getLanguageList("Scoreboard.Content." + arena.getArenaState().getFormattedName() + (((Arena) arena).isFighting() ? "" : "-Waiting"));
        } else {
            lines = super.getScoreboardLines(player);
        }

        Arena pluginArena = (Arena) arena.getPlugin().getArenaRegistry().getArena(arena.getId());
        if (pluginArena != null) {
            List<Map.Entry<Player, Double>> points = pluginArena.getSortedPlayers();
            lines.add("§e§l积分 TOP 5 玩家");

            if (!points.isEmpty()) {
                double lastScore = -1;
                int displayRank = 1;
                int actualIndex = 0;

                for (Map.Entry<Player, Double> entry : points) {
                    if (actualIndex >= 5) break; // 只显示前5

                    player = entry.getKey();
                    double score = entry.getValue();

                    // 处理并列排名：分数相同，排名不变
                    if (score != lastScore) {
                        displayRank = actualIndex + 1;
                        lastScore = score;
                    }

                    // 排名颜色
                    String rankColor = switch (displayRank) {
                        case 1 -> "§6§l"; // 第1 金色
                        case 2 -> "§7§l"; // 第2 银色
                        case 3 -> "§c§l"; // 第3 铜色
                        default -> "§f";  // 其他 白色
                    };

                    // 显示格式：保留2位小数（可自己改）
                    lines.add(rankColor + displayRank + ". §f" + player.getName() + " §7| §a" + String.format("%.2f", score));

                    actualIndex++;
                }
            }
        }

        return lines;
    }
}
