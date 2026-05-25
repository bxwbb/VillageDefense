package plugily.projects.villagedefense.arena.managers.Shop;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class PotionMerchandise extends UpgradableMerchandise {

    public PotionMerchandise(int maxLevel, int slot, PotionEffectType potionEffectType, int basePrice) {
        super(maxLevel, slot);
        for (int i = 0; i < maxLevel; i++) {
            ItemStack itemStack = new ItemStack(Material.POTION);
            PotionMeta potionMeta = (PotionMeta) itemStack.getItemMeta();
            PotionEffect poison = new PotionEffect(potionEffectType, 1200, i);
            potionMeta.addCustomEffect(poison, true);
            itemStack.setItemMeta(potionMeta);
            int bp = basePrice;
            if (ShopManager.fileConfiguration.contains("Potion." + potionEffectType.getName())) {
                bp = ShopManager.fileConfiguration.getInt("Potion." + potionEffectType.getName());
            }
            this.getMerchandiseList().add(new AUpgradableMerchandise(itemStack, bp * (i + 1) * (i + 1), intToRoman(i + 1)));
        }
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
