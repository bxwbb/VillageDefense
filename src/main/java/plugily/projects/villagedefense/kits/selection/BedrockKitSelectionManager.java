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

package plugily.projects.villagedefense.kits.selection;

import com.xigua.baseAPI.BaseAPI;
import com.xigua.cumulus.form.SimpleForm;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;
import plugily.projects.minigamesbox.api.arena.IPluginArena;
import plugily.projects.minigamesbox.api.events.player.PlugilyPlayerChooseKitEvent;
import plugily.projects.minigamesbox.api.kit.IKit;
import plugily.projects.minigamesbox.classic.handlers.language.MessageBuilder;
import plugily.projects.minigamesbox.classic.utils.configuration.ConfigUtils;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.utils.BedrockSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bedrock form replacement for the default kit selector inventory.
 */
public class BedrockKitSelectionManager implements Listener {

    private static final String CONFIG_NAME = "kit_shop";

    private final Main plugin;
    private FileConfiguration config;

    public BedrockKitSelectionManager(Main plugin) {
        this.plugin = plugin;
        this.config = ConfigUtils.getConfig(plugin, CONFIG_NAME);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        config = ConfigUtils.getConfig(plugin, CONFIG_NAME);
    }

    @EventHandler
    public void onSelectKitCommand(PlayerCommandPreprocessEvent event) {
        String[] parts = splitCommand(event.getMessage());
        if (parts.length < 2 || !isVillageDefenseCommand(parts[0]) || !parts[1].equalsIgnoreCase("selectkit")) {
            return;
        }
        Player player = event.getPlayer();
        if (!BedrockSupport.isBedrockPlayer(plugin, player)) {
            return;
        }

        event.setCancelled(true);
        openList(player);
    }

    public void openList(Player player) {
        Plugin basePlugin = Bukkit.getPluginManager().getPlugin("BaseAPI");
        if (!(basePlugin instanceof BaseAPI)) {
            player.performCommand("vd selectkit");
            return;
        }

        List<IKit> kits = plugin.getKitRegistry().getKits();
        if (kits.isEmpty()) {
            player.sendMessage(color("&c当前没有可选择职业。"));
            return;
        }

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(color(getTitle()))
                .content(color(String.join("\n", config.getStringList("Settings.Bedrock-Content"))));

        for (IKit kit : kits) {
            String button = color(kit.getName() + " &7" + getConfiguredPrice(kit.getKey()) + "游戏币\n" + getOwnershipStatus(player, kit));
            builder.button(button, response -> Bukkit.getScheduler().runTask(plugin, () -> openDetail(player, kit.getKey())));
        }
        ((BaseAPI) basePlugin).sendForm(player.getUniqueId(), builder.build());
    }

    private void openDetail(Player player, String kitKey) {
        Plugin basePlugin = Bukkit.getPluginManager().getPlugin("BaseAPI");
        if (!(basePlugin instanceof BaseAPI)) {
            player.performCommand("vd selectkit");
            return;
        }

        IKit kit = plugin.getKitRegistry().getKitByKey(normalizeKitKey(kitKey));
        if (kit == null) {
            player.sendMessage(color("&c该职业不存在。"));
            return;
        }

        boolean unlocked = isUnlocked(player, kit);
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(color(kit.getName()))
                .content(color(buildDetailContent(player, kit)));

        if (unlocked) {
            builder.button(color(config.getString("Settings.Bedrock-Select-Button", "&a选择该职业")),
                    response -> Bukkit.getScheduler().runTask(plugin, () -> selectKit(player, kit)));
        }
        builder.button(color(config.getString("Settings.Bedrock-Back-Button", "&c返回")),
                response -> Bukkit.getScheduler().runTask(plugin, () -> openList(player)));
        ((BaseAPI) basePlugin).sendForm(player.getUniqueId(), builder.build());
    }

    private boolean selectKit(Player player, IKit kit) {
        IPluginArena arena = plugin.getArenaRegistry().getArena(player);
        if (arena == null) {
            return false;
        }

        PlugilyPlayerChooseKitEvent event = new PlugilyPlayerChooseKitEvent(player, kit, arena);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        if (!isUnlocked(player, kit)) {
            new MessageBuilder("KIT_NOT_UNLOCKED").asKey().value(kit.getName()).player(player).sendPlayer();
            return false;
        }

        plugin.getUserManager().getUser(player).setKit(kit);
        new MessageBuilder("KIT_CHOOSE").asKey().value(kit.getName()).player(player).sendPlayer();
        return true;
    }

    private String buildDetailContent(Player player, IKit kit) {
        List<String> lines = new ArrayList<>();
        lines.add(kit.getName());
        lines.add("&7价格: &b" + getConfiguredPrice(kit.getKey()) + " 游戏币");
        lines.add("&7状态: " + getOwnershipStatus(player, kit));
        lines.add("");
        lines.addAll(kit.getDescription());
        return String.join("\n", lines);
    }

    private boolean isUnlocked(Player player, IKit kit) {
        try {
            return kit.isUnlockedByPlayer(player);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private String getOwnershipStatus(Player player, IKit kit) {
        if (isUnlocked(player, kit)) {
            return config.getString("Settings.Bedrock-Status-Owned", "&a已拥有");
        }
        return config.getString("Settings.Bedrock-Status-Unowned", "&7未拥有");
    }

    private String getTitle() {
        String title = config.getString("Settings.Bedrock-Select-Title", "");
        if (title != null && !title.trim().isEmpty()) {
            return title;
        }
        return config.getString("Settings.Title", "&2职业购买");
    }

    private String getConfiguredPrice(String kitKey) {
        return String.valueOf((int) Math.max(0.0D, config.getDouble("Kits." + normalizeKitKey(kitKey) + ".Price", 0.0D)));
    }

    private String normalizeKitKey(String kitKey) {
        return kitKey == null ? "" : kitKey.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isVillageDefenseCommand(String command) {
        String normalized = command == null ? "" : command.toLowerCase(Locale.ROOT);
        int namespaceIndex = normalized.indexOf(':');
        if (namespaceIndex >= 0 && namespaceIndex + 1 < normalized.length()) {
            normalized = normalized.substring(namespaceIndex + 1);
        }
        return normalized.equals("vd") || normalized.equals("villagedefense") || normalized.equals("villaged");
    }

    private String[] splitCommand(String rawCommand) {
        if (rawCommand == null) {
            return new String[0];
        }
        String normalized = rawCommand.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return new String[0];
        }
        return normalized.split("\\s+");
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
