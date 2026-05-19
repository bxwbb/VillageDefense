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

package plugily.projects.villagedefense.arena;

import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.jetbrains.annotations.NotNull;
import plugily.projects.minigamesbox.api.arena.IArenaState;
import plugily.projects.minigamesbox.classic.arena.PluginArena;
import plugily.projects.minigamesbox.classic.handlers.language.MessageBuilder;
import plugily.projects.minigamesbox.classic.utils.misc.MiscUtils;
import plugily.projects.minigamesbox.classic.utils.version.xseries.XAttribute;
import plugily.projects.minigamesbox.classic.utils.version.xseries.XEntityType;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.arena.managers.*;
import plugily.projects.villagedefense.arena.managers.Shop.ShopManager;
import plugily.projects.villagedefense.arena.states.EndingState;
import plugily.projects.villagedefense.arena.states.InGameState;
import plugily.projects.villagedefense.arena.states.RestartingState;
import plugily.projects.villagedefense.arena.states.StartingState;
import plugily.projects.villagedefense.creatures.CreatureUtils;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * 村庄守卫的单局 Arena 模型。
 *
 * <p>miniGameBox 的 {@link PluginArena} 已经保存通用字段，如玩家、倒计时、状态、出生点。
 * 本类只补充本玩法需要追踪的运行期实体集合：敌人、村民、狼、铁傀儡和腐肉掉落。</p>
 *
 * @author Tigerpanzer_02
 * <p>
 * Created at 17.12.2021
 */
public class Arena extends PluginArena {

    private static Main plugin;
    private final List<LivingEntity> enemies = new ArrayList<>();
    private final List<Wolf> wolves = new ArrayList<>();
    private final List<Villager> villagers = new ArrayList<>();
    private final List<IronGolem> ironGolems = new ArrayList<>();
    private final List<Pillager> pillagers = new ArrayList<>();
    private final List<Item> droppedFleshes = new ArrayList<>();
    private final List<Entity> spawnedEntities = new ArrayList<>();
    private MapRestorerManager mapRestorerManager;
    private WaveType waveType;
    private Challenge challenge;

    public Challenge getChallenge() {
        return challenge;
    }

    public void setChallenge(Challenge challenge) {
        this.challenge = challenge;
    }

    private final Map<SpawnPoint, List<Location>> spawnPoints = new EnumMap<>(SpawnPoint.class);
    public static final Map<Player, Double> playerPoints  = new HashMap<>();

    private ShopManager shopManager;
    private EnemySpawnManager enemySpawnManager;
    private CreatureTargetManager creatureTargetManager;

    private boolean fighting = false;

    public Arena(String id) {
        super(id);
        setPluginValues();
        // 每个管理器负责一个较窄的领域，避免把商店、刷怪、目标选择都塞进 Arena。
        shopManager = new ShopManager(this);
        enemySpawnManager = new EnemySpawnManager(this);
        creatureTargetManager = new CreatureTargetManager(this);
        mapRestorerManager = new MapRestorerManager(this);
        setMapRestorerManager(mapRestorerManager);
        setScoreboardManager(new ScoreboardManager(this));

        // 状态机入口仍使用 miniGameBox，具体状态行为由本插件覆盖。
        addGameStateHandler(IArenaState.ENDING, new EndingState());
        addGameStateHandler(IArenaState.IN_GAME, new InGameState());
        addGameStateHandler(IArenaState.RESTARTING, new RestartingState());
        addGameStateHandler(IArenaState.STARTING, new StartingState());
    }

    public void reloadShopManager() {
        shopManager = new ShopManager(this);
    }

    public static void init(Main plugin) {
        Arena.plugin = plugin;
    }

    @Override
    public Main getPlugin() {
        return plugin;
    }

    private void setPluginValues() {
        for (SpawnPoint point : SpawnPoint.values()) {
            spawnPoints.put(point, new ArrayList<>());
        }
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public EnemySpawnManager getEnemySpawnManager() {
        return enemySpawnManager;
    }

    public CreatureTargetManager getCreatureTargetManager() {
        return creatureTargetManager;
    }

    public void clearVillagers() {
        for (Entity entity : plugin.getBukkitHelper().getNearbyEntities(getStartLocation(), 50)) {
            if (!(entity instanceof Villager)) {
                continue;
            }
            removeVillager((Villager) entity);
        }
    }

    public void spawnVillagers() {
        List<Location> villagerSpawns = getVillagerSpawns();
        if (villagerSpawns.isEmpty()) {
            getPlugin().getDebugger().debug(Level.WARNING, "No villager spawns set for {0} game won't start", getId());
            return;
        }

        // 村民数量可以大于刷怪点数量，取模循环使用配置好的点位。
        int amount = getPlugin().getConfig().getInt("Limit.Spawn.Villagers", 10);
        int spawnSize = villagerSpawns.size();
        for (int i = 0; i < amount; i++) {
            spawnVillager(villagerSpawns.get(i % spawnSize));
        }

        if (villagers.isEmpty()) {
            getPlugin().getDebugger().debug(Level.WARNING, "Spawning villagers for {0} failed! Are villager spawns set in safe and valid locations?", getId());
        }
    }

    public boolean isFighting() {
        return fighting;
    }

    public void setFighting(boolean fighting) {
        this.fighting = fighting;
    }


    /**
     * Get list of already spawned enemies.
     * This will only return alive enemies not total enemies in current wave.
     *
     * @return list of spawned enemies in arena
     */
    @NotNull
    public List<LivingEntity> getEnemies() {
        return enemies;
    }

    public void removeEnemy(LivingEntity enemy) {
        enemies.remove(enemy);
    }

    @NotNull
    public List<Location> getVillagerSpawns() {
        return spawnPoints.getOrDefault(SpawnPoint.VILLAGER, new ArrayList<>());
    }

    @NotNull
    public List<Location> getBonusPoints() {
        return spawnPoints.getOrDefault(SpawnPoint.BONUS, new ArrayList<>());
    }

    public void addBonusPoint(Location location) {
        plugin.getDebugger().debug("Arena {0} Adding bonus point on location {1}", getId(), location.toString());
        List<Location> bonus = getBonusPoints();
        bonus.add(location);
        spawnPoints.put(SpawnPoint.BONUS, bonus);
        plugin.getDebugger().debug("Arena {0} bonus {1}", getId(), getBonusPoints());
    }


    public void addVillagerSpawn(Location location) {
        plugin.getDebugger().debug("Arena {0} Adding villager spawn on location {1}", getId(), location.toString());
        List<Location> villagerSpawns = getVillagerSpawns();
        villagerSpawns.add(location);
        spawnPoints.put(SpawnPoint.VILLAGER, villagerSpawns);
        plugin.getDebugger().debug("Arena {0} VillagerSpawns {1}", getId(), getVillagerSpawns());
    }

    public void addZombieSpawn(Location location) {
        plugin.getDebugger().debug("Arena {0} Adding zombie spawn on location {1}", getId(), location.toString());
        List<Location> zombies = getZombieSpawns();
        zombies.add(location);
        spawnPoints.put(SpawnPoint.ZOMBIE, zombies);
        plugin.getDebugger().debug("Arena {0} ZombieSpawns {1}", getId(), getZombieSpawns());
    }

    @NotNull
    public List<Item> getDroppedFleshes() {
        return droppedFleshes;
    }

    public void addDroppedFlesh(Item item) {
        droppedFleshes.add(item);
    }

    public void removeDroppedFlesh(Item item) {
        droppedFleshes.remove(item);
    }

    public int getZombiesLeft() {
        // 剩余僵尸 = 待生成数量 + 已生成但仍存活的敌人集合。
        return getArenaOption("ZOMBIES_TO_SPAWN") + enemies.size();
    }

    public int getWave() {
        return getArenaOption("WAVE");
    }

    /**
     * Should be used with endWave.
     *
     * @param wave new game wave
     * @see ArenaManager#endWave(Arena)
     */
    public void setWave(int wave) {
        setArenaOption("WAVE", wave);
    }

    public void spawnVillager(Location location) {
        Villager villager = CreatureUtils.getCreatureInitializer().spawnVillager(location);
        villager.setCustomNameVisible(getPlugin().getConfigPreferences().getOption("NAME_VISIBILITY_VILLAGER"));
        // 原始名字存在 metadata 里，显示名会被血条逻辑动态拼接生命值。
        String name = CreatureUtils.getRandomVillagerName();
        villager.setMetadata(CreatureUtils.getCreatureInitializer().getCreatureCustomNameMetadata(), new FixedMetadataValue(plugin, name));
        villager.setCustomName(CreatureUtils.getHealthNameTag(villager));
        addVillager(villager);
    }

    public void spawnWolf(Location location, Player player) {
        spawnWolf(location, player, false);
    }

    public void spawnWolf(Location location, Player player, boolean force) {
        // force 用于职业/管理逻辑强制生成；普通商店购买需要经过上限检查。
        if (!force && !canSpawnMobForPlayer(player, XEntityType.WOLF.get())) {
            return;
        }
        Wolf wolf = CreatureUtils.getCreatureInitializer().spawnWolf(location);
        wolf.setMetadata("VD_OWNER_UUID", new FixedMetadataValue(getPlugin(), player.getUniqueId().toString()));
        wolf.setOwner(player);
        wolf.setCustomNameVisible(getPlugin().getConfigPreferences().getOption("NAME_VISIBILITY_WOLF"));
        wolf.setCustomName(new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_WAVE_ENTITIES_WOLF_NAME").asKey().integer(0).player(player).build());
        new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_WAVE_ENTITIES_WOLF_SPAWN").asKey().player(player).sendPlayer();
        addWolf(wolf);
    }


    public void spawnGolem(Location location, Player player) {
        spawnGolem(location, player, false);
    }

    public void spawnGolem(Location location, Player player, boolean force) {
        // 友方实体统一写入 VD_OWNER_UUID，玩家离开时 ArenaManager 会按该 metadata 清理。
        if (!force && !canSpawnMobForPlayer(player, XEntityType.IRON_GOLEM.get())) {
            return;
        }
        IronGolem ironGolem = CreatureUtils.getCreatureInitializer().spawnGolem(location);
        ironGolem.setMetadata("VD_OWNER_UUID", new FixedMetadataValue(getPlugin(), player.getUniqueId().toString()));
        ironGolem.setCustomNameVisible(getPlugin().getConfigPreferences().getOption("NAME_VISIBILITY_GOLEM"));
        ironGolem.setCustomName(new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_WAVE_ENTITIES_GOLEM_NAME").asKey().integer(0).player(player).build());
        plugin.getServer().getMobGoals().removeAllGoals(ironGolem, GoalType.TARGET);
        new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_WAVE_ENTITIES_GOLEM_SPAWN").asKey().player(player).sendPlayer();
        addIronGolem(ironGolem);
        MiscUtils.getEntityAttribute(ironGolem, XAttribute.MOVEMENT_SPEED.get()).ifPresent(ai -> ai.setBaseValue(0.25));
    }

    public void spawnPillager(Location location, Player player) {
        spawnPillager(location, player, false);
    }

    public void spawnPillager(Location location, Player player, boolean force) {
        if (!force && !canSpawnMobForPlayer(player, XEntityType.PILLAGER.get())) {
            return;
        }
        Pillager pillager = CreatureUtils.getCreatureInitializer().spawnPillager(location);
        pillager.setMetadata("VD_OWNER_UUID", new FixedMetadataValue(getPlugin(), player.getUniqueId().toString()));
        pillager.setMetadata("IS_PLAYER", new FixedMetadataValue(getPlugin(), true));
        pillager.setCustomNameVisible(true);
        pillager.setCustomName(new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_WAVE_ENTITIES_PILLAGER_NAME").asKey().integer(0).player(player).build());
        pillager.getEquipment().setHelmet(new ItemStack(Material.AIR));
        pillager.getEquipment().setChestplate(new ItemStack(Material.AIR));
        pillager.getEquipment().setLeggings(new ItemStack(Material.AIR));
        pillager.getEquipment().setBoots(new ItemStack(Material.AIR));
        pillager.getEquipment().setItemInMainHand(new ItemStack(Material.AIR));
        pillager.getEquipment().setItemInOffHand(new ItemStack(Material.AIR));
        pillager.getEquipment().setHelmetDropChance(0.0f);
        pillager.getEquipment().setChestplateDropChance(0.0f);
        pillager.getEquipment().setLeggingsDropChance(0.0f);
        pillager.getEquipment().setBootsDropChance(0.0f);
        pillager.getEquipment().setItemInMainHandDropChance(0.0f);
        pillager.getEquipment().setItemInOffHandDropChance(0.0f);
        plugin.getServer().getMobGoals().removeAllGoals(pillager, GoalType.TARGET);
        ItemStack crossbow = new ItemStack(Material.CROSSBOW);
        CrossbowMeta meta = (CrossbowMeta) crossbow.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            crossbow.addUnsafeEnchantment(Enchantment.QUICK_CHARGE, 5);
            crossbow.addUnsafeEnchantment(Enchantment.MULTISHOT, 1);
            crossbow.addUnsafeEnchantment(Enchantment.PIERCING, 5);
            crossbow.setItemMeta(meta);
        }
        pillager.getEquipment().setItemInMainHand(crossbow);
        new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_WAVE_ENTITIES_PILLAGER_SPAWN").asKey().player(player).sendPlayer();
        addPillager(pillager);
        MiscUtils.getEntityAttribute(pillager, XAttribute.MOVEMENT_SPEED.get()).ifPresent(ai -> ai.setBaseValue(0.25));
    }

    protected void addWolf(Wolf wolf) {
        wolves.add(wolf);
        spawnedEntities.add(wolf);
    }

    public boolean canSpawnMobForPlayer(Player player, EntityType type) {
        if (type != XEntityType.IRON_GOLEM.get() && type != XEntityType.WOLF.get() && type != XEntityType.PILLAGER.get()) {
            return false;
        }
        int globalEntityLimit = 0;
        int entityLimit = 0;
        String spawnedName = "";
        switch (type) {
            case WOLF:
                entityLimit = plugin.getPermissionsManager().getPermissionCategoryValue("PLAYER_SPAWN_LIMIT_WOLVES", player);
                spawnedName = new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_WAVE_ENTITIES_WOLF_NAME").asKey().player(player).build();
                globalEntityLimit = plugin.getConfig().getInt("Limit.Spawn.Wolves", 20);
                break;
            case IRON_GOLEM:
                entityLimit = plugin.getPermissionsManager().getPermissionCategoryValue("PLAYER_SPAWN_LIMIT_GOLEMS", player);
                spawnedName = new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_WAVE_ENTITIES_GOLEM_NAME").asKey().player(player).build();
                globalEntityLimit = plugin.getConfig().getInt("Limit.Spawn.Golems", 15);
                break;
            case PILLAGER:
                entityLimit = plugin.getPermissionsManager().getPermissionCategoryValue("PLAYER_SPAWN_LIMIT_GOLEMS", player);
                spawnedName = new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_WAVE_ENTITIES_GOLEM_NAME").asKey().player(player).build();
                globalEntityLimit = plugin.getConfig().getInt("Limit.Spawn.Pillager", 25);
                break;
            default:
                break;
        }
        plugin.getDebugger().debug("SpawnMobCheck for {0} and mob {1}, globalLimit {2}, playerLimit {3}", player.getName(), type, globalEntityLimit, entityLimit);
        String finalSpawnedName = spawnedName;
        List<Entity> entities = new ArrayList<>(spawnedEntities);
        if (plugin.getConfigPreferences().getOption("LIMIT_ENTITY_BUY_AFTER_DEATH")) {
            // 该选项开启后，死亡实体不继续占用购买上限。
            List<Entity> entityList = entities.stream().filter(entity -> entity.getType() == type).collect(Collectors.toList());
            entityList = entityList.stream().filter(entity -> !entity.isDead()).collect(Collectors.toList());

            long spawnedAmount = entityList.size();
            if (spawnedAmount >= globalEntityLimit) {
                sendMobLimitReached(player, globalEntityLimit);
                return false;
            }

            long spawnedPlayerAmount = entityList.stream().filter(entity -> Objects.equals(entity.getCustomName(), finalSpawnedName)).count();
            if (spawnedPlayerAmount >= entityLimit) {
                sendMobLimitReached(player, entityLimit);
                return false;
            }
        }
        boolean finalReturn = false;
        switch (type) {
            case WOLF:
                finalReturn = entityLimit > 0 && wolves.size() < entityLimit;
                break;
            case IRON_GOLEM:
                finalReturn = entityLimit > 0 && ironGolems.size() < entityLimit;
                break;
            case PILLAGER:
                finalReturn = entityLimit > 0 && pillagers.size() < entityLimit;
                break;
            default:
                break;
        }
        if (!finalReturn) {
            sendMobLimitReached(player, entityLimit);
        }
        return finalReturn;
    }

    private void sendMobLimitReached(Player player, int entityLimit) {
        new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_SHOP_MOB_LIMIT_REACHED").asKey().player(player).integer(entityLimit).sendPlayer();
    }

    /**
     * Get alive wolves.
     *
     * @return alive wolves in game
     */
    @NotNull
    public List<Wolf> getWolves() {
        return wolves;
    }

    /**
     * Get alive iron golems.
     *
     * @return alive iron golems in game
     */
    @NotNull
    public List<IronGolem> getIronGolems() {
        return ironGolems;
    }

    public List<Pillager> getPillagers() {
        return pillagers;
    }

    /**
     * Get alive villagers.
     *
     * @return alive villagers in game
     */
    @NotNull
    public List<Villager> getVillagers() {
        return villagers;
    }

    public boolean checkLevelUpRottenFlesh() {
        String rottenFleshLevelOption = "ROTTEN_FLESH_LEVEL";
        int rottenFleshLevel = getArenaOption(rottenFleshLevelOption);
        int rottenFleshAmount = getArenaOption("ROTTEN_FLESH_AMOUNT");

        // 首次升级给固定门槛，之后随等级和玩家数量提高需求。
        if (rottenFleshLevel == 0 && rottenFleshAmount > 50) {
            setArenaOption(rottenFleshLevelOption, 1);
            return true;
        }

        if (rottenFleshLevel * 10 * getPlayers().size() + 10 < rottenFleshAmount) {
            changeArenaOptionBy(rottenFleshLevelOption, 1);
            return true;
        }

        return false;
    }

    protected void addVillager(Villager villager) {
        villagers.add(villager);
    }

    public void removeVillager(Villager villager) {
        villager.remove();
        villager.setHealth(0);
        villagers.remove(villager);
    }

    @Override
    public MapRestorerManager getMapRestorerManager() {
        return mapRestorerManager;
    }

    @NotNull
    public List<Location> getZombieSpawns() {
        return spawnPoints.getOrDefault(SpawnPoint.ZOMBIE, new ArrayList<>());
    }

    public final Location getRandomZombieSpawnLocation(Random random) {
        List<Location> spawns = getZombieSpawns();
        return spawns.get(spawns.size() == 1 ? 0 : random.nextInt(spawns.size()));
    }

    public List<LivingEntity> getAlivePetsList() {
        // “宠物”只指玩家购买或职业生成的友方实体，不包括村民。
        List<LivingEntity> entities = new ArrayList<>();
        entities.addAll(ironGolems);
        entities.addAll(pillagers);
        entities.addAll(wolves);
        return entities;
    }

    public List<LivingEntity> getAliveEntitiesList() {
        List<LivingEntity> entities = new ArrayList<>();
        entities.addAll(ironGolems);
        entities.addAll(pillagers);
        entities.addAll(wolves);
        entities.addAll(villagers);
        return entities;
    }

    protected void addIronGolem(IronGolem ironGolem) {
        ironGolems.add(ironGolem);
        spawnedEntities.add(ironGolem);
    }

    protected void addPillager(Pillager pillager) {
        pillagers.add(pillager);
        spawnedEntities.add(pillager);
    }

    public void removeIronGolem(IronGolem ironGolem) {
        ironGolem.remove();
        ironGolems.remove(ironGolem);
    }

    public void removePillager(Pillager pillager) {
        pillager.remove();
        pillagers.remove(pillager);
    }

    public void removeWolf(Wolf wolf) {
        wolf.remove();
        wolves.remove(wolf);
    }

    public List<Entity> getSpawnedEntities() {
        return spawnedEntities;
    }

    public WaveType getWaveType() {
        return waveType;
    }

    public void setWaveType(WaveType waveType) {
        this.waveType = waveType;
    }

    public enum SpawnPoint {
        ZOMBIE, VILLAGER, BONUS
    }

    public enum WaveType {
        // 默认
        DEFAULT,
        // 沙尘
        SAND,
        // 寒潮
        STORM,
        // 劫掠
        THEFT
    }

    public enum Challenge {
        EASY,
        HARD,
        INFINITE
    }

    public List<Map.Entry<Player, Double>> getSortedPlayers() {
        List<Map.Entry<Player, Double>> list = new ArrayList<>(playerPoints.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return list;
    }

}
