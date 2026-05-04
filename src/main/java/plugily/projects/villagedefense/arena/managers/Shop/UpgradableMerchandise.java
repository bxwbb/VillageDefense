package plugily.projects.villagedefense.arena.managers.Shop;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class UpgradableMerchandise extends Merchandise {

    private Predicate<Player> playerPredicate;

    public static class AUpgradableMerchandise {
        public ItemStack itemStack;
        public int price;
        public String name;

        public AUpgradableMerchandise(ItemStack itemStack, int price, String name) {
            this.itemStack = itemStack;
            this.price = price;
            this.name = name;
        }
    }

    private List<AUpgradableMerchandise> merchandiseList;
    private boolean allShow;

    public UpgradableMerchandise(int maxLevel, int slot) {
        this(maxLevel, slot, new ArrayList<>());
    }

    public UpgradableMerchandise(int maxLevel, int slot, List<AUpgradableMerchandise> merchandiseList) {
        this(maxLevel, slot, merchandiseList, false);
    }

    public UpgradableMerchandise(int maxLevel, int slot, List<AUpgradableMerchandise> merchandiseList, boolean allShow) {
        super(maxLevel, slot);
        this.merchandiseList = merchandiseList;
        this.allShow = allShow;
    }

    @Override
    public ItemStack getLevelItem(int level) {
        return new ItemStack(merchandiseList.get(level - 1).itemStack);
    }

    @Override
    public int getLevelPrice(int level) {
        return merchandiseList.get(level - 1).price;
    }

    @Override
    public String getLevelName(int level) {
        return merchandiseList.get(level - 1).name;
    }

    @Override
    public boolean isEnabled(Player player) {
        return playerPredicate == null || playerPredicate.test(player);
    }

    public List<AUpgradableMerchandise> getMerchandiseList() {
        return merchandiseList;
    }

    public void setMerchandiseList(List<AUpgradableMerchandise> merchandiseList) {
        this.merchandiseList = merchandiseList;
    }

    public Predicate<Player> getPlayerPredicate() {
        return playerPredicate;
    }

    public void setPlayerPredicate(Predicate<Player> playerPredicate) {
        this.playerPredicate = playerPredicate;
    }

    public boolean isAllShow() {
        return allShow;
    }

    public void setAllShow(boolean allShow) {
        this.allShow = allShow;
    }
}
