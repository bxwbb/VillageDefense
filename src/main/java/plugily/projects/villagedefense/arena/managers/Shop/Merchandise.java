package plugily.projects.villagedefense.arena.managers.Shop;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import plugily.projects.minigamesbox.classic.utils.misc.complement.ComplementAccessor;

public abstract class Merchandise {

    public final int MAX_LEVEL;
    public final int SLOT;

    public Merchandise(int maxLevel, int slot) {
        this.MAX_LEVEL = maxLevel;
        this.SLOT = slot;
    }

    public abstract ItemStack getLevelItem(int level);

    public abstract int getLevelPrice(int level);

    public String getLevelName(int level) {
        return ComplementAccessor.getComplement().getDisplayName(getLevelItem(level).getItemMeta());
    }

    public abstract boolean isEnabled(Player player);

}
