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
import com.xigua.cumulus.form.SimpleForm;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import plugily.projects.minigamesbox.inventory.normal.NormalFastInv;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.network.NetworkRoomManager.RoomSnapshot;
import plugily.projects.villagedefense.utils.BedrockSupport;

import java.util.ArrayList;
import java.util.List;

/**
 * Gives lobby players a room selector and opens Java/Bedrock specific menus.
 */
public class RoomSelectorManager implements Listener {

    private static final int SELECTOR_SLOT = 0;
    private static final String SELECTOR_NAME = "&a&l房间选择 &7(右键使用)";
    private static final String SELECTOR_TITLE = "&2村庄守卫战 - 房间选择";

    private final Main plugin;

    public RoomSelectorManager(Main plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> giveSelector(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!isSelectorItem(event.getItem()) || plugin.getArenaRegistry().getArena(player) != null) {
            return;
        }
        event.setCancelled(true);
        openSelector(player);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (plugin.getArenaRegistry().getArena(player) == null
                && (isSelectorItem(event.getCurrentItem()) || isSelectorItem(event.getCursor()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.getArenaRegistry().getArena(event.getPlayer()) == null
                && isSelectorItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    private void giveSelector(Player player) {
        if (player == null || !player.isOnline() || plugin.getArenaRegistry().getArena(player) != null) {
            return;
        }
        player.getInventory().setItem(SELECTOR_SLOT, createSelectorItem());
    }

    private void openSelector(Player player) {
        if (BedrockSupport.isBedrockPlayer(plugin, player)) {
            openBedrockSelector(player);
            return;
        }
        openJavaSelector(player);
    }

    private void openJavaSelector(Player player) {
        List<RoomSnapshot> arenas = getVisibleRooms();
        if (arenas.isEmpty()) {
            player.sendMessage(color("&c当前没有可用房间。"));
            return;
        }

        NormalFastInv gui = new NormalFastInv(getInventorySize(arenas.size()), color(SELECTOR_TITLE));
        for (int i = 0; i < Math.min(arenas.size(), 54); i++) {
            RoomSnapshot room = arenas.get(i);
            gui.setItem(i, createArenaItem(room), event -> {
                event.setCancelled(true);
                Player clicker = (Player) event.getWhoClicked();
                clicker.closeInventory();
                joinRoom(clicker, room);
            });
        }
        gui.open(player);
    }

    private void openBedrockSelector(Player player) {
        List<RoomSnapshot> rooms = getVisibleRooms();
        if (rooms.isEmpty()) {
            player.sendMessage(color("&c当前没有可用房间。"));
            return;
        }

        Plugin basePlugin = Bukkit.getPluginManager().getPlugin("BaseAPI");
        if (!(basePlugin instanceof BaseAPI)) {
            openJavaSelector(player);
            return;
        }

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(color(SELECTOR_TITLE))
                .content(color("&7请选择要加入的房间"));
        for (RoomSnapshot room : rooms) {
            String buttonText = color("&a" + safe(room.getMapName(), room.getArenaId()) + " &e" + getModeName(room.getMode())
                    + "\n&7[" + room.getPlayers() + "/" + room.getMaxPlayers() + "]");
            builder.button(buttonText, response -> Bukkit.getScheduler().runTask(plugin, () -> joinRoom(player, room)));
        }
        ((BaseAPI) basePlugin).sendForm(player.getUniqueId(), builder.build());
    }

    private List<RoomSnapshot> getVisibleRooms() {
        List<RoomSnapshot> rooms = new ArrayList<>();
        for (RoomSnapshot room : plugin.getNetworkRoomManager().getRoomsForSelection()) {
            if (room != null) {
                rooms.add(room);
            }
        }
        return rooms;
    }

    private ItemStack createSelectorItem() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(SELECTOR_NAME));
            List<String> lore = new ArrayList<>();
            lore.add(color("&7右键打开房间选择菜单"));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createArenaItem(RoomSnapshot room) {
        ItemStack item = new ItemStack(getArenaMaterial(room));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color("&a&l" + safe(room.getMapName(), room.getArenaId())));
            List<String> lore = new ArrayList<>();
            lore.add(color("&7模式: &f" + getModeName(room.getMode())));
            lore.add(color("&7状态: " + getStateName(room.isInGame())));
            lore.add(color("&7人数: &a" + room.getPlayers() + "&7/&a" + room.getMaxPlayers()));
            lore.add(color("&7最终波数: &f" + getFinalWaveName(room.getFinalWave())));
            lore.add("");
            lore.add(color("&e点击加入房间"));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material getArenaMaterial(RoomSnapshot room) {
        if (!room.isJoinable()) {
            return Material.RED_WOOL;
        }
        if (room.isInGame()) {
            return Material.ORANGE_WOOL;
        }
        return Material.LIME_WOOL;
    }

    private void joinRoom(Player player, RoomSnapshot room) {
        if (plugin.getNetworkRoomManager() != null && plugin.getNetworkRoomManager().isEnabled()) {
            plugin.getNetworkRoomManager().joinRoom(player, room);
            return;
        }
        player.performCommand("vd join " + room.getArenaId());
    }

    private String getModeName(String mode) {
        String normalized = mode == null ? "ENDLESS" : mode.trim().toUpperCase();
        switch (normalized) {
            case "EASY":
                return "简单";
            case "HARD":
                return "困难";
            case "ENDLESS":
            default:
                return "无尽";
        }
    }

    private String getFinalWaveName(int finalWave) {
        return finalWave <= 0 ? "无尽" : String.valueOf(finalWave);
    }

    private String getStateName(boolean inGame) {
        return inGame ? "&6游戏中" : "&a等待中";
    }

    private int getInventorySize(int arenaCount) {
        int size = Math.max(9, ((Math.min(arenaCount, 54) + 8) / 9) * 9);
        return Math.min(size, 54);
    }

    private boolean isSelectorItem(ItemStack item) {
        if (item == null || item.getType() != Material.CHEST || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && color(SELECTOR_NAME).equals(meta.getDisplayName());
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
