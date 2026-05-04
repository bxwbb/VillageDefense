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

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;
import plugily.projects.minigamesbox.api.user.IUser;
import plugily.projects.minigamesbox.classic.handlers.language.MessageBuilder;
import plugily.projects.minigamesbox.classic.utils.configuration.ConfigUtils;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.arena.Arena;

import java.util.*;
import java.util.function.Consumer;

/**
 * Created by Tom on 16/08/2014.
 */
public class ShopManager {

    public final Main plugin;
    final Arena arena;
    private Consumer<Player> openMenuConsumer;
    private final ShopMenu shopMenu;

    public static final List<Merchandise> merchandises = new ArrayList<>();
    public final Map<Player, Map<Merchandise, PlayerInfo>> playerData = new HashMap<>();

    public static class PlayerInfo {
        public int level;
        public int maxLevel;

        public PlayerInfo(int level, int maxLevel) {
            this.level = level;
            this.maxLevel = maxLevel;
        }
    }

    static {
        merchandises.add(new UpgradableMerchandise(4, 0, Arrays.asList(
                new UpgradableMerchandise.AUpgradableMerchandise(new ItemStack(Material.LEATHER_HELMET), 80, "皮革"),
                new UpgradableMerchandise.AUpgradableMerchandise(new ItemStack(Material.IRON_HELMET), 180, "铁质"),
                new UpgradableMerchandise.AUpgradableMerchandise(new ItemStack(Material.DIAMOND_HELMET), 380, "钻石"),
                new UpgradableMerchandise.AUpgradableMerchandise(new ItemStack(Material.NETHERITE_HELMET), 750, "下界合金")
        )));
        merchandises.add(new UpgradableMerchandise(4, 1, Arrays.asList(
                new UpgradableMerchandise.AUpgradableMerchandise(new ItemStack(Material.LEATHER_CHESTPLATE), 100, "皮革"),
                new UpgradableMerchandise.AUpgradableMerchandise(new ItemStack(Material.IRON_CHESTPLATE), 220, "铁质"),
                new UpgradableMerchandise.AUpgradableMerchandise(new ItemStack(Material.DIAMOND_CHESTPLATE), 450, "钻石"),
                new UpgradableMerchandise.AUpgradableMerchandise(new ItemStack(Material.NETHERITE_CHESTPLATE), 850, "下界合金")
        )));
        merchandises.add(new UpgradableMerchandise(4, 2, Arrays.asList(
                new UpgradableMerchandise.AUpgradableMerchandise(new ItemStack(Material.LEATHER_LEGGINGS), 90, "皮革"),
                new UpgradableMerchandise.AUpgradableMerchandise(new ItemStack(Material.IRON_LEGGINGS), 200, "铁质"),
                new UpgradableMerchandise.AUpgradableMerchandise(new ItemStack(Material.DIAMOND_LEGGINGS), 420, "钻石"),
                new UpgradableMerchandise.AUpgradableMerchandise(new ItemStack(Material.NETHERITE_LEGGINGS), 800, "下界合金")
        )));
        merchandises.add(new UpgradableMerchandise(4, 3, Arrays.asList(
                new UpgradableMerchandise.AUpgradableMerchandise(new ItemStack(Material.LEATHER_BOOTS), 70, "皮革"),
                new UpgradableMerchandise.AUpgradableMerchandise(new ItemStack(Material.IRON_BOOTS), 160, "铁质"),
                new UpgradableMerchandise.AUpgradableMerchandise(new ItemStack(Material.DIAMOND_BOOTS), 350, "钻石"),
                new UpgradableMerchandise.AUpgradableMerchandise(new ItemStack(Material.NETHERITE_BOOTS), 700, "下界合金")
        )));
        merchandises.add(new UpgradableMerchandise(4, 4, Arrays.asList(
                new UpgradableMerchandise.AUpgradableMerchandise(new ItemStack(Material.STONE_SWORD), 60, "石质"),
                new UpgradableMerchandise.AUpgradableMerchandise(new ItemStack(Material.IRON_SWORD), 150, "铁质"),
                new UpgradableMerchandise.AUpgradableMerchandise(new ItemStack(Material.DIAMOND_SWORD), 360, "钻石"),
                new UpgradableMerchandise.AUpgradableMerchandise(new ItemStack(Material.NETHERITE_SWORD), 720, "下界合金")
        )));
        ItemStack tridentEnchanted = new ItemStack(Material.TRIDENT);
        ItemMeta tridentMeta = tridentEnchanted.getItemMeta();
        if (tridentMeta != null) {
            tridentMeta.addEnchant(Enchantment.RIPTIDE, 1, true);
            tridentMeta.addEnchant(Enchantment.LOYALTY, 1, true);
            tridentEnchanted.setItemMeta(tridentMeta);
        }
        merchandises.add(new UpgradableMerchandise(2, 5, Arrays.asList(
                new UpgradableMerchandise.AUpgradableMerchandise(new ItemStack(Material.TRIDENT), 260, "普通三叉戟"),
                new UpgradableMerchandise.AUpgradableMerchandise(tridentEnchanted, 520, "激流忠诚三叉戟")
        )));
        ItemStack bowPower1 = new ItemStack(Material.BOW);
        ItemMeta bowPower1Meta = bowPower1.getItemMeta();
        if (bowPower1Meta != null) {
            bowPower1Meta.addEnchant(Enchantment.POWER, 1, true);
            bowPower1.setItemMeta(bowPower1Meta);
        }
        ItemStack bowInfinity = new ItemStack(Material.BOW);
        ItemMeta bowInfinityMeta = bowInfinity.getItemMeta();
        if (bowInfinityMeta != null) {
            bowInfinityMeta.addEnchant(Enchantment.POWER, 1, true);
            bowInfinityMeta.addEnchant(Enchantment.INFINITY, 1, true);
            bowInfinity.setItemMeta(bowInfinityMeta);
        }
        merchandises.add(new UpgradableMerchandise(3, 6, Arrays.asList(
                new UpgradableMerchandise.AUpgradableMerchandise(new ItemStack(Material.BOW), 120, "普通弓"),
                new UpgradableMerchandise.AUpgradableMerchandise(bowPower1, 280, "力量I弓"),
                new UpgradableMerchandise.AUpgradableMerchandise(bowInfinity, 600, "无限力量弓")
        )));
        ItemStack crossbowNormal = new ItemStack(Material.CROSSBOW);
        ItemStack crossbowPierce1 = new ItemStack(Material.CROSSBOW);
        ItemMeta pierceMeta = crossbowPierce1.getItemMeta();
        if (pierceMeta != null) {
            pierceMeta.addEnchant(Enchantment.PIERCING, 1, true);
            crossbowPierce1.setItemMeta(pierceMeta);
        }
        ItemStack crossbowFull = new ItemStack(Material.CROSSBOW);
        ItemMeta fullMeta = crossbowFull.getItemMeta();
        if (fullMeta != null) {
            fullMeta.addEnchant(Enchantment.PIERCING, 1, true);
            fullMeta.addEnchant(Enchantment.MULTISHOT, 1, true);
            crossbowFull.setItemMeta(fullMeta);
        }
        merchandises.add(new UpgradableMerchandise(3, 7, Arrays.asList(
                new UpgradableMerchandise.AUpgradableMerchandise(crossbowNormal, 150, "普通弩"),
                new UpgradableMerchandise.AUpgradableMerchandise(crossbowPierce1, 320, "穿透I弩"),
                new UpgradableMerchandise.AUpgradableMerchandise(crossbowFull, 650, "穿透多重弩")
        )));
        ItemStack arrowNormal = new ItemStack(Material.ARROW);
        ItemStack arrowSlowness = new ItemStack(Material.TIPPED_ARROW);
        PotionMeta slowMeta = (PotionMeta) arrowSlowness.getItemMeta(); // 强转成PotionMeta
        if (slowMeta != null) {
            slowMeta.setBasePotionData(new PotionData(PotionType.SLOWNESS)); // 核心方法
            slowMeta.setDisplayName("迟缓药箭");
            arrowSlowness.setItemMeta(slowMeta);
        }
        ItemStack arrowPoison = new ItemStack(Material.TIPPED_ARROW);
        PotionMeta poisonMeta = (PotionMeta) arrowPoison.getItemMeta();
        if (poisonMeta != null) {
            poisonMeta.setBasePotionData(new PotionData(PotionType.POISON));
            poisonMeta.setDisplayName("剧毒药箭");
            arrowPoison.setItemMeta(poisonMeta);
        }
        merchandises.add(new UpgradableMerchandise(3, 8, Arrays.asList(
                new UpgradableMerchandise.AUpgradableMerchandise(arrowNormal, 35, "普通箭"),
                new UpgradableMerchandise.AUpgradableMerchandise(arrowSlowness, 160, "迟缓药箭"),
                new UpgradableMerchandise.AUpgradableMerchandise(arrowPoison, 320, "剧毒药箭")
        )));
        merchandises.add(new PotionMerchandise(10, 9, PotionEffectType.INSTANT_HEALTH, 200));
        merchandises.add(new PotionMerchandise(10, 10, PotionEffectType.SPEED, 180));
        merchandises.add(new PotionMerchandise(10, 11, PotionEffectType.SLOWNESS, 170));
        merchandises.add(new PotionMerchandise(10, 12, PotionEffectType.STRENGTH, 220));
        merchandises.add(new PotionMerchandise(10, 13, PotionEffectType.WEAKNESS, 160));
        merchandises.add(new PotionMerchandise(10, 14, PotionEffectType.REGENERATION, 210));
        merchandises.add(new PotionMerchandise(10, 15, PotionEffectType.JUMP_BOOST, 175));
        merchandises.add(new PotionMerchandise(10, 16, PotionEffectType.INVISIBILITY, 260));
        merchandises.add(new PotionMerchandise(10, 17, PotionEffectType.FIRE_RESISTANCE, 240));
        merchandises.add(new UpgradableMerchandise(13, 18, Arrays.asList(
                new UpgradableMerchandise.AUpgradableMerchandise(createEnchBook(Enchantment.PROTECTION, 1), 220, "保护 I"),
                new UpgradableMerchandise.AUpgradableMerchandise(createEnchBook(Enchantment.FIRE_PROTECTION, 1), 230, "火焰保护 I"),
                new UpgradableMerchandise.AUpgradableMerchandise(createEnchBook(Enchantment.PROJECTILE_PROTECTION, 1), 230, "弹射物保护 I"),
                new UpgradableMerchandise.AUpgradableMerchandise(createEnchBook(Enchantment.SHARPNESS, 1), 200, "锋利 I"),
                new UpgradableMerchandise.AUpgradableMerchandise(createEnchBook(Enchantment.KNOCKBACK, 1), 190, "击退 I"),
                new UpgradableMerchandise.AUpgradableMerchandise(createEnchBook(Enchantment.FIRE_ASPECT, 1), 210, "火焰附加 I"),
                new UpgradableMerchandise.AUpgradableMerchandise(createEnchBook(Enchantment.POWER, 1), 200, "力量 I"),
                new UpgradableMerchandise.AUpgradableMerchandise(createEnchBook(Enchantment.KNOCKBACK, 1), 190, "冲击 I"),
                new UpgradableMerchandise.AUpgradableMerchandise(createEnchBook(Enchantment.FLAME, 1), 210, "火矢 I"),
                new UpgradableMerchandise.AUpgradableMerchandise(createEnchBook(Enchantment.PIERCING, 1), 220, "穿透 I"),
                new UpgradableMerchandise.AUpgradableMerchandise(createEnchBook(Enchantment.MULTISHOT, 1), 260, "多重射击 I"),
                new UpgradableMerchandise.AUpgradableMerchandise(createEnchBook(Enchantment.UNBREAKING, 1), 180, "耐久 I"),
                new UpgradableMerchandise.AUpgradableMerchandise(createEnchBook(Enchantment.MENDING, 1), 380, "经验修补 I")
        ), true));
        ItemStack fishingRod = new ItemStack(Material.FISHING_ROD);
        ItemStack enderPearl = new ItemStack(Material.ENDER_PEARL);
        ItemStack totem = new ItemStack(Material.TOTEM_OF_UNDYING);

        merchandises.add(new UpgradableMerchandise(3,19,Arrays.asList(
                new UpgradableMerchandise.AUpgradableMerchandise(fishingRod,120,"钓鱼竿"),
                new UpgradableMerchandise.AUpgradableMerchandise(enderPearl,350,"末影珍珠"),
                new UpgradableMerchandise.AUpgradableMerchandise(totem,680,"不死图腾")
        )));
        ItemStack normalShield = new ItemStack(Material.SHIELD);

        ItemStack coloredShield = new ItemStack(Material.SHIELD);
        ItemMeta meta1 = coloredShield.getItemMeta();
        if(meta1 != null){
            meta1.addEnchant(Enchantment.UNBREAKING,1,true);
            coloredShield.setItemMeta(meta1);
        }

        ItemStack advancedShield = new ItemStack(Material.SHIELD);
        ItemMeta meta2 = advancedShield.getItemMeta();
        if(meta2 != null){
            meta2.addEnchant(Enchantment.UNBREAKING,2,true);
            meta2.addEnchant(Enchantment.MENDING,1,true);
            advancedShield.setItemMeta(meta2);
        }

        merchandises.add(new UpgradableMerchandise(3,20,Arrays.asList(
                new UpgradableMerchandise.AUpgradableMerchandise(normalShield,140,"普通盾牌"),
                new UpgradableMerchandise.AUpgradableMerchandise(coloredShield,260,"精致装饰盾牌"),
                new UpgradableMerchandise.AUpgradableMerchandise(advancedShield,480,"满级附魔盾牌")
        )));
        ItemStack book = new ItemStack(Material.BOOK);
        ItemStack lapis = new ItemStack(Material.LAPIS_LAZULI);

        merchandises.add(new UpgradableMerchandise(2,21,Arrays.asList(
                new UpgradableMerchandise.AUpgradableMerchandise(book,40,"普通书本"),
                new UpgradableMerchandise.AUpgradableMerchandise(lapis,90,"青金石")
        )));
        ItemStack apple = new ItemStack(Material.APPLE);
        ItemStack bakedPotato = new ItemStack(Material.BAKED_POTATO);
        ItemStack goldenApple = new ItemStack(Material.GOLDEN_APPLE);

        ItemStack beef = new ItemStack(Material.BEEF);
        ItemStack cookedBeef = new ItemStack(Material.COOKED_BEEF);
        ItemStack enchGoldenApple = new ItemStack(Material.ENCHANTED_GOLDEN_APPLE);

        ItemStack chicken = new ItemStack(Material.CHICKEN);
        ItemStack cookedChicken = new ItemStack(Material.COOKED_CHICKEN);
        ItemStack pumpkinPie = new ItemStack(Material.PUMPKIN_PIE);

        ItemStack bread = new ItemStack(Material.BREAD);
        ItemStack carrot = new ItemStack(Material.CARROT);
        ItemStack goldenCarrot = new ItemStack(Material.GOLDEN_CARROT);

        ItemStack porkchop = new ItemStack(Material.PORKCHOP);
        ItemStack cookedPorkchop = new ItemStack(Material.COOKED_PORKCHOP);
        ItemStack mushroomStew = new ItemStack(Material.MUSHROOM_STEW);

        ItemStack potato = new ItemStack(Material.POTATO);
        ItemStack sweetBerries = new ItemStack(Material.SWEET_BERRIES);
        ItemStack rabbitStew = new ItemStack(Material.RABBIT_STEW);

        ItemStack rabbit = new ItemStack(Material.RABBIT);
        ItemStack cookedRabbit = new ItemStack(Material.COOKED_RABBIT);
        ItemStack honeyBottle = new ItemStack(Material.HONEY_BOTTLE);

        ItemStack cod = new ItemStack(Material.COD);
        ItemStack cookedCod = new ItemStack(Material.COOKED_COD);
        ItemStack pufferfish = new ItemStack(Material.PUFFERFISH);

        ItemStack salmon = new ItemStack(Material.SALMON);
        ItemStack cookedSalmon = new ItemStack(Material.COOKED_SALMON);
        ItemStack tropicalFish = new ItemStack(Material.TROPICAL_FISH);

        ItemStack melonSlice = new ItemStack(Material.MELON_SLICE);
        ItemStack driedKelp = new ItemStack(Material.DRIED_KELP);
        ItemStack cookie = new ItemStack(Material.COOKIE);

        ItemStack beetroot = new ItemStack(Material.BEETROOT);
        ItemStack beetrootSoup = new ItemStack(Material.BEETROOT_SOUP);
        ItemStack cake = new ItemStack(Material.CAKE);

        merchandises.add(new UpgradableMerchandise(3,27,Arrays.asList(
                new UpgradableMerchandise.AUpgradableMerchandise(apple,60,"苹果"),
                new UpgradableMerchandise.AUpgradableMerchandise(bakedPotato,120,"烤土豆"),
                new UpgradableMerchandise.AUpgradableMerchandise(goldenApple,450,"金苹果")
        )));
        merchandises.add(new UpgradableMerchandise(3,28,Arrays.asList(
                new UpgradableMerchandise.AUpgradableMerchandise(beef,50,"生牛肉"),
                new UpgradableMerchandise.AUpgradableMerchandise(cookedBeef,130,"熟牛排"),
                new UpgradableMerchandise.AUpgradableMerchandise(enchGoldenApple,680,"附魔金苹果")
        )));
        merchandises.add(new UpgradableMerchandise(3,29,Arrays.asList(
                new UpgradableMerchandise.AUpgradableMerchandise(chicken,45,"生鸡肉"),
                new UpgradableMerchandise.AUpgradableMerchandise(cookedChicken,110,"熟鸡肉"),
                new UpgradableMerchandise.AUpgradableMerchandise(pumpkinPie,280,"南瓜派")
        )));
        merchandises.add(new UpgradableMerchandise(3,30,Arrays.asList(
                new UpgradableMerchandise.AUpgradableMerchandise(bread,70,"面包"),
                new UpgradableMerchandise.AUpgradableMerchandise(carrot,55,"胡萝卜"),
                new UpgradableMerchandise.AUpgradableMerchandise(goldenCarrot,320,"金胡萝卜")
        )));
        merchandises.add(new UpgradableMerchandise(3,31,Arrays.asList(
                new UpgradableMerchandise.AUpgradableMerchandise(porkchop,48,"生猪排"),
                new UpgradableMerchandise.AUpgradableMerchandise(cookedPorkchop,125,"熟猪排"),
                new UpgradableMerchandise.AUpgradableMerchandise(mushroomStew,240,"蘑菇煲")
        )));
        merchandises.add(new UpgradableMerchandise(3,32,Arrays.asList(
                new UpgradableMerchandise.AUpgradableMerchandise(potato,40,"土豆"),
                new UpgradableMerchandise.AUpgradableMerchandise(sweetBerries,65,"甜浆果"),
                new UpgradableMerchandise.AUpgradableMerchandise(rabbitStew,260,"兔肉煲")
        )));
        merchandises.add(new UpgradableMerchandise(3,33,Arrays.asList(
                new UpgradableMerchandise.AUpgradableMerchandise(rabbit,42,"生兔肉"),
                new UpgradableMerchandise.AUpgradableMerchandise(cookedRabbit,115,"熟兔肉"),
                new UpgradableMerchandise.AUpgradableMerchandise(honeyBottle,220,"蜂蜜瓶")
        )));
        merchandises.add(new UpgradableMerchandise(3,34,Arrays.asList(
                new UpgradableMerchandise.AUpgradableMerchandise(cod,44,"生鳕鱼"),
                new UpgradableMerchandise.AUpgradableMerchandise(cookedCod,105,"熟鳕鱼"),
                new UpgradableMerchandise.AUpgradableMerchandise(pufferfish,180,"河豚")
        )));
        merchandises.add(new UpgradableMerchandise(3,35,Arrays.asList(
                new UpgradableMerchandise.AUpgradableMerchandise(salmon,46,"生三文鱼"),
                new UpgradableMerchandise.AUpgradableMerchandise(cookedSalmon,118,"熟三文鱼"),
                new UpgradableMerchandise.AUpgradableMerchandise(tropicalFish,160,"热带鱼")
        )));
        merchandises.add(new UpgradableMerchandise(3,36,Arrays.asList(
                new UpgradableMerchandise.AUpgradableMerchandise(melonSlice,52,"西瓜片"),
                new UpgradableMerchandise.AUpgradableMerchandise(driedKelp,58,"干海带"),
                new UpgradableMerchandise.AUpgradableMerchandise(cookie,95,"曲奇")
        )));
        merchandises.add(new UpgradableMerchandise(3,37,Arrays.asList(
                new UpgradableMerchandise.AUpgradableMerchandise(beetroot,38,"甜菜根"),
                new UpgradableMerchandise.AUpgradableMerchandise(beetrootSoup,210,"甜菜汤"),
                new UpgradableMerchandise.AUpgradableMerchandise(cake,350,"蛋糕")
        )));
    }

    public ShopManager(Arena arena) {
        this.arena = arena;
        shopMenu = new ShopMenuJava(this);
        plugin = arena.getPlugin();
        FileConfiguration config = ConfigUtils.getConfig(plugin, "arenas");

//        defaultGolemItemName = new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_GOLEM_ITEM", false).asKey().build();
//        defaultWolfItemName = new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_WOLF_ITEM", false).asKey().build();

        if (config.isSet("instances." + arena.getId() + ".shop")) {
            registerShop();
        }
        openMenuConsumer = player -> {
            if (plugin.getArenaRegistry().getArena(player) == null) {
                return;
            }
            if (!shopMenu.isReady()) {
                new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_NOT_DEFINED").asKey().player(player).sendPlayer();
                return;
            }
            shopMenu.open(player);
        };
    }

    public void resetPlayerData() {
        for (Player player : arena.getPlayers()) {
            playerData.put(player, new HashMap<>());
        }
    }

    public void setOpenMenuConsumer(@NotNull Consumer<Player> openMenuConsumer) {
        this.openMenuConsumer = openMenuConsumer;
    }

    public void openShop(Player player) {
        if (openMenuConsumer != null) {
            openMenuConsumer.accept(player);
        }
    }

    void addMaxStackType(Merchandise merchandise, Player player) {
        if (!merchandise.isEnabled(player)) return;
        if (playerData.get(player).get(merchandise).level == playerData.get(player).get(merchandise).maxLevel) {
            playerData.get(player).get(merchandise).level = 1;
        } else {
            playerData.get(player).get(merchandise).level++;
        }
    }

    private void registerShop() {
        plugin.getServer().getPluginManager().registerEvents(shopMenu, plugin);
        shopMenu.registerShop();
    }

    public void giveItem(Player player, ItemStack itemStack) {
        player.getInventory().addItem(itemStack);
    }

    public void doubleAddPlayerData(Merchandise merchandise, Player player) {
        if (playerData.get(player).get(merchandise).maxLevel + 1 > merchandise.MAX_LEVEL) return;
        playerData.get(player).get(merchandise).maxLevel++;
        playerData.get(player).get(merchandise).level = playerData.get(player).get(merchandise).maxLevel;
    }

    public void changePlayerData(Merchandise merchandise, Player player, byte value) {
        playerData.get(player).get(merchandise).level += value;
        playerData.get(player).get(merchandise).level = loopIndex(playerData.get(player).get(merchandise).level, playerData.get(player).get(merchandise).maxLevel);
    }

    public void setPlayerData(Merchandise merchandise, Player player, byte value) {
        playerData.get(player).get(merchandise).maxLevel = value;
        playerData.get(player).get(merchandise).level = playerData.get(player).get(merchandise).maxLevel;
    }

    public void adjustOrbs(IUser user, int cost) {
        user.adjustStatistic("ORBS", -cost);
        arena.changeArenaOptionBy("TOTAL_ORBS_SPENT", cost);
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

    private static ItemStack createEnchBook(Enchantment ench, int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta != null) {
            meta.addEnchant(ench, level, true);
            book.setItemMeta(meta);
        }
        return book;
    }

    public static Merchandise getMerchandiseWithSlot(int slot) {
        for (Merchandise merchandise : merchandises) {
            if(merchandise.SLOT == slot) {
                return merchandise;
            }
        }
        return null;
    }

}
