package plugily.projects.villagedefense.arena.managers;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import plugily.projects.minigamesbox.inventory.normal.NormalFastInv;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.arena.Arena;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class PotionManager {

    public static final Map<PotionEffectType, Integer> potionPrices = new HashMap<>();

    final Map<Player, Map<PotionEffectType, PotionData>> playerData = new HashMap<>();

    final Main plugin;
    final ShopManager shopManager;
    private final PotionMenu potionMenu;

    public PotionManager(Main plugin, ShopManager shopManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
        potionMenu = new PotionMenuJava(this);
    }

    public static class PotionData {
        public byte level;
        public byte maxLevel;

        public PotionData(byte level, byte maxLevel) {
            this.level = level;
            this.maxLevel = maxLevel;
        }
    }

    public void addNewPotionItem(Arena arena, PotionEffectType potionEffectType, NormalFastInv gui, int slot, int prices, Player player) {
        potionMenu.addNewPotionItem(arena, potionEffectType, gui, slot, prices, player);
    }

    void addMaxStackType(PotionEffectType potionEffectType, Player player) {
        if (Objects.equals(playerData.get(player).get(potionEffectType).maxLevel, playerData.get(player).get(potionEffectType).level)) {
            playerData.get(player).get(potionEffectType).level = 0;
        } else {
            playerData.get(player).get(potionEffectType).level++;
        }
    }

}
