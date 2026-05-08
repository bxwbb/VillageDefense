package plugily.projects.villagedefense.arena.managers.Shop;

import com.xigua.baseAPI.BaseAPI;
import com.xigua.baseAPI.api.events.NeteasePythonEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectType;
import plugily.projects.minigamesbox.api.user.IUser;

import java.util.*;

public class ShopMenuBedrock extends ShopMenu {

    private static final Map<PotionEffectType, String> EFFECT_CHINESE_MAP = new HashMap<>();

    static {
        EFFECT_CHINESE_MAP.put(PotionEffectType.SPEED, "迅捷");
        EFFECT_CHINESE_MAP.put(PotionEffectType.SLOWNESS, "迟缓");
        EFFECT_CHINESE_MAP.put(PotionEffectType.HASTE, "急迫");
        EFFECT_CHINESE_MAP.put(PotionEffectType.MINING_FATIGUE, "挖掘疲劳");
        EFFECT_CHINESE_MAP.put(PotionEffectType.STRENGTH, "力量");
        EFFECT_CHINESE_MAP.put(PotionEffectType.INSTANT_HEALTH, "瞬间治疗");
        EFFECT_CHINESE_MAP.put(PotionEffectType.INSTANT_DAMAGE, "瞬间伤害");
        EFFECT_CHINESE_MAP.put(PotionEffectType.JUMP_BOOST, "跳跃提升");
        EFFECT_CHINESE_MAP.put(PotionEffectType.NAUSEA, "反胃");
        EFFECT_CHINESE_MAP.put(PotionEffectType.REGENERATION, "再生");
        EFFECT_CHINESE_MAP.put(PotionEffectType.FIRE_RESISTANCE, "火焰抗性");
        EFFECT_CHINESE_MAP.put(PotionEffectType.WATER_BREATHING, "水下呼吸");
        EFFECT_CHINESE_MAP.put(PotionEffectType.INVISIBILITY, "隐身");
        EFFECT_CHINESE_MAP.put(PotionEffectType.BLINDNESS, "失明");
        EFFECT_CHINESE_MAP.put(PotionEffectType.NIGHT_VISION, "夜视");
        EFFECT_CHINESE_MAP.put(PotionEffectType.HUNGER, "饥饿");
        EFFECT_CHINESE_MAP.put(PotionEffectType.WEAKNESS, "虚弱");
        EFFECT_CHINESE_MAP.put(PotionEffectType.POISON, "中毒");
        EFFECT_CHINESE_MAP.put(PotionEffectType.WITHER, "凋零");
        EFFECT_CHINESE_MAP.put(PotionEffectType.HEALTH_BOOST, "生命提升");
        EFFECT_CHINESE_MAP.put(PotionEffectType.ABSORPTION, "伤害吸收");
        EFFECT_CHINESE_MAP.put(PotionEffectType.SATURATION, "饱和");
        EFFECT_CHINESE_MAP.put(PotionEffectType.GLOWING, "发光");
        EFFECT_CHINESE_MAP.put(PotionEffectType.LEVITATION, "漂浮");
        EFFECT_CHINESE_MAP.put(PotionEffectType.LUCK, "幸运");
        EFFECT_CHINESE_MAP.put(PotionEffectType.UNLUCK, "霉运");
        EFFECT_CHINESE_MAP.put(PotionEffectType.SLOW_FALLING, "缓慢降落");
        EFFECT_CHINESE_MAP.put(PotionEffectType.CONDUIT_POWER, "潮涌能量");
        EFFECT_CHINESE_MAP.put(PotionEffectType.DOLPHINS_GRACE, "海豚恩惠");
        EFFECT_CHINESE_MAP.put(PotionEffectType.BAD_OMEN, "不祥之兆");
        EFFECT_CHINESE_MAP.put(PotionEffectType.HERO_OF_THE_VILLAGE, "村庄英雄");
    }

    /**
     * 获取药水效果中文名称
     *
     * @param type 药水效果对象
     * @return 中文名称，找不到返回原英文键名
     */
    public static String getChineseName(PotionEffectType type) {
        if (type == null) return "未知效果";
        return EFFECT_CHINESE_MAP.getOrDefault(type, type.getName());
    }

    public ShopMenuBedrock(ShopManager shopManager) {
        super(shopManager);
    }

    @Override
    public void registerShop() {

    }

    @Override
    public boolean isReady() {
        return true;
    }

    private final int[] Category = {4, 5, 9, 5, 11};
    private final String[] CategoryName = {"armor", "weapon", "potion", "other", "food"};

    @Override
    public void open(Player player) {
        Map<String, Object> ret = new HashMap<>();
        List<Object> catArray = new ArrayList<>();
        int cat = 0;
        int catIndex = 0;
        for (Merchandise merchandise : ShopManager.merchandises) {
            Map<String, Object> jsonObject = new HashMap<>();
            List<Object> data = new ArrayList<>();
            for (int i = 1; i <= merchandise.MAX_LEVEL; i++) {
                Map<String, Object> dataObject = new HashMap<>();
                if (merchandise.getLevelItem(i).getType().equals(Material.POTION)) {
                    dataObject.put("name", getChineseName(((PotionMeta) merchandise.getLevelItem(i).getItemMeta()).getCustomEffects().getFirst().getType()) + merchandise.getLevelName(i));
                } else {
                    dataObject.put("name", merchandise.getLevelName(i));
                }
                dataObject.put("price", merchandise.getLevelPrice(i));
                dataObject.put("slot", i - 1);
                dataObject.put("item", merchandise.getLevelItem(i).getType().getKey().toString());
                data.add(dataObject);
            }
            jsonObject.put("slot", merchandise.SLOT);
            if (getShopManager().playerData.containsKey(player) && getShopManager().playerData.get(player).containsKey(merchandise)) {
                jsonObject.put("maxLevel", getShopManager().playerData.get(player).get(merchandise).maxLevel - 1);
            } else {
                jsonObject.put("maxLevel", 0);
            }
            jsonObject.put("data", data);
            catArray.add(jsonObject);
            cat++;
            if (cat == Category[catIndex]) {
                ret.put(CategoryName[catIndex], catArray);
                catArray = new ArrayList<>();
                catIndex++;
                cat = 0;
            }
        }
        IUser user = getShopManager().plugin.getUserManager().getUser(player);
        ret.put("player_coin", user.getStatistic("ORBS"));
        ((BaseAPI) Objects.requireNonNull(Bukkit.getServer().getPluginManager().getPlugin("BaseAPI"))).notifyToClient(player, "VillageDefense", "main", "openShop", ret);
    }

    @Override
    public void refreshOpen(Player player) {
        Map<String, Object> ret = new HashMap<>();
        List<Object> catArray = new ArrayList<>();
        int cat = 0;
        int catIndex = 0;
        for (Merchandise merchandise : ShopManager.merchandises) {
            Map<String, Object> jsonObject = new HashMap<>();
            List<Object> data = new ArrayList<>();
            for (int i = 1; i <= merchandise.MAX_LEVEL; i++) {
                Map<String, Object> dataObject = new HashMap<>();
                if (merchandise.getLevelItem(i).getType().equals(Material.POTION)) {
                    dataObject.put("name", getChineseName(((PotionMeta) merchandise.getLevelItem(i).getItemMeta()).getCustomEffects().getFirst().getType()) + merchandise.getLevelName(i));
                } else {
                    dataObject.put("name", merchandise.getLevelName(i));
                }
                dataObject.put("price", merchandise.getLevelPrice(i));
                dataObject.put("slot", i - 1);
                dataObject.put("item", merchandise.getLevelItem(i).getType().getKey().toString());
                data.add(dataObject);
            }
            jsonObject.put("slot", merchandise.SLOT);
            if (getShopManager().playerData.containsKey(player) && getShopManager().playerData.get(player).containsKey(merchandise)) {
                jsonObject.put("maxLevel", getShopManager().playerData.get(player).get(merchandise).maxLevel - 1);
            } else {
                jsonObject.put("maxLevel", 0);
            }
            jsonObject.put("data", data);
            catArray.add(jsonObject);
            cat++;
            if (cat == Category[catIndex]) {
                ret.put(CategoryName[catIndex], catArray);
                catArray = new ArrayList<>();
                catIndex++;
                cat = 0;
            }
        }
        IUser user = getShopManager().plugin.getUserManager().getUser(player);
        ret.put("player_coin", user.getStatistic("ORBS"));
        ((BaseAPI) Objects.requireNonNull(Bukkit.getServer().getPluginManager().getPlugin("BaseAPI"))).notifyToClient(player, "VillageDefense", "main", "refreshOpenShop", ret);
    }

    @Override
    public void openBuffMenu(Player player) {
        Map<String, Object> ret = new HashMap<>();
        IUser user = getShopManager().plugin.getUserManager().getUser(player);
        ret.put("player_coin", user.getStatistic("ORBS"));
        List<Object> dataList = new ArrayList<>();
        int slot = 0;
        for (PotionEffectType potionEffectType : ShopManager.potionEffectPrices.keySet()) {
            Map<String, Object> potionData = new HashMap<>();
            potionData.put("name", ShopMenuBedrock.getChineseName(potionEffectType) + "增益");
            potionData.put("rotten_flesh", getShopManager().potionEffectData.get(potionEffectType).level);
            potionData.put("max_rotten_flesh", ShopManager.potionEffectPrices.get(potionEffectType).get(getShopManager().potionEffectData.get(potionEffectType).maxLevel));
            potionData.put("level", getShopManager().potionEffectData.get(potionEffectType).maxLevel);
            potionData.put("slot", slot);
            dataList.add(potionData);
            slot++;
        }
        ret.put("data", dataList);
        ((BaseAPI) Objects.requireNonNull(Bukkit.getServer().getPluginManager().getPlugin("BaseAPI"))).notifyToClient(player, "VillageDefense", "main", "openBuffShop", ret);
    }

    @Override
    public void refreshBuffMenu(Player player) {
        Map<String, Object> ret = new HashMap<>();
        IUser user = getShopManager().plugin.getUserManager().getUser(player);
        ret.put("player_coin", user.getStatistic("ORBS"));
        List<Object> dataList = new ArrayList<>();
        int slot = 0;
        for (PotionEffectType potionEffectType : ShopManager.potionEffectPrices.keySet()) {
            Map<String, Object> potionData = new HashMap<>();
            potionData.put("name", ShopMenuBedrock.getChineseName(potionEffectType) + "增益");
            potionData.put("rotten_flesh", getShopManager().potionEffectData.get(potionEffectType).level);
            potionData.put("max_rotten_flesh", ShopManager.potionEffectPrices.get(potionEffectType).get(getShopManager().potionEffectData.get(potionEffectType).maxLevel));
            potionData.put("level", getShopManager().potionEffectData.get(potionEffectType).maxLevel);
            potionData.put("slot", slot);
            dataList.add(potionData);
            slot++;
        }
        ret.put("data", dataList);
        ((BaseAPI) Objects.requireNonNull(Bukkit.getServer().getPluginManager().getPlugin("BaseAPI"))).notifyToClient(player, "VillageDefense", "main", "refreshBuffShop", ret);
    }

    @EventHandler
    public void onNeteasePythonEvent(NeteasePythonEvent event) {
        if (event.getSystemName().equals("main") && event.getNamespace().equals("VillageDefense")) {
            Map<String, Object> data = event.getData();
            String eventName = event.getPyEventName();
            Player player = event.getPlayer();
            if (eventName.equals("goodsButtonClick")) {
                int category_index = (int) data.get("category_index");
                int product_slot = (int) data.get("product_slot");
                int level_slot = (int) data.get("level_slot");
                int slot = product_slot;
                for (int i = 1; i <= category_index; i++) {
                    slot += Category[i - 1];
                }
                Merchandise merchandise = ShopManager.getMerchandiseWithSlot(slot);
                getShopManager().playerBuy(player, merchandise, level_slot + 1);
            }
        }
    }
}
