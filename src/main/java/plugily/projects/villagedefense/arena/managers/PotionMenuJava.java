package plugily.projects.villagedefense.arena.managers;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import plugily.projects.minigamesbox.api.user.IUser;
import plugily.projects.minigamesbox.classic.handlers.language.MessageBuilder;
import plugily.projects.minigamesbox.classic.utils.misc.complement.ComplementAccessor;
import plugily.projects.minigamesbox.inventory.normal.NormalFastInv;
import plugily.projects.villagedefense.arena.Arena;
import plugily.projects.villagedefense.arena.managers.PotionManager.PotionData;

import java.util.HashMap;
import java.util.List;

import static plugily.projects.villagedefense.arena.managers.PotionManager.potionPrices;

public class PotionMenuJava extends PotionMenu {

    public PotionMenuJava(PotionManager potionManager) {
        super(potionManager);
    }

    private ItemStack resetLore(ItemStack itemStack, Player player, PotionEffectType potionEffectType) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        StringBuilder stringBuilder = new StringBuilder();
        if (!getPotionManager().playerData.containsKey(player)) {
            getPotionManager().playerData.put(player, new HashMap<>());
        }
        if (!getPotionManager().playerData.get(player).containsKey(potionEffectType)) {
            getPotionManager().playerData.get(player).put(potionEffectType, new PotionManager.PotionData((byte) 0, (byte) 0));
        }
        PotionManager.PotionData potionData = getPotionManager().playerData.get(player).get(potionEffectType);
        for (int i = 1; i <= potionData.maxLevel + 1; i++) {
            if (i != 1)
                stringBuilder.append(new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_SEPARATOR").asKey().build());
            if ((potionData.level + 1) == i) {
                stringBuilder.append(new MessageBuilder("&5&l<").build());
            }
            stringBuilder.append(new MessageBuilder("&5&l" + intToRoman(i)).build());
            if ((potionData.level + 1) == i) {
                stringBuilder.append(new MessageBuilder("&5&l>").build());
            }
        }
        List<String> lore = ComplementAccessor.getComplement().getLore(itemMeta);
        lore.add(stringBuilder.toString());
        lore.add(ChatColor.GOLD + String.valueOf(potionPrices.get(potionEffectType) * (potionData.level + 1)) + " " + new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_CURRENCY").asKey().build());
        lore.add("");
        lore.add(new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_ITEM_CTRL_LORE").asKey().build());
        ComplementAccessor.getComplement().setLore(itemMeta, lore);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private ItemStack getItem(PotionEffectType potionEffectType, int level) {
        ItemStack itemStack = new ItemStack(Material.POTION);
        PotionMeta potionMeta = (PotionMeta) itemStack.getItemMeta();
        PotionEffect poison = new PotionEffect(potionEffectType, 1200, level);
        potionMeta.addCustomEffect(poison, true);
        itemStack.setItemMeta(potionMeta);
        return itemStack;
    }

    public void addNewPotionItem(Arena arena, PotionEffectType potionEffectType, NormalFastInv gui, int slot, int prices, Player player) {
        potionPrices.put(potionEffectType, prices);
        if (!getPotionManager().playerData.containsKey(player)) {
            getPotionManager().playerData.put(player, new HashMap<>());
        }
        if (!getPotionManager().playerData.get(player).containsKey(potionEffectType)) {
            getPotionManager().playerData.get(player).put(potionEffectType, new PotionData((byte) 0, (byte) 0));
        }
        PotionData potionData = getPotionManager().playerData.get(player).get(potionEffectType);
        ItemStack itemStack = getItem(potionEffectType, potionData.level);
        resetLore(itemStack, player, potionEffectType);
        gui.setItem(slot, itemStack, inventoryClickEvent -> {
            inventoryClickEvent.setCancelled(true);
            if (!(inventoryClickEvent.getWhoClicked() instanceof Player)) return;
            Player clickPlayer = (Player) inventoryClickEvent.getWhoClicked();
            if (!arena.getPlayers().contains(clickPlayer)) {
                return;
            }
            ItemStack clickItemStack = inventoryClickEvent.getCurrentItem();
            if (clickItemStack == null) return;
            if (inventoryClickEvent.getClick().isLeftClick()) {
                int cost = potionPrices.get(potionEffectType);
                IUser user = getPotionManager().plugin.getUserManager().getUser(clickPlayer);
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
                clickPlayer.getInventory().addItem(getItem(potionEffectType, getPotionManager().playerData.get(player).get(potionEffectType).level));
                getPotionManager().playerData.get(player).get(potionEffectType).maxLevel++;
                gui.getInventory().setItem(slot, resetLore(getItem(potionEffectType, getPotionManager().playerData.get(player).get(potionEffectType).level), clickPlayer, potionEffectType));
                getPotionManager().shopManager.adjustOrbs(user, cost);
            } else if (inventoryClickEvent.getClick().isRightClick()) {
                getPotionManager().addMaxStackType(potionEffectType, clickPlayer);
                gui.getInventory().setItem(slot, resetLore(getItem(potionEffectType, getPotionManager().playerData.get(player).get(potionEffectType).level), clickPlayer, potionEffectType));
                clickPlayer.playSound(
                        clickPlayer.getLocation(),
                        Sound.UI_BUTTON_CLICK,
                        0.9F,
                        1.1F
                );
            }
        });
    }

    public static String intToRoman(int num) {
        if (num < 1 || num > 3999) {
            return "";
        }

        int[] values = {
                1000, 900, 500, 400,
                100, 90, 50, 40,
                10, 9, 5, 4,
                1
        };
        String[] symbols = {
                "M", "CM", "D", "CD",
                "C", "XC", "L", "XL",
                "X", "IX", "V", "IV",
                "I"
        };

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length && num > 0; i++) {
            while (num >= values[i]) {
                sb.append(symbols[i]);
                num -= values[i];
            }
        }
        return sb.toString();
    }

}
