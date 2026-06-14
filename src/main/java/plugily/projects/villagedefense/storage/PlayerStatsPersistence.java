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

package plugily.projects.villagedefense.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import plugily.projects.minigamesbox.api.user.IUser;
import plugily.projects.villagedefense.Main;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 插件侧本地统计兜底持久化。
 *
 * <p>当前项目的运行时统计更新逻辑是完整的，但实际持久化链在部分环境中没有生效，
 * 因此这里额外将关键统计同步到插件数据目录下，确保重启后可恢复。</p>
 */
public class PlayerStatsPersistence {

    private static final List<String> PERSISTENT_STATS = Arrays.asList(
        "WINS",
        "LOSES",
        "KILLS",
        "DEATHS",
        "HIGHEST_WAVE"
    );

    private final Main plugin;
    private final File file;
    private YamlConfiguration data;

    public PlayerStatsPersistence(Main plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "player_stats.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public void load(Player player) {
        IUser user = plugin.getUserManager().getUser(player);
        if (user == null) {
            return;
        }

        String path = getPlayerPath(player.getUniqueId());
        if (!data.contains(path)) {
            return;
        }

        for (String statistic : PERSISTENT_STATS) {
            user.setStatistic(statistic, data.getInt(path + "." + statistic, user.getStatistic(statistic)));
        }
    }

    public void save(Player player) {
        IUser user = plugin.getUserManager().getUser(player);
        if (user == null) {
            return;
        }

        String path = getPlayerPath(player.getUniqueId());
        for (String statistic : PERSISTENT_STATS) {
            data.set(path + "." + statistic, user.getStatistic(statistic));
        }
        saveFile();
    }

    public void saveAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            IUser user = plugin.getUserManager().getUser(player);
            if (user == null) {
                continue;
            }

            String path = getPlayerPath(player.getUniqueId());
            for (String statistic : PERSISTENT_STATS) {
                data.set(path + "." + statistic, user.getStatistic(statistic));
            }
        }
        saveFile();
    }

    private String getPlayerPath(UUID uniqueId) {
        return "players." + uniqueId;
    }

    private void saveFile() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to save player statistics to " + file.getName(), exception);
        }
    }
}
