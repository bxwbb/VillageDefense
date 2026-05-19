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

import com.xigua.baseAPI.BaseAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import plugily.projects.minigamesbox.api.arena.managers.IPluginScoreboardManager;
import plugily.projects.minigamesbox.api.user.IUser;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.arena.Arena;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends all scoreboards through BaseAPI's client packet channel.
 */
public class BaseApiScoreboardManager implements Listener {

    private static final Pattern USER_STATISTIC_PATTERN = Pattern.compile("%user_statistic_([a-zA-Z0-9_]+)%");
    private static final Pattern ARENA_OPTION_PATTERN = Pattern.compile("%arena_option_([a-zA-Z0-9_]+)%");

    private final Main plugin;
    private BaseAPI baseAPI;
    private Method papiSetPlaceholdersMethod;

    public BaseApiScoreboardManager(Main plugin) {
        this.plugin = plugin;
        this.baseAPI = findBaseAPI();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startUpdateTask();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> sendScoreboard(event.getPlayer()), 20L);
    }

    private void startUpdateTask() {
        long interval = Math.max(1L, plugin.getConfig().getLong("Scoreboard.BaseAPI.Update-Interval-Ticks", 20L));
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.getConfig().getBoolean("Scoreboard.Display", true)
                        || !plugin.getConfig().getBoolean("Scoreboard.BaseAPI.Enabled", true)) {
                    return;
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    sendScoreboard(player);
                }
            }
        }.runTaskTimer(plugin, 20L, interval);
    }

    private void sendScoreboard(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        BaseAPI api = getBaseAPI();
        if (api == null) {
            return;
        }

        Arena arena = plugin.getArenaRegistry().getArena(player);
        ScoreboardContext context = new ScoreboardContext(arena);
        String title = plugin.getLanguageConfig().getString("Scoreboard.Title", "&2村庄守卫战");
        List<String> lines = arena == null ? getLobbyLines() : getArenaLines(arena, player);

        sendViaBaseAPI(player, title, lines, context);
    }

    private List<String> getLobbyLines() {
        List<String> lines = plugin.getLanguageManager().getLanguageList("Scoreboard.Content.Lobby");
        if (!lines.isEmpty()) {
            return lines;
        }
        List<String> fallback = new ArrayList<>();
        fallback.add("");
        fallback.add(" &a玩家 | %player%");
        fallback.add(" &f在线人数 | %online%");
        fallback.add("");
        fallback.add(" &b最高波次 | %user_statistic_highest_wave%");
        return fallback;
    }

    private List<String> getArenaLines(Arena arena, Player player) {
        IPluginScoreboardManager scoreboardManager = arena.getScoreboardManager();
        List<String> lines = scoreboardManager.getScoreboardLines(player);
        return scoreboardManager.formatScoreboardLines(lines, player);
    }

    private void sendViaBaseAPI(Player player, String title, List<String> lines, ScoreboardContext context) {
        BaseAPI api = getBaseAPI();
        if (api == null) {
            return;
        }
        Map<String, Object> eventData = new HashMap<>();

        eventData.put("title", ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', replaceCommon(player, title, context))));

        List<String> order = new ArrayList<>();
        Map<String, String> textDict = new LinkedHashMap<>();

        for (int i = 0; i < lines.size(); i++) {
            String key = String.valueOf(i + 1);
            order.add(key);
            textDict.put(key, replaceCommon(player, lines.get(i), context));
        }
        eventData.put("order", order);
        eventData.put("text_dict", textDict);

        api.notifyToClient(player, "Xigua_common", "main", "SetScoreboard", eventData);
    }

    private String replaceCommon(Player player, String text, ScoreboardContext context) {
        if (text == null) {
            return "";
        }
        String replaced = text
                .replace("%plugin_name%", "&2村庄守卫战")
                .replace("%player%", player.getName())
                .replace("%player_name%", player.getName())
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%server_online%", String.valueOf(Bukkit.getOnlinePlayers().size()));

        replaced = replaceUserStatistics(player, replaced);
        replaced = replaceArenaPlaceholders(player, replaced, context);
        replaced = applyPlaceholderApi(player, replaced);
        return ChatColor.translateAlternateColorCodes('&', replaced);
    }

    private String applyPlaceholderApi(Player player, String text) {
        Method setPlaceholdersMethod = getPapiSetPlaceholdersMethod();
        if (setPlaceholdersMethod == null) {
            return text;
        }
        try {
            Object replaced = setPlaceholdersMethod.invoke(null, player, text);
            if (replaced instanceof String value) {
                return value;
            }
        } catch (ReflectiveOperationException ignored) {
            papiSetPlaceholdersMethod = null;
        }
        return text;
    }

    private Method getPapiSetPlaceholdersMethod() {
        if (papiSetPlaceholdersMethod != null) {
            return papiSetPlaceholdersMethod;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return null;
        }
        try {
            Class<?> placeholderApi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            papiSetPlaceholdersMethod = findSetPlaceholdersMethod(placeholderApi);
            return papiSetPlaceholdersMethod;
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return null;
        }
    }

    private Method findSetPlaceholdersMethod(Class<?> placeholderApi) throws NoSuchMethodException {
        try {
            return placeholderApi.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
        } catch (NoSuchMethodException ignored) {
            return placeholderApi.getMethod("setPlaceholders", Player.class, String.class);
        }
    }

    private String replaceUserStatistics(Player player, String text) {
        IUser user = plugin.getUserManager().getUser(player);
        if (user == null) {
            return text;
        }
        Matcher matcher = USER_STATISTIC_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String statistic = matcher.group(1).toUpperCase(Locale.ROOT);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(String.valueOf(user.getStatistic(statistic))));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String replaceArenaPlaceholders(Player player, String text, ScoreboardContext context) {
        Arena arena = context.arena;
        if (arena == null) {
            return text.replace("%arena_player_points%", "0");
        }

        String replaced = text
                .replace("%arena_name%", arena.getMapName())
                .replace("%arena_id%", arena.getId())
                .replace("%arena_players_size%", String.valueOf(arena.getPlayers().size()))
                .replace("%arena_players_left_size%", String.valueOf(arena.getPlayersLeft().size()))
                .replace("%arena_max_players%", String.valueOf(arena.getMaximumPlayers()))
                .replace("%arena_min_players%", String.valueOf(arena.getMinimumPlayers()))
                .replace("%arena_time%", String.valueOf(arena.getTimer()))
                .replace("%arena_state%", arena.getArenaState().getFormattedName())
                .replace("%arena_villager_size%", String.valueOf(arena.getVillagers().size()))
                .replace("%arena_zombie_size_left%", String.valueOf(arena.getZombiesLeft()))
                .replace("%arena_rotten_flesh_amount%", String.valueOf(arena.getArenaOption("ROTTEN_FLESH_AMOUNT")))
                .replace("%arena_player_points%", String.valueOf(arena.getPlayerPoints(player)));

        Matcher matcher = ARENA_OPTION_PATTERN.matcher(replaced);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String option = matcher.group(1).toUpperCase(Locale.ROOT);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(String.valueOf(arena.getArenaOption(option))));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private BaseAPI getBaseAPI() {
        if (baseAPI != null && baseAPI.isEnabled()) {
            return baseAPI;
        }
        baseAPI = findBaseAPI();
        return baseAPI;
    }

    private BaseAPI findBaseAPI() {
        Plugin basePlugin = Bukkit.getPluginManager().getPlugin("BaseAPI");
        if (basePlugin instanceof BaseAPI api) {
            return api;
        }
        return null;
    }

    private static final class ScoreboardContext {
        private final Arena arena;

        private ScoreboardContext(Arena arena) {
            this.arena = arena;
        }
    }
}
