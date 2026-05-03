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

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import plugily.projects.minigamesbox.api.user.IUser;
import plugily.projects.minigamesbox.classic.handlers.language.MessageBuilder;
import plugily.projects.minigamesbox.classic.utils.configuration.ConfigUtils;
import plugily.projects.minigamesbox.classic.utils.helper.ItemUtils;
import plugily.projects.minigamesbox.classic.utils.misc.complement.ComplementAccessor;
import plugily.projects.minigamesbox.classic.utils.serialization.LocationSerializer;
import plugily.projects.minigamesbox.classic.utils.version.xseries.XEntityType;
import plugily.projects.minigamesbox.inventory.normal.NormalFastInv;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.arena.Arena;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Created by Tom on 16/08/2014.
 */
public class ShopManager {

    private final String defaultGolemItemName;
    private final String defaultWolfItemName;

    final Main plugin;
    private final FileConfiguration config;
    final Arena arena;
    private Consumer<Player> openMenuConsumer;
    final PotionManager potionManager;
    private final ShopMenu shopMenu;

    final Map<Player, Byte[]> playerData = new HashMap<>();

    public static final Map<Material, Integer> prices = new HashMap<>();

    static {
        prices.put(Material.LEATHER_HELMET, 10);
        prices.put(Material.LEATHER_CHESTPLATE, 20);
        prices.put(Material.LEATHER_LEGGINGS, 15);
        prices.put(Material.LEATHER_BOOTS, 10);
        prices.put(Material.IRON_HELMET, 20);
        prices.put(Material.IRON_CHESTPLATE, 40);
        prices.put(Material.IRON_LEGGINGS, 30);
        prices.put(Material.IRON_BOOTS, 20);
        prices.put(Material.DIAMOND_HELMET, 50);
        prices.put(Material.DIAMOND_CHESTPLATE, 100);
        prices.put(Material.DIAMOND_LEGGINGS, 80);
        prices.put(Material.DIAMOND_BOOTS, 50);
        prices.put(Material.NETHERITE_HELMET, 100);
        prices.put(Material.NETHERITE_CHESTPLATE, 200);
        prices.put(Material.NETHERITE_LEGGINGS, 160);
        prices.put(Material.NETHERITE_BOOTS, 100);
        prices.put(Material.STONE_SWORD, 20);
        prices.put(Material.IRON_SWORD, 30);
        prices.put(Material.DIAMOND_SWORD, 50);
        prices.put(Material.NETHERITE_SWORD, 100);
        prices.put(Material.TRIDENT, 100);
        prices.put(Material.BOW, 100);
        prices.put(Material.CROSSBOW, 200);
        prices.put(Material.TOTEM_OF_UNDYING, 10000);
        prices.put(Material.LAPIS_ORE, 500);
    }

    public ShopManager(Arena arena) {
        shopMenu = new ShopMenuJava(arena, this);
        plugin = arena.getPlugin();
        config = ConfigUtils.getConfig(plugin, "arenas");
        this.arena = arena;

        defaultGolemItemName = new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_GOLEM_ITEM", false).asKey().build();
        defaultWolfItemName = new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_WOLF_ITEM", false).asKey().build();

        if (config.isSet("instances." + arena.getId() + ".shop")) {
            registerShop();
        }
        openMenuConsumer = player -> {
            if (plugin.getArenaRegistry().getArena(player) == null) {
                return;
            }
            if (shopMenu.isReady()) {
                new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_NOT_DEFINED").asKey().player(player).sendPlayer();
                return;
            }
            shopMenu.open(player);
        };
        potionManager = new PotionManager(plugin, this);
    }

    public void resetPlayerData() {
        for (Player player : arena.getPlayers()) {
            // 衣服四件套(最大值，当前值)
            // 剑，三叉戟
            playerData.put(player, new Byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        }
    }

    public void setOpenMenuConsumer(@NotNull Consumer<Player> openMenuConsumer) {
        this.openMenuConsumer = openMenuConsumer;
    }

    /**
     * Default name of golem spawn item from language.yml
     *
     * @return the default golem item name
     */
    public String getDefaultGolemItemName() {
        return defaultGolemItemName;
    }

    /**
     * Default name of wolf spawn item from language.yml
     *
     * @return the default wolf item name
     */
    public String getDefaultWolfItemName() {
        return defaultWolfItemName;
    }

    public void openShop(Player player) {
        if (openMenuConsumer != null) {
            openMenuConsumer.accept(player);
        }
    }

    void addMaxStackType(int slot, Player player) {
        if (slot == 5) {
            if (playerData.get(player)[slot * 2] >= 2) {
                if (Objects.equals(playerData.get(player)[slot * 2 + 1], playerData.get(player)[slot * 2])) {
                    playerData.get(player)[slot * 2 + 1] = 2;
                } else {
                    playerData.get(player)[slot * 2 + 1]++;
                }
            }
        } else {
            if (Objects.equals(playerData.get(player)[slot * 2 + 1], playerData.get(player)[slot * 2])) {
                playerData.get(player)[slot * 2 + 1] = 0;
            } else {
                playerData.get(player)[slot * 2 + 1]++;
            }
        }
    }

    private void registerShop() {
        shopMenu.registerShop();
    }

    void giveItem(Player player, ItemStack itemStack) {
        player.getInventory().addItem(itemStack);
    }

    void doubleAddPlayerData(int slot, Player player) {
        playerData.get(player)[slot * 2]++;
        playerData.get(player)[slot * 2 + 1] = playerData.get(player)[slot * 2];
    }

    void setPlayerData(int slot, Player player, byte value) {
        playerData.get(player)[slot * 2] = value;
        playerData.get(player)[slot * 2 + 1] = playerData.get(player)[slot * 2];
    }

    void adjustOrbs(IUser user, int cost) {
        user.adjustStatistic("ORBS", -cost);
        arena.changeArenaOptionBy("TOTAL_ORBS_SPENT", cost);
    }

}
