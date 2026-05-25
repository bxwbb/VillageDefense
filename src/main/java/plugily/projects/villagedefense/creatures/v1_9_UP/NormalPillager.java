package plugily.projects.villagedefense.creatures.v1_9_UP;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Creature;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pillager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import plugily.projects.minigamesbox.classic.utils.version.VersionUtils;
import plugily.projects.minigamesbox.classic.utils.version.xseries.XAttribute;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.arena.Arena;
import plugily.projects.villagedefense.arena.managers.spawner.SimpleEnemySpawner;

import java.util.Objects;

public class NormalPillager implements SimpleEnemySpawner {
    private final Main plugin;
    public NormalPillager(Main plugin) { this.plugin = plugin; }

    @Override public ItemStack getDropItem() { return new ItemStack(Material.ARROW); }

    @Override
    public double getSpawnRate(Arena arena, int wave, int phase, int spawnAmount) {
        if(wave < 3) return 0;
        return Math.min(0.85, 0.5 + wave * 0.03);
    }

    @Override
    public int getFinalAmount(Arena arena, int wave, int phase, int spawnAmount) {
        return (int) (spawnAmount * (1.0 + wave * 0.08));
    }

    @Override public boolean checkPhase(Arena arena, int wave, int phase, int spawnAmount) { return true; }

    @Override
    public Creature spawn(Location location) {
        Pillager pillager = (Pillager) VersionUtils.spawnEntity(location, EntityType.PILLAGER);
        pillager.getEquipment().setHelmet(new ItemStack(Material.AIR));
        pillager.getEquipment().setHelmetDropChance(0);
        pillager.getEquipment().setChestplate(new ItemStack(Material.AIR));
        pillager.getEquipment().setChestplateDropChance(0);
        pillager.getEquipment().setLeggings(new ItemStack(Material.AIR));
        pillager.getEquipment().setLeggingsDropChance(0);
        pillager.getEquipment().setBoots(new ItemStack(Material.AIR));
        pillager.getEquipment().setBootsDropChance(0);
        pillager.getEquipment().setItemInMainHand(new ItemStack(Material.CROSSBOW));
        pillager.getEquipment().setItemInMainHandDropChance(0);
        Objects.requireNonNull(pillager.getAttribute(XAttribute.FOLLOW_RANGE.get())).setBaseValue(200);
        pillager.setRemoveWhenFarAway(false);
        pillager.setMetadata("PlugilyProjects-VillageDefense-Name", new FixedMetadataValue(plugin, "NormalPillager"));
        return pillager;
    }

    @Override public String getName() { return "NormalPillager"; }
}