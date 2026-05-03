package plugily.projects.villagedefense.arena.managers;

import com.xigua.baseAPI.api.events.NeteasePythonEvent;
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
