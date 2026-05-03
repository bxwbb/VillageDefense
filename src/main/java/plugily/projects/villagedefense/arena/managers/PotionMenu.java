package plugily.projects.villagedefense.arena.managers;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import plugily.projects.minigamesbox.inventory.normal.NormalFastInv;
import plugily.projects.villagedefense.arena.Arena;

public abstract class PotionMenu {

    private final PotionManager potionManager;

    public PotionMenu(PotionManager potionManager) {
        this.potionManager = potionManager;
    }

    public PotionManager getPotionManager() {
        return potionManager;
    }

    public abstract void addNewPotionItem(Arena arena, PotionEffectType potionEffectType, NormalFastInv gui, int slot, int prices, Player player);

}
