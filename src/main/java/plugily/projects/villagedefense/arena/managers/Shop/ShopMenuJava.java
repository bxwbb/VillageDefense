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

package plugily.projects.villagedefense.arena.managers.Shop;


import com.xigua.baseAPI.api.events.NeteasePythonEvent;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import plugily.projects.minigamesbox.api.user.IUser;
import plugily.projects.minigamesbox.classic.handlers.language.MessageBuilder;
import plugily.projects.minigamesbox.classic.utils.misc.complement.ComplementAccessor;
import plugily.projects.minigamesbox.inventory.normal.NormalFastInv;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Tom on 16/08/2014.
 */
public class ShopMenuJava extends ShopMenu {

    private Map<Player, NormalFastInv> guis = new HashMap<>();

    public ShopMenuJava(ShopManager shopManager) {
        super(shopManager);
    }

    public ItemStack getLevelItemWithLore(Merchandise merchandise, int level, ShopManager.PlayerInfo playerInfo) {
        ItemStack itemStack = merchandise.getLevelItem(level);
        ItemMeta itemMeta = itemStack.getItemMeta();
        List<String> lore = ComplementAccessor.getComplement().getLore(itemMeta);
        int maxLevel = merchandise.MAX_LEVEL;
        int current = playerInfo.level;
        int playerMaxUnlock = playerInfo.maxLevel;
        for (int i = 1; i <= maxLevel; i++) {
            StringBuilder line = new StringBuilder();
            ChatColor color;
            if (i == current) {
                color = ChatColor.GREEN;
            } else if (i <= playerMaxUnlock) {
                color = ChatColor.GRAY;
            } else {
                color = ChatColor.DARK_GRAY;
            }
            String name = merchandise.getLevelName(i);
            if (i == current) {
                line.append(color).append("> ").append(name);
            } else {
                line.append(color).append("  ").append(name);
            }
            lore.add(line.toString());
        }
        lore.add("");
        lore.add(ChatColor.GOLD + String.valueOf(merchandise.getLevelPrice(level)) + " " + new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_CURRENCY").asKey().build());
        lore.add(new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_ITEM_CTRL_LORE").asKey().build());
        ComplementAccessor.getComplement().setLore(itemMeta, lore);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    public void registerShop() {
        for (Player p : getShopManager().arena.getPlayers()) {
            guis.put(p, new NormalFastInv(54, new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_GUI").asKey().build()));
            guis.get(p).addOpenHandler(inventoryOpenEvent -> {
                Player player = (Player) inventoryOpenEvent.getPlayer();
                for (Merchandise merchandise : ShopManager.merchandises) {
                    if (!getShopManager().playerData.containsKey(player)) {
                        getShopManager().playerData.put(player, new HashMap<>());
                    }
                    if (!getShopManager().playerData.get(player).containsKey(merchandise)) {
                        boolean allShow = false;
                        if (merchandise instanceof UpgradableMerchandise upgradableMerchandise)
                            allShow = upgradableMerchandise.isAllShow();
                        getShopManager().playerData.get(player).put(merchandise, new ShopManager.PlayerInfo(allShow ? merchandise.MAX_LEVEL : 1, allShow ? merchandise.MAX_LEVEL : 1));
                    }
                    ShopManager.PlayerInfo playerInfo = getShopManager().playerData.get(player).get(merchandise);
                    guis.get(p).setItem(merchandise.SLOT, getLevelItemWithLore(merchandise, playerInfo.level, playerInfo), inventoryClickEvent -> {
                        Player clickPlayer = (Player) inventoryClickEvent.getWhoClicked();
                        if (inventoryClickEvent.getClick().isShiftClick()) {
                            if (inventoryClickEvent.getClick().isRightClick()) {
                                clickPlayer.playSound(
                                        clickPlayer.getLocation(),
                                        Sound.UI_BUTTON_CLICK,
                                        1.0F,
                                        1.2F
                                );
                                getShopManager().changePlayerData(merchandise, clickPlayer, (byte) -1);
                                guis.get(p).getInventory().setItem(merchandise.SLOT, getLevelItemWithLore(merchandise, playerInfo.level, playerInfo));
                            }
                        } else {
                            if (inventoryClickEvent.getClick().isLeftClick()) {
                                int cost = merchandise.getLevelPrice(playerInfo.level);
                                IUser user = getShopManager().plugin.getUserManager().getUser(clickPlayer);
                                int orbs = user.getStatistic("ORBS");
                                if (cost > orbs) {
                                    new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_NOT_ENOUGH_CURRENCY").asKey().player(clickPlayer).sendPlayer();
                                    clickPlayer.playSound(
                                            clickPlayer.getLocation(),
                                            Sound.ENTITY_VILLAGER_NO,
                                            1.0F,
                                            0.8F
                                    );
                                    return;
                                }
                                clickPlayer.playSound(
                                        clickPlayer.getLocation(),
                                        Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                                        1.0F,
                                        1.2F
                                );
                                clickPlayer.getInventory().addItem(merchandise.getLevelItem(playerInfo.level));
                                if (playerInfo.level == playerInfo.maxLevel) {
                                    getShopManager().doubleAddPlayerData(merchandise, clickPlayer);
                                    guis.get(p).getInventory().setItem(merchandise.SLOT, getLevelItemWithLore(merchandise, playerInfo.level, playerInfo));
                                }
                                getShopManager().adjustOrbs(user, cost);
                            } else if (inventoryClickEvent.getClick().isRightClick()) {
                                clickPlayer.playSound(
                                        clickPlayer.getLocation(),
                                        Sound.UI_BUTTON_CLICK,
                                        1.0F,
                                        1.2F
                                );
                                getShopManager().changePlayerData(merchandise, clickPlayer, (byte) 1);
                                guis.get(p).getInventory().setItem(merchandise.SLOT, getLevelItemWithLore(merchandise, playerInfo.level, playerInfo));
                            }
                        }
                    });
                }
            });
        }
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void open(Player player) {
        if (!guis.containsKey(player)) {
            guis.put(player, new NormalFastInv(54, new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_GUI").asKey().build()));
            guis.get(player).addOpenHandler(inventoryOpenEvent -> {
                Player inventoryOpenEventPlayer = (Player) inventoryOpenEvent.getPlayer();
                for (Merchandise merchandise : ShopManager.merchandises) {
                    if (!getShopManager().playerData.containsKey(inventoryOpenEventPlayer)) {
                        getShopManager().playerData.put(inventoryOpenEventPlayer, new HashMap<>());
                    }
                    if (!getShopManager().playerData.get(inventoryOpenEventPlayer).containsKey(merchandise)) {
                        boolean allShow = false;
                        if (merchandise instanceof UpgradableMerchandise upgradableMerchandise)
                            allShow = upgradableMerchandise.isAllShow();
                        getShopManager().playerData.get(inventoryOpenEventPlayer).put(merchandise, new ShopManager.PlayerInfo(allShow ? merchandise.MAX_LEVEL : 1, allShow ? merchandise.MAX_LEVEL : 1));
                    }
                    ShopManager.PlayerInfo playerInfo = getShopManager().playerData.get(inventoryOpenEventPlayer).get(merchandise);
                    guis.get(player).setItem(merchandise.SLOT, getLevelItemWithLore(merchandise, playerInfo.level, playerInfo), inventoryClickEvent -> {
                        Player clickPlayer = (Player) inventoryClickEvent.getWhoClicked();
                        if (inventoryClickEvent.getClick().isShiftClick()) {
                            if (inventoryClickEvent.getClick().isRightClick()) {
                                clickPlayer.playSound(
                                        clickPlayer.getLocation(),
                                        Sound.UI_BUTTON_CLICK,
                                        1.0F,
                                        1.2F
                                );
                                getShopManager().changePlayerData(merchandise, clickPlayer, (byte) -1);
                                guis.get(player).getInventory().setItem(merchandise.SLOT, getLevelItemWithLore(merchandise, playerInfo.level, playerInfo));
                            }
                        } else {
                            if (inventoryClickEvent.getClick().isLeftClick()) {
                                int cost = merchandise.getLevelPrice(playerInfo.level);
                                IUser user = getShopManager().plugin.getUserManager().getUser(clickPlayer);
                                int orbs = user.getStatistic("ORBS");
                                if (cost > orbs) {
                                    new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_NOT_ENOUGH_CURRENCY").asKey().player(clickPlayer).sendPlayer();
                                    clickPlayer.playSound(
                                            clickPlayer.getLocation(),
                                            Sound.ENTITY_VILLAGER_NO,
                                            1.0F,
                                            0.8F
                                    );
                                    return;
                                }
                                clickPlayer.playSound(
                                        clickPlayer.getLocation(),
                                        Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                                        1.0F,
                                        1.2F
                                );
                                clickPlayer.getInventory().addItem(merchandise.getLevelItem(playerInfo.level));
                                if (playerInfo.level == playerInfo.maxLevel) {
                                    getShopManager().doubleAddPlayerData(merchandise, clickPlayer);
                                    guis.get(player).getInventory().setItem(merchandise.SLOT, getLevelItemWithLore(merchandise, playerInfo.level, playerInfo));
                                }
                                getShopManager().adjustOrbs(user, cost);
                            } else if (inventoryClickEvent.getClick().isRightClick()) {
                                clickPlayer.playSound(
                                        clickPlayer.getLocation(),
                                        Sound.UI_BUTTON_CLICK,
                                        1.0F,
                                        1.2F
                                );
                                getShopManager().changePlayerData(merchandise, clickPlayer, (byte) 1);
                                guis.get(player).getInventory().setItem(merchandise.SLOT, getLevelItemWithLore(merchandise, playerInfo.level, playerInfo));
                            }
                        }
                    });
                }
            });
        }
        guis.get(player).open(player);
    }

    /**
     * 字符串居中，空格补齐到指定总长度
     *
     * @param totalLength 最终固定总长度
     * @param text        原始字符串
     * @return 居中后空格补齐的字符串
     * @since 11+ 原版用 String.repeat，本实现兼容 Java8
     */
    public static String centerString(int totalLength, String text) {
        int len = text.length();
        if (len >= totalLength) {
            return text;
        }

        int totalPad = totalLength - len;
        int leftPad = totalPad / 2;
        int rightPad = totalPad - leftPad;

        return getBlank(leftPad) + text + getBlank(rightPad);
    }

    /**
     * 生成指定个数空格（兼容Java8，无需String.repeat）
     */
    private static String getBlank(int count) {
        if (count <= 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }

    /**
     * 1 开始的环形循环索引
     *
     * @param index  当前索引（从 1 开始）
     * @param length 最大长度（从 1 开始）
     * @return 循环后的正确索引（1 ~ length）
     */
    public static int loopIndex(int index, int length) {
        if (length <= 0) return 1;

        // 把 1 开始 → 转成 0 开始计算
        index--;

        // 安全取模（支持正负循环）
        int mod = index % length;
        int zeroBased = mod < 0 ? mod + length : mod;

        // 转回 1 开始
        return zeroBased + 1;
    }

    @EventHandler
    public void onNeteasePythonEvent(NeteasePythonEvent event) {
        if (event.getSystemName().equals("main") && event.getNamespace().equals("Xigua_common")) {
            Map<String, Object> data = event.getData();
            String eventName = event.getPyEventName();
            Player player = event.getPlayer();
            switch (eventName) {
                case "MouseWheelUp":
                case "MouseWheelDown":
                    if (data.size() == 1) return;
                    int slot = (int) data.get("slot");
                    Merchandise merchandise = ShopManager.getMerchandiseWithSlot(slot);
                    ShopManager.PlayerInfo playerInfo = getShopManager().playerData.get(player).get(merchandise);
                    if (merchandise != null && merchandise.isEnabled(player)) {
                        player.playSound(
                                player.getLocation(),
                                Sound.UI_BUTTON_CLICK,
                                1.0F,
                                1.2F
                        );
                        getShopManager().changePlayerData(merchandise, player, (byte) (eventName.equals("MouseWheelDown") ? 1 : -1));
                        guis.get(player).getInventory().setItem(merchandise.SLOT, getLevelItemWithLore(merchandise, playerInfo.level, playerInfo));
                    }
                    break;
                default:
                    break;
            }
        }
    }

}
