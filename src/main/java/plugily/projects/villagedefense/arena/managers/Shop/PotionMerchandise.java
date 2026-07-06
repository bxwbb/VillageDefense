package plugily.projects.villagedefense.arena.managers.Shop;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import static org.bukkit.potion.PotionEffectType.SPEED;

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

    public PotionMerchandise(int maxLevel, int slot, PotionEffectType potionEffectType, int basePrice, boolean isSplash) {
        super(maxLevel, slot);
        for (int i = 0; i < maxLevel; i++) {
            ItemStack itemStack = new ItemStack(isSplash ? Material.SPLASH_POTION :Material.POTION);
            PotionMeta potionMeta = (PotionMeta) itemStack.getItemMeta();
            PotionEffect poison = new PotionEffect(potionEffectType, 1200, i);
            potionMeta.addCustomEffect(poison, true);
            itemStack.setItemMeta(potionMeta);
            int bp = basePrice;
            if (ShopManager.fileConfiguration.contains("Potion." + potionEffectType.getName())) {
                bp = ShopManager.fileConfiguration.getInt("Potion." + potionEffectType.getName());
            }
            this.getMerchandiseList().add(new AUpgradableMerchandise(itemStack, bp * (i + 1) * (i + 1), getCNName(potionEffectType) + intToRoman(i + 1)));
        }
    }

    public static String getCNName(PotionEffectType potionEffectType) {
        if (potionEffectType == PotionEffectType.SPEED) {
            return "速度";
        }
        if (potionEffectType == PotionEffectType.SLOWNESS) {
            return "缓慢";
        }
        if (potionEffectType == PotionEffectType.MINING_FATIGUE) {
            return "急迫";
        }
        if (potionEffectType == PotionEffectType.STRENGTH) {
            return "力量";
        }
        if (potionEffectType == PotionEffectType.INSTANT_HEALTH) {
            return "瞬间治疗";
        }
        if (potionEffectType == PotionEffectType.INSTANT_DAMAGE) {
            return "瞬间伤害";
        }
        if (potionEffectType == PotionEffectType.JUMP_BOOST) {
            return "跳跃提升";
        }
        if (potionEffectType == PotionEffectType.REGENERATION) {
            return "生命恢复";
        }
        if (potionEffectType == PotionEffectType.RESISTANCE) {
            return "抗性提升";
        }
        if (potionEffectType == PotionEffectType.FIRE_RESISTANCE) {
            return "火焰抗性";
        }
        if (potionEffectType == PotionEffectType.WATER_BREATHING) {
            return "水下呼吸";
        }
        if (potionEffectType == PotionEffectType.INVISIBILITY) {
            return "隐身";
        }
        if (potionEffectType == PotionEffectType.BLINDNESS) {
            return "失明";
        }
        if (potionEffectType == PotionEffectType.NIGHT_VISION) {
            return "夜视";
        }
        if (potionEffectType == PotionEffectType.HUNGER) {
            return "饥饿";
        }
        if (potionEffectType == PotionEffectType.WEAKNESS) {
            return "虚弱";
        }
        if (potionEffectType == PotionEffectType.POISON) {
            return "中毒";
        }
        if (potionEffectType == PotionEffectType.WITHER) {
            return "凋零";
        }
        if (potionEffectType == PotionEffectType.HEALTH_BOOST) {
            return "生命提升";
        }
        if (potionEffectType == PotionEffectType.ABSORPTION) {
            return "伤害吸收";
        }
        if (potionEffectType == PotionEffectType.SATURATION) {
            return "饱和";
        }
        if (potionEffectType == PotionEffectType.GLOWING) {
            return "发光";
        }
        if (potionEffectType == PotionEffectType.LEVITATION) {
            return "漂浮";
        }
        if (potionEffectType == PotionEffectType.LUCK) {
            return "幸运";
        }
        if (potionEffectType == PotionEffectType.UNLUCK) {
            return "霉运";
        }
        if (potionEffectType == PotionEffectType.SLOW_FALLING) {
            return "缓降";
        }
        if (potionEffectType == PotionEffectType.DOLPHINS_GRACE) {
            return "海豚的恩惠";
        }
        if (potionEffectType == PotionEffectType.BAD_OMEN) {
            return "不祥之兆";
        }
        if (potionEffectType == PotionEffectType.HERO_OF_THE_VILLAGE) {
            return "村庄英雄";
        }
        return "未知";
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
