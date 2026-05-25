package plugily.projects.villagedefense.arena.managers.Shop;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public abstract class ShopMenu implements Listener {

    private final ShopManager shopManager;

    public ShopMenu(ShopManager shopManager) {
        this.shopManager = shopManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public abstract void registerShop();

    public abstract boolean isReady();

    public abstract void open(Player player);

    public abstract void refreshOpen(Player player);

    public abstract void openBuffMenu(Player player);

    public abstract void refreshBuffMenu(Player player);
}
