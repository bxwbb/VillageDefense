package plugily.projects.villagedefense.arena.managers.Shop;

import org.bukkit.entity.Player;

public class ShopMenuBedrock extends ShopMenu {

    public ShopMenuBedrock(ShopManager shopManager) {
        super(shopManager);
    }

    @Override
    public void registerShop() {
    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public void open(Player player) {

    }
}
