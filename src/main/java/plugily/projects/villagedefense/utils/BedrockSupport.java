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

package plugily.projects.villagedefense.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import plugily.projects.villagedefense.Main;

import java.lang.reflect.Method;
import java.util.UUID;

public final class BedrockSupport {

    private BedrockSupport() {
    }

    public static boolean isBedrockPlayer(Main plugin, Player player) {
        if (plugin == null || player == null || !isBaseApiEnabled() || !isFloodgateEnabled()) {
            return false;
        }

        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstance = apiClass.getMethod("getInstance");
            Object api = getInstance.invoke(null);
            Method isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            Object result = isFloodgatePlayer.invoke(api, player.getUniqueId());
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean isBaseApiEnabled() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("BaseAPI");
        return plugin != null && plugin.isEnabled();
    }

    private static boolean isFloodgateEnabled() {
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if ("floodgate".equalsIgnoreCase(plugin.getName()) && plugin.isEnabled()) {
                return true;
            }
        }
        return false;
    }
}
