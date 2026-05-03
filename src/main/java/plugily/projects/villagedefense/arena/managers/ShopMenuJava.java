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
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import plugily.projects.minigamesbox.api.user.IUser;
import plugily.projects.minigamesbox.classic.handlers.language.MessageBuilder;
import plugily.projects.minigamesbox.classic.utils.misc.complement.ComplementAccessor;
import plugily.projects.minigamesbox.inventory.normal.NormalFastInv;
import plugily.projects.villagedefense.arena.Arena;

import java.util.List;

import static plugily.projects.villagedefense.arena.managers.ShopManager.prices;

/**
 * Created by Tom on 16/08/2014.
 */
public class ShopMenuJava extends ShopMenu {

    private NormalFastInv gui;

    public ShopMenuJava(Arena arena, ShopManager shopManager) {
        super(shopManager);
    }

    private ItemStack getItem(int slot, Player player) {
        byte stackType = getShopManager().playerData.get(player)[slot * 2 + 1];
        ItemStack itemStack = new ItemStack(Material.AIR);
        switch (slot) {
            case 0:
                switch (stackType) {
                    case 0:
                        itemStack = new ItemStack(Material.LEATHER_HELMET);
                        break;
                    case 1:
                        itemStack = new ItemStack(Material.IRON_HELMET);
                        break;
                    case 2:
                        itemStack = new ItemStack(Material.DIAMOND_HELMET);
                        break;
                    case 3:
                        itemStack = new ItemStack(Material.NETHERITE_HELMET);
                }
                break;
            case 1:
                switch (stackType) {
                    case 0:
                        itemStack = new ItemStack(Material.LEATHER_CHESTPLATE);
                        break;
                    case 1:
                        itemStack = new ItemStack(Material.IRON_CHESTPLATE);
                        break;
                    case 2:
                        itemStack = new ItemStack(Material.DIAMOND_CHESTPLATE);
                        break;
                    case 3:
                        itemStack = new ItemStack(Material.NETHERITE_CHESTPLATE);
                        break;
                }
                break;
            case 2:
                switch (stackType) {
                    case 0:
                        itemStack = new ItemStack(Material.LEATHER_LEGGINGS);
                        break;
                    case 1:
                        itemStack = new ItemStack(Material.IRON_LEGGINGS);
                        break;
                    case 2:
                        itemStack = new ItemStack(Material.DIAMOND_LEGGINGS);
                        break;
                    case 3:
                        itemStack = new ItemStack(Material.NETHERITE_LEGGINGS);
                        break;
                }
                break;
            case 3:
                switch (stackType) {
                    case 0:
                        itemStack = new ItemStack(Material.LEATHER_BOOTS);
                        break;
                    case 1:
                        itemStack = new ItemStack(Material.IRON_BOOTS);
                        break;
                    case 2:
                        itemStack = new ItemStack(Material.DIAMOND_BOOTS);
                        break;
                    case 3:
                        itemStack = new ItemStack(Material.NETHERITE_BOOTS);
                        break;
                }
                break;
            case 4:
                switch (stackType) {
                    case 0:
                        itemStack = new ItemStack(Material.STONE_SWORD);
                        break;
                    case 1:
                        itemStack = new ItemStack(Material.IRON_SWORD);
                        break;
                    case 2:
                        itemStack = new ItemStack(Material.DIAMOND_SWORD);
                        break;
                    case 3:
                        itemStack = new ItemStack(Material.NETHERITE_SWORD);
                        break;
                }
                break;
            case 5:
                switch (stackType) {
                    case 0:
                    case 1:
                        itemStack = new ItemStack(Material.BARRIER);
                        break;
                    case 2:
                        itemStack = new ItemStack(Material.TRIDENT);
                        break;
                    case 3:
                        itemStack = new ItemStack(Material.TRIDENT);
                        ItemMeta itemMeta = itemStack.getItemMeta();
                        itemMeta.addEnchant(Enchantment.RIPTIDE, 1, true);
                        itemMeta.addEnchant(Enchantment.LOYALTY, 1, true);
                        itemStack.setItemMeta(itemMeta);
                }
                break;
            default:
                itemStack = new ItemStack(Material.AIR);
                break;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.addEnchant(Enchantment.MENDING, 1, true);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private ItemStack getHadDataItem(int slot, Player player) {
        byte stackType = getShopManager().playerData.get(player)[slot * 2 + 1];
        byte maxStackType = getShopManager().playerData.get(player)[slot * 2];
        ItemStack itemStack = getItem(slot, player);
        StringBuilder stringBuilder = new StringBuilder();
        if (maxStackType >= 0) {
            if (slot == 4) {
                if (stackType == 0) {
                    stringBuilder.append(new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_USTONE").asKey().build());
                } else {
                    stringBuilder.append(new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_STONE").asKey().build());
                }
            } else {
                if (stackType == 0) {
                    stringBuilder.append(new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_ULEATHER").asKey().build());
                } else {
                    stringBuilder.append(new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_LEATHER").asKey().build());
                }
            }
        }
        if (maxStackType >= 1) {
            stringBuilder.append(new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_SEPARATOR").asKey().build());
            if (stackType == 1) {
                stringBuilder.append(new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_UIRON").asKey().build());
            } else {
                stringBuilder.append(new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_IRON").asKey().build());
            }
        }
        if (maxStackType >= 2) {
            if (slot == 5) {
                if (stackType == 2) {
                    stringBuilder.append(new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_UTRIDENT").asKey().build());
                } else {
                    stringBuilder.append(new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_TRIDENT").asKey().build());
                }
            } else {
                stringBuilder.append(new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_SEPARATOR").asKey().build());
                if (stackType == 2) {
                    stringBuilder.append(new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_UDIAMOND").asKey().build());
                } else {
                    stringBuilder.append(new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_DIAMOND").asKey().build());
                }
            }
        }
        if (maxStackType >= 3) {
            stringBuilder.append(new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_SEPARATOR").asKey().build());
            if (slot == 5) {
                if (stackType == 3) {
                    stringBuilder.append(new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_UENCHANTED_TRIDENT").asKey().build());
                } else {
                    stringBuilder.append(new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_ENCHANTED_TRIDENT").asKey().build());
                }
            } else {
                if (stackType == 3) {
                    stringBuilder.append(new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_UNETHERITE").asKey().build());
                } else {
                    stringBuilder.append(new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_NETHERITE").asKey().build());
                }
            }
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        List<String> lore = ComplementAccessor.getComplement().getLore(itemMeta);
        if (!(slot == 5 && maxStackType <= 1)) {
            lore.add(stringBuilder.toString());
        }
        if (prices.containsKey(itemStack.getType()))
            lore.add(ChatColor.GOLD + String.valueOf(prices.get(itemStack.getType())) + " " + new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_CURRENCY").asKey().build());
        lore.add("");
        lore.add(new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_ITEM_CTRL_LORE").asKey().build());
        ComplementAccessor.getComplement().setLore(itemMeta, lore);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    public void registerShop() {
        gui = new NormalFastInv(54, new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_GUI").asKey().build());
        gui.addOpenHandler(inventoryOpenEvent -> {
            if (!(inventoryOpenEvent.getPlayer() instanceof Player)) return;
            Player player = (Player) inventoryOpenEvent.getPlayer();
            for (int slot = 0; slot < 6; slot++) {
                int finalSlot = slot;
                gui.setItem(slot, getHadDataItem(slot, player), inventoryClickEvent -> {
                    inventoryClickEvent.setCancelled(true);
                    if (!(inventoryClickEvent.getWhoClicked() instanceof Player)) return;
                    Player clickPlayer = (Player) inventoryClickEvent.getWhoClicked();
                    if (!getShopManager().arena.getPlayers().contains(clickPlayer)) {
                        return;
                    }
                    ItemStack clickItemStack = inventoryClickEvent.getCurrentItem();
                    if (clickItemStack == null) return;
                    if (!prices.containsKey(clickItemStack.getType())) return;
                    if (inventoryClickEvent.getClick().isLeftClick()) {
                        int cost = prices.get(clickItemStack.getType());
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
                        clickPlayer.getInventory().addItem(getItem(finalSlot, clickPlayer));
                        if (getShopManager().playerData.get(clickPlayer)[finalSlot * 2] != 3) {
                            getShopManager().doubleAddPlayerData(finalSlot, clickPlayer);
                            if (finalSlot == 4 && getShopManager().playerData.get(clickPlayer)[finalSlot * 2] == 2) {
                                getShopManager().setPlayerData(10, clickPlayer, (byte) 2);
                                gui.getInventory().setItem(5, getHadDataItem(5, clickPlayer));
                            }
                            gui.getInventory().setItem(finalSlot, getHadDataItem(finalSlot, clickPlayer));
                        }
                        getShopManager().adjustOrbs(user, cost);
                    } else if (inventoryClickEvent.getClick().isRightClick()) {
                        getShopManager().addMaxStackType(finalSlot, clickPlayer);
                        gui.getInventory().setItem(finalSlot, getHadDataItem(finalSlot, clickPlayer));
                        clickPlayer.playSound(
                                clickPlayer.getLocation(),
                                Sound.UI_BUTTON_CLICK,
                                0.9F,
                                1.1F
                        );
                    }
                });
            }
            for (int slot = 9; slot < 9 + 5; slot++) {
                ItemStack itemStack = new ItemStack(Material.AIR);
                switch (slot) {
                    case 9:
                        itemStack = new ItemStack(Material.BOW);
                        break;
                    case 10:
                        itemStack = new ItemStack(Material.CROSSBOW);
                        ItemMeta meta = itemStack.getItemMeta();
                        meta.addEnchant(Enchantment.MULTISHOT, 1, true);
                        meta.addEnchant(Enchantment.QUICK_CHARGE, 1, true);
                        itemStack.setItemMeta(meta);
                        break;
                    case 11:
                        itemStack = new ItemStack(Material.ARROW, 16);
                        break;
                    case 12:
                        itemStack = new ItemStack(Material.TOTEM_OF_UNDYING);
                        break;
                    case 13:
                        itemStack = new ItemStack(Material.LAPIS_ORE);
                        break;
                }
                ItemMeta itemMeta = itemStack.getItemMeta();
                if (slot < (9 + 2)) itemMeta.addEnchant(Enchantment.MENDING, 1, true);
                List<String> lore = ComplementAccessor.getComplement().getLore(itemMeta);
                if (prices.containsKey(itemStack.getType()))
                    lore.add(ChatColor.GOLD + String.valueOf(prices.get(itemStack.getType())) + " " + new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_CURRENCY").asKey().build());
                ComplementAccessor.getComplement().setLore(itemMeta, lore);
                itemStack.setItemMeta(itemMeta);
                int finalSlot = slot;
                gui.setItem(slot, itemStack, inventoryClickEvent -> {
                    inventoryClickEvent.setCancelled(true);
                    if (!(inventoryClickEvent.getWhoClicked() instanceof Player)) return;
                    Player clickPlayer = (Player) inventoryClickEvent.getWhoClicked();
                    if (!getShopManager().arena.getPlayers().contains(clickPlayer)) {
                        return;
                    }
                    ItemStack clickItemStack = inventoryClickEvent.getCurrentItem();
                    if (clickItemStack == null) return;
                    if (!prices.containsKey(clickItemStack.getType())) return;
                    int cost = prices.get(clickItemStack.getType());
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
                    getShopManager().giveItem(player, getItem(finalSlot, clickPlayer));
                    getShopManager().adjustOrbs(user, cost);
                });
            }
            // 瞬间治疗 价格20
            getShopManager().potionManager.addNewPotionItem(getShopManager().arena, PotionEffectType.INSTANT_HEALTH, gui, 18, 20, player);
            // 生命恢复 价格18
            getShopManager().potionManager.addNewPotionItem(getShopManager().arena, PotionEffectType.REGENERATION, gui, 19, 18, player);
            // 加速 价格16
            getShopManager().potionManager.addNewPotionItem(getShopManager().arena, PotionEffectType.SPEED, gui, 20, 16, player);
            // 跳跃提升 价格14
            getShopManager().potionManager.addNewPotionItem(getShopManager().arena, PotionEffectType.JUMP_BOOST, gui, 21, 14, player);
            // 抗性提升 价格28
            getShopManager().potionManager.addNewPotionItem(getShopManager().arena, PotionEffectType.REGENERATION, gui, 22, 28, player);
            // 隐身 价格30
            getShopManager().potionManager.addNewPotionItem(getShopManager().arena, PotionEffectType.INVISIBILITY, gui, 23, 30, player);
            // 力量 价格26
            getShopManager().potionManager.addNewPotionItem(getShopManager().arena, PotionEffectType.STRENGTH, gui, 24, 26, player);
        });
    }

    @Override
    public boolean isReady() {
        return gui == null;
    }

    @Override
    public void open(Player player) {
        gui.open(player);
    }

}
