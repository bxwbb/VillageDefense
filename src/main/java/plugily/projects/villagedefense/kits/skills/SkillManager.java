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

package plugily.projects.villagedefense.kits.skills;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import plugily.projects.minigamesbox.api.arena.IArenaState;
import plugily.projects.minigamesbox.api.events.game.PlugilyGameStartEvent;
import plugily.projects.minigamesbox.api.events.game.PlugilyGameStateChangeEvent;
import plugily.projects.minigamesbox.api.kit.IKit;
import plugily.projects.minigamesbox.api.user.IUser;
import plugily.projects.minigamesbox.classic.utils.configuration.ConfigUtils;
import plugily.projects.minigamesbox.classic.utils.version.VersionUtils;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.arena.Arena;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkillManager implements Listener {

    public static final String DECOY_METADATA = "VD_SKILL_DECOY";
    private static final String TNT_METADATA = "VD_SKILL_TNT";
    private static final Particle.DustOptions WHITE_BLADE_DUST = new Particle.DustOptions(Color.WHITE, 1.0F);
    private static final String TNT_ARENA_METADATA = "VD_SKILL_TNT_ARENA";
    private static final long GAME_START_CAST_DELAY_MILLIS = 2000L;

    private final Main plugin;
    private final Map<String, SkillConfig> skills = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> activeUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> rageUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> castAllowedAfter = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> skillBars = new ConcurrentHashMap<>();
    private final Map<UUID, List<BukkitTask>> playerTasks = new ConcurrentHashMap<>();
    private final Map<String, List<Villager>> decoys = new ConcurrentHashMap<>();
    private final Map<String, List<Location>> temporaryBlocks = new ConcurrentHashMap<>();
    private final Set<UUID> skillDamageSources = ConcurrentHashMap.newKeySet();
    private BukkitTask barTask;

    public SkillManager(Main plugin) {
        this.plugin = plugin;
        reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startBarTask();
    }

    public void reload() {
        skills.clear();
        FileConfiguration config = ConfigUtils.getConfig(plugin, "skills");
        ConfigurationSection section = config.getConfigurationSection("Skills");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection skillSection = section.getConfigurationSection(key);
            if (skillSection == null) {
                continue;
            }
            SkillConfig skill = SkillConfig.from(key, skillSection);
            if (skill.enabled) {
                skills.put(skill.key, skill);
            }
        }
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        if (barTask != null) {
            barTask.cancel();
            barTask = null;
        }
        for (Arena arena : plugin.getArenaRegistry().getPluginArenas()) {
            clearArena(arena);
        }
        for (BossBar bossBar : skillBars.values()) {
            bossBar.removeAll();
        }
        skillBars.clear();
        cooldowns.clear();
        activeUntil.clear();
        rageUntil.clear();
        castAllowedAfter.clear();
        for (List<BukkitTask> tasks : playerTasks.values()) {
            for (BukkitTask task : tasks) {
                if (task != null) {
                    task.cancel();
                }
            }
        }
        playerTasks.clear();
        skillDamageSources.clear();
    }

    public List<Villager> getDecoys(Arena arena) {
        if (arena == null) {
            return Collections.emptyList();
        }
        List<Villager> list = decoys.get(arena.getId());
        if (list == null) {
            return Collections.emptyList();
        }
        list.removeIf(villager -> villager == null || villager.isDead() || !villager.isValid());
        return new ArrayList<>(list);
    }

    public boolean isDecoy(Arena arena, Villager villager) {
        return villager != null && getDecoys(arena).contains(villager);
    }

    public double getEconomyMultiplier(Player player) {
        if (player == null) {
            return 1.0d;
        }
        Arena arena = plugin.getArenaRegistry().getArena(player);
        IUser user = plugin.getUserManager().getUser(player);
        if (arena == null || user == null || user.isSpectator()) {
            return 1.0d;
        }
        SkillConfig skill = getFirstSkillByType(user.getKit(), SkillType.LOOTER);
        return skill == null ? 1.0d : Math.max(1.0d, skill.multiplier);
    }

    public int applyEconomyMultiplier(Player player, int amount) {
        if (amount <= 0) {
            return amount;
        }
        return (int) Math.ceil(amount * getEconomyMultiplier(player));
    }

    public boolean tryCastPrimarySkill(Player player) {
        if (player == null) {
            return false;
        }
        Arena arena = plugin.getArenaRegistry().getArena(player);
        if (arena == null || arena.getArenaState() != IArenaState.IN_GAME) {
            return false;
        }

        IUser user = plugin.getUserManager().getUser(player);
        if (user == null || user.isSpectator()) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (castAllowedAfter.getOrDefault(player.getUniqueId(), 0L) > now) {
            return false;
        }

        SkillConfig skill = getPrimaryActiveSkill(user.getKit());
        if (skill == null || !checkCooldown(player, skill)) {
            return false;
        }

        boolean casted = castActiveSkill(player, arena, skill);
        if (casted) {
            markActive(player, skill);
        }
        return casted;
    }

    public void showSkillBar(Player player) {
        if (player == null) {
            return;
        }
        Arena arena = plugin.getArenaRegistry().getArena(player);
        IUser user = plugin.getUserManager().getUser(player);
        if (arena == null || arena.getArenaState() != IArenaState.IN_GAME || user == null || user.isSpectator()) {
            hideSkillBar(player);
            return;
        }
        SkillConfig skill = getPrimaryActiveSkill(player);
        if (skill == null) {
            hideSkillBar(player);
            return;
        }
        BossBar bossBar = skillBars.computeIfAbsent(player.getUniqueId(),
                id -> Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SEGMENTED_10));
        bossBar.addPlayer(player);
        updateSkillBar(player, skill);
    }

    public void hideSkillBar(Player player) {
        if (player == null) {
            return;
        }
        BossBar bossBar = skillBars.remove(player.getUniqueId());
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    public void clearPlayerState(Player player) {
        if (player == null) {
            return;
        }
        cooldowns.remove(player.getUniqueId());
        activeUntil.remove(player.getUniqueId());
        rageUntil.remove(player.getUniqueId());
        castAllowedAfter.remove(player.getUniqueId());
        cancelPlayerTasks(player);
        skillDamageSources.remove(player.getUniqueId());
        if (player.isOnline()) {
            VersionUtils.setGlowing(player, false);
        }
        hideSkillBar(player);
    }

    public void clearArena(Arena arena) {
        if (arena == null) {
            return;
        }
        String arenaId = arena.getId();
        for (Villager villager : decoys.getOrDefault(arenaId, Collections.emptyList())) {
            if (villager != null && !villager.isDead()) {
                villager.remove();
            }
        }
        decoys.remove(arenaId);

        for (Location location : temporaryBlocks.getOrDefault(arenaId, Collections.emptyList())) {
            if (location != null && location.getBlock().getType() == Material.STONE_BRICKS) {
                location.getBlock().setType(Material.AIR);
            }
        }
        temporaryBlocks.remove(arenaId);
    }

    private void startBarTask() {
        barTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                Arena arena = plugin.getArenaRegistry().getArena(player);
                IUser user = plugin.getUserManager().getUser(player);
                if (arena == null || arena.getArenaState() != IArenaState.IN_GAME || user == null || user.isSpectator()) {
                    hideSkillBar(player);
                    continue;
                }
                SkillConfig skill = getPrimaryActiveSkill(user.getKit());
                if (skill == null) {
                    hideSkillBar(player);
                    continue;
                }
                showSkillBar(player);
            }
        }, 20L, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameStart(PlugilyGameStartEvent event) {
        if (!(event.getArena() instanceof Arena arena)) {
            return;
        }
        for (Player player : arena.getPlayers()) {
            preparePlayerForGameStart(player);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> arena.getPlayers().forEach(this::showSkillBar), 20L);
    }

    @EventHandler
    public void onGameStateChange(PlugilyGameStateChangeEvent event) {
        if (!(event.getArena() instanceof Arena arena)) {
            return;
        }
        if (event.getArenaState() == IArenaState.IN_GAME) {
            for (Player player : arena.getPlayers()) {
                preparePlayerForGameStart(player);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> arena.getPlayers().forEach(this::showSkillBar), 20L);
            return;
        }
        if (event.getArenaState() == IArenaState.ENDING || event.getArenaState() == IArenaState.RESTARTING) {
            for (Player player : arena.getPlayers()) {
                clearPlayerState(player);
            }
            clearArena(arena);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearPlayerState(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!isSkillTriggerItem(event.getItem())) {
            return;
        }

        if (tryCastPrimarySkill(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private boolean isSkillTriggerItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return false;
        }
        String materialName = itemStack.getType().name();
        return materialName.endsWith("_SWORD")
                || materialName.endsWith("_PICKAXE")
                || materialName.endsWith("_AXE")
                || materialName.endsWith("_SHOVEL")
                || materialName.equals("BOW")
                || materialName.equals("CROSSBOW")
                || materialName.equals("TRIDENT")
                || materialName.equals("MACE");
    }

    private void preparePlayerForGameStart(Player player) {
        clearPlayerState(player);
        castAllowedAfter.put(player.getUniqueId(), System.currentTimeMillis() + GAME_START_CAST_DELAY_MILLIS);
    }

    private void trackTask(Player player, BukkitTask task) {
        if (player == null || task == null) {
            return;
        }
        playerTasks.computeIfAbsent(player.getUniqueId(), id -> Collections.synchronizedList(new ArrayList<>())).add(task);
    }

    private void cancelPlayerTasks(Player player) {
        List<BukkitTask> tasks = playerTasks.remove(player.getUniqueId());
        if (tasks == null) {
            return;
        }
        for (BukkitTask task : tasks) {
            if (task != null) {
                task.cancel();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        Player player = getSourcePlayer(event.getDamager());
        if (player == null || skillDamageSources.contains(player.getUniqueId())) {
            return;
        }

        Arena arena = plugin.getArenaRegistry().getArena(player);
        if (arena == null || !arena.getEnemies().contains(target)) {
            return;
        }
        IUser user = plugin.getUserManager().getUser(player);
        if (user == null || user.isSpectator()) {
            return;
        }

        applyRageDamage(player, user, event);
        applyLightning(player, user, target, event);
        applyExecute(player, arena, user, target, event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSkillTntExplode(EntityExplodeEvent event) {
        if (event.getEntity().hasMetadata(TNT_METADATA)) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSkillTntDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Arena arena = plugin.getArenaRegistry().getArena(player);
        if (!isSkillTntFromArena(event.getDamager(), arena)) {
            return;
        }
        event.setCancelled(true);
        event.setDamage(0.0d);
    }

    private boolean castActiveSkill(Player player, Arena arena, SkillConfig skill) {
        switch (skill.type) {
            case RAGE:
                castRage(player, skill);
                return true;
            case BLADE:
                castBlade(player, arena, skill);
                return true;
            case EXPLOSION:
                castExplosion(player, arena, skill);
                return true;
            case STONE_WALL:
                return castStoneWall(player, arena, skill);
            case DECOY:
                castDecoy(player, arena, skill);
                return true;
            case HEAL:
                castHeal(player, arena, skill);
                return true;
            case RESISTANCE:
                return castResistance(player, skill);
            case KNOCKBACK:
                castKnockback(player, arena, skill);
                return true;
            case BLACK_HOLE:
                castBlackHole(player, arena, skill);
                return true;
            case GOLEM:
                castGolem(player, arena);
                return true;
            case PARTICLE_ARROW:
                castParticleArrow(player, arena, skill);
                return true;
            case LIGHTNING:
            case LOOTER:
            case EXECUTE:
            default:
                return false;
        }
    }

    private void castRage(Player player, SkillConfig skill) {
        long expiresAt = System.currentTimeMillis() + skill.durationSeconds * 1000L;
        rageUntil.put(player.getUniqueId(), expiresAt);
        VersionUtils.setGlowing(player, true);
        player.sendMessage(color("&c狂暴已开启，血量越低伤害越高。"));
        trackTask(player, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Long current = rageUntil.get(player.getUniqueId());
            if (current != null && current <= expiresAt) {
                rageUntil.remove(player.getUniqueId());
                if (player.isOnline()) {
                    VersionUtils.setGlowing(player, false);
                    player.sendMessage(color("&7狂暴效果已结束。"));
                }
            }
        }, 20L * Math.max(1, skill.durationSeconds)));
    }

    private void castBlade(Player player, Arena arena, SkillConfig skill) {
        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() == 0) {
            direction = new Vector(0, 0, 1);
        }
        direction.normalize();
        Location start = player.getLocation().add(0, 1.2d, 0).add(direction.clone().multiply(0.7d));
        Vector right = new Vector(-direction.getZ(), 0, direction.getX());
        if (right.lengthSquared() == 0) {
            right = new Vector(1, 0, 0);
        }
        right.normalize();
        final Vector bladeRight = right;
        final Vector bladeForward = direction.clone();
        final Vector bladeDirection = direction.clone();

        Set<UUID> damaged = new HashSet<>();
        double step = Math.max(0.1d, skill.speedPerSecond / 20.0d);
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private double traveled = 0.0d;

            @Override
            public void run() {
                traveled += step;
                Location center = start.clone().add(bladeDirection.clone().multiply(traveled));
                List<Location> bladePoints = drawBlade(center, bladeForward, bladeRight, arena, skill);
                for (LivingEntity enemy : new ArrayList<>(arena.getEnemies())) {
                    if (enemy == null || enemy.isDead() || damaged.contains(enemy.getUniqueId())) {
                        continue;
                    }
                    if (isNearAnyPoint(enemy, bladePoints, 1.2d)) {
                        damaged.add(enemy.getUniqueId());
                        damageBySkill(player, enemy, getPercentDamage(enemy, skill.damagePercent));
                    }
                }
                if (traveled >= skill.maxDistance) {
                    task[0].cancel();
                }
            }
        }, 0L, 1L);
        trackTask(player, task[0]);
        player.sendMessage(color("&f剑刃已释放。"));
    }

    private List<Location> drawBlade(Location center, Vector forward, Vector right, Arena arena, SkillConfig skill) {
        List<Location> points = new ArrayList<>();
        double halfLength = Math.max(0.5d, skill.width / 2.0d);
        for (int i = -16; i <= 16; i++) {
            double side = halfLength * i / 16.0d;
            double curve = Math.sqrt(Math.max(0.0d, halfLength * halfLength - side * side)) * 0.45d;
            Location particle = center.clone()
                    .add(right.clone().multiply(side))
                    .add(forward.clone().multiply(curve));
            points.add(particle);
            sendWhiteBladeDust(arena.getPlayers(), particle, 1);
        }
        sendWhiteBladeDust(arena.getPlayers(), center, 3);
        return points;
    }

    private void sendWhiteBladeDust(Collection<Player> viewers, Location location, int count) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        for (Player viewer : viewers) {
            if (viewer == null || !viewer.isOnline() || !viewer.getWorld().equals(location.getWorld())) {
                continue;
            }
            viewer.spawnParticle(Particle.DUST, location, count, 0.02d, 0.02d, 0.02d, 0.0d, WHITE_BLADE_DUST);
        }
    }

    private boolean isNearAnyPoint(LivingEntity enemy, List<Location> points, double radius) {
        if (enemy == null || points.isEmpty()) {
            return false;
        }
        double radiusSquared = radius * radius;
        Location enemyCenter = enemy.getLocation().add(0, Math.min(1.0d, enemy.getHeight() * 0.5d), 0);
        for (Location point : points) {
            if (point.getWorld().equals(enemy.getWorld()) && point.distanceSquared(enemyCenter) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    private void castExplosion(Player player, Arena arena, SkillConfig skill) {
        Location base = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(skill.frontDistance));
        World world = player.getWorld();
        Vector right = new Vector(-player.getLocation().getDirection().getZ(), 0, player.getLocation().getDirection().getX());
        if (right.lengthSquared() == 0) {
            right = new Vector(1, 0, 0);
        }
        right.normalize();
        int count = Math.max(1, skill.count);
        for (int i = 0; i < count; i++) {
            double offset = count == 1 ? 0.0d : (i - (count - 1) / 2.0d) * 0.7d;
            Location spawn = base.clone().add(right.clone().multiply(offset));
            TNTPrimed tnt = world.spawn(spawn, TNTPrimed.class);
            tnt.setFuseTicks(Math.max(1, skill.fuseSeconds * 20));
            tnt.setYield((float) skill.power);
            tnt.setMetadata(TNT_METADATA, new FixedMetadataValue(plugin, player.getUniqueId().toString()));
            tnt.setMetadata(TNT_ARENA_METADATA, new FixedMetadataValue(plugin, arena.getId()));
        }
        player.sendMessage(color("&c爆破已部署。"));
    }

    private boolean castStoneWall(Player player, Arena arena, SkillConfig skill) {
        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() == 0) {
            direction = new Vector(0, 0, 1);
        }
        direction.normalize();
        Location base = player.getLocation().add(direction.clone().multiply(skill.frontDistance));
        Vector right = new Vector(-direction.getZ(), 0, direction.getX()).normalize();
        List<Location> placed = new ArrayList<>();

        int halfWidth = Math.max(1, skill.width) / 2;
        int height = Math.max(1, skill.height);
        for (int x = -halfWidth; x <= halfWidth; x++) {
            for (int y = 0; y < height; y++) {
                Location location = base.clone().add(right.clone().multiply(x)).add(0, y, 0);
                Block block = location.getBlock();
                if (!block.getType().isAir()) {
                    continue;
                }
                block.setType(Material.STONE_BRICKS);
                placed.add(block.getLocation());
            }
        }
        if (placed.isEmpty()) {
            player.sendMessage(color("&c前方没有足够空气生成石砖墙。"));
            return false;
        }
        temporaryBlocks.computeIfAbsent(arena.getId(), key -> new ArrayList<>()).addAll(placed);
        Bukkit.getScheduler().runTaskLater(plugin, () -> removeTemporaryBlocks(arena, placed), 20L * Math.max(1, skill.durationSeconds));
        player.sendMessage(color("&7石砖墙已生成。"));
        return true;
    }

    private void removeTemporaryBlocks(Arena arena, List<Location> placed) {
        List<Location> active = temporaryBlocks.get(arena.getId());
        if (active != null) {
            active.removeAll(placed);
        }
        for (Location location : placed) {
            if (location != null && location.getBlock().getType() == Material.STONE_BRICKS) {
                location.getBlock().setType(Material.AIR);
            }
        }
    }

    private void castDecoy(Player player, Arena arena, SkillConfig skill) {
        Villager villager = player.getWorld().spawn(player.getLocation(), Villager.class);
        villager.setAI(false);
        villager.setAdult();
        villager.setCustomNameVisible(true);
        villager.setCustomName(color(skill.decoyName));
        VersionUtils.setMaxHealth(villager, Math.max(1.0d, skill.health));
        villager.setHealth(Math.max(1.0d, skill.health));
        villager.setMetadata(DECOY_METADATA, new FixedMetadataValue(plugin, arena.getId()));
        decoys.computeIfAbsent(arena.getId(), key -> new ArrayList<>()).add(villager);

        for (LivingEntity enemy : arena.getEnemies()) {
            if (enemy instanceof org.bukkit.entity.Creature creature) {
                creature.setTarget(villager);
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            List<Villager> list = decoys.get(arena.getId());
            if (list != null) {
                list.remove(villager);
            }
            if (!villager.isDead()) {
                villager.remove();
            }
        }, 20L * Math.max(1, skill.durationSeconds));
        player.sendMessage(color("&a诱饵已生成。"));
    }

    private void castHeal(Player player, Arena arena, SkillConfig skill) {
        int duration = Math.max(1, skill.durationSeconds);
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int seconds = 0;

            @Override
            public void run() {
                seconds++;
                for (Player arenaPlayer : arena.getPlayersLeft()) {
                    heal(arenaPlayer, skill.healPerSecond);
                    VersionUtils.sendParticles("HEART", arena.getPlayers(), arenaPlayer.getLocation().add(0, 1, 0), 6, 0.2, 0.4, 0.2);
                }
                if (seconds >= duration) {
                    task[0].cancel();
                }
            }
        }, 0L, 20L);
        trackTask(player, task[0]);
        player.sendMessage(color("&a全队治疗已开启。"));
    }

    private boolean castResistance(Player player, SkillConfig skill) {
        PotionEffectType resistance = getResistanceType();
        if (resistance == null) {
            player.sendMessage(color("&c当前服务端找不到抗性药水效果。"));
            return false;
        }
        player.addPotionEffect(new PotionEffect(resistance, Math.max(1, skill.durationSeconds) * 20, Math.max(0, skill.amplifier - 1), true, true, true));
        player.sendMessage(color("&b抗性提升已生效。"));
        return true;
    }

    private void castKnockback(Player player, Arena arena, SkillConfig skill) {
        int affected = 0;
        for (LivingEntity enemy : new ArrayList<>(arena.getEnemies())) {
            if (enemy == null || enemy.isDead() || !enemy.getWorld().equals(player.getWorld())) {
                continue;
            }
            if (enemy.getLocation().distanceSquared(player.getLocation()) > skill.radius * skill.radius) {
                continue;
            }
            Vector direction = enemy.getLocation().toVector().subtract(player.getLocation().toVector());
            if (direction.lengthSquared() == 0) {
                direction = player.getLocation().getDirection();
            }
            enemy.setVelocity(direction.normalize().multiply(skill.knockbackStrength).setY(skill.knockbackY));
            damageBySkill(player, enemy, getPercentDamage(enemy, skill.damagePercent));
            affected++;
        }
        VersionUtils.sendParticles("EXPLOSION_NORMAL", arena.getPlayers(), player.getLocation(), 20, 1, 0.3, 1);
    }

    private void castBlackHole(Player player, Arena arena, SkillConfig skill) {
        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() == 0) {
            direction = new Vector(0, 0, 1);
        }
        direction.normalize();
        Vector moveDirection = direction.clone();
        Location start = player.getLocation().add(direction.clone().multiply(skill.frontDistance)).add(0, 1.0d, 0);
        int durationTicks = Math.max(1, skill.durationSeconds) * 20;
        int intervalTicks = Math.max(1, skill.intervalTicks);
        int[] ticks = {0};

        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ticks[0] += intervalTicks;
            double traveled = 2.0d * ticks[0] / 20.0d;
            Location center = start.clone().add(moveDirection.clone().multiply(traveled));
            VersionUtils.sendParticles(skill.particle, arena.getPlayers(), center, 30, skill.radius * 0.25d, 0.5d, skill.radius * 0.25d);
            VersionUtils.sendParticles("SMOKE_LARGE", arena.getPlayers(), center, 8, 0.2d, 0.2d, 0.2d);
            VersionUtils.sendParticles("CLOUD", arena.getPlayers(), center, 18, skill.radius * 0.18d, 0.35d, skill.radius * 0.18d);
            VersionUtils.sendParticles("FIREWORKS_SPARK", arena.getPlayers(), center, 12, skill.radius * 0.12d, 0.2d, skill.radius * 0.12d);

            for (LivingEntity enemy : new ArrayList<>(arena.getEnemies())) {
                if (enemy == null || enemy.isDead() || !enemy.getWorld().equals(center.getWorld())) {
                    continue;
                }
                if (enemy.getLocation().distanceSquared(center) > skill.radius * skill.radius) {
                    continue;
                }
                Vector pull = center.toVector().subtract(enemy.getLocation().toVector());
                if (pull.lengthSquared() > 0) {
                    enemy.setVelocity(pull.normalize().multiply(skill.pullStrength).setY(0.2d));
                }
                if (ticks[0] % 20 == 0) {
                    damageBySkill(player, enemy, getPercentDamage(enemy, skill.damagePercent));
                }
            }

            if (ticks[0] >= durationTicks) {
                task[0].cancel();
            }
        }, 0L, intervalTicks);
        trackTask(player, task[0]);
        player.sendMessage(color("&5黑洞已召唤。"));
    }

    private void castGolem(Player player, Arena arena) {
        arena.spawnGolem(player.getLocation(), player, true);
        player.sendMessage(color("&f铁傀儡已加入战斗。"));
    }

    private void castParticleArrow(Player player, Arena arena, SkillConfig skill) {
        int count = Math.max(1, skill.count);
        int intervalTicks = Math.max(1, skill.intervalTicks);
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int launched = 0;

            @Override
            public void run() {
                launched++;
                launchParticleArrow(player, arena, skill);
                if (launched >= count) {
                    task[0].cancel();
                }
            }
        }, 0L, intervalTicks);
        trackTask(player, task[0]);
        player.sendMessage(color("&b粒子箭已释放。"));
    }

    private void launchParticleArrow(Player player, Arena arena, SkillConfig skill) {
        Location start = player.getEyeLocation().add(player.getLocation().getDirection().normalize().multiply(0.7d));
        Vector direction = player.getLocation().getDirection().normalize();
        double step = Math.max(0.1d, skill.speedPerSecond / 20.0d);
        Set<UUID> damaged = new HashSet<>();
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private double traveled = 0.0d;

            @Override
            public void run() {
                traveled += step;
                Location location = start.clone().add(direction.clone().multiply(traveled));
                VersionUtils.sendParticles(skill.particle, arena.getPlayers(), location, 2, 0.02d, 0.02d, 0.02d);
                for (LivingEntity enemy : new ArrayList<>(arena.getEnemies())) {
                    if (enemy == null || enemy.isDead() || damaged.contains(enemy.getUniqueId())) {
                        continue;
                    }
                    if (enemy.getWorld().equals(location.getWorld()) && enemy.getLocation().add(0, 1.0d, 0).distanceSquared(location) <= 1.0d) {
                        damaged.add(enemy.getUniqueId());
                        damageBySkill(player, enemy, getPercentDamage(enemy, skill.damagePercent));
                        task[0].cancel();
                        return;
                    }
                }
                if (traveled >= skill.maxDistance) {
                    task[0].cancel();
                }
            }
        }, 0L, 1L);
        trackTask(player, task[0]);
    }

    private void applyRageDamage(Player player, IUser user, EntityDamageByEntityEvent event) {
        Long expiresAt = rageUntil.get(player.getUniqueId());
        if (expiresAt == null) {
            return;
        }
        if (expiresAt <= System.currentTimeMillis()) {
            rageUntil.remove(player.getUniqueId());
            VersionUtils.setGlowing(player, false);
            return;
        }
        SkillConfig skill = getFirstSkillByType(user.getKit(), SkillType.RAGE);
        if (skill == null) {
            return;
        }
        double maxHealth = Math.max(1.0d, VersionUtils.getMaxHealth(player));
        double missingPercent = Math.max(0.0d, Math.min(1.0d, 1.0d - player.getHealth() / maxHealth));
        double multiplier = 1.0d + missingPercent * (Math.max(1.0d, skill.maxDamageMultiplier) - 1.0d);
        event.setDamage(event.getDamage() * multiplier);
    }

    private void applyLightning(Player player, IUser user, LivingEntity target, EntityDamageByEntityEvent event) {
        SkillConfig skill = getFirstSkillByType(user.getKit(), SkillType.LIGHTNING);
        if (skill == null || Math.random() > skill.chancePercent) {
            return;
        }
        target.getWorld().strikeLightningEffect(target.getLocation());
        event.setDamage(event.getDamage() + getPercentDamage(target, skill.damagePercent));
    }

    private void applyExecute(Player player, Arena arena, IUser user, LivingEntity target, EntityDamageByEntityEvent event) {
        SkillConfig skill = getFirstSkillByType(user.getKit(), SkillType.EXECUTE);
        if (skill == null || target == null || target.isDead()) {
            return;
        }
        double maxHealth = Math.max(1.0d, VersionUtils.getMaxHealth(target));
        double thresholdHealth = maxHealth * skill.healthThresholdPercent;
        double healthAfterHit = Math.max(0.0d, target.getHealth() - event.getFinalDamage());
        if (target.getHealth() > thresholdHealth && healthAfterHit > thresholdHealth) {
            return;
        }
        event.setDamage(Math.max(event.getDamage(), maxHealth + 100.0d));
        VersionUtils.sendParticles("CRIT_MAGIC", arena.getPlayers(), target.getLocation().add(0, 1.0d, 0), 20, 0.3d, 0.4d, 0.3d);
    }

    private SkillConfig getFirstSkillByType(IKit kit, SkillType type) {
        for (SkillConfig skill : getSkillsForKit(kit)) {
            if (skill.type == type) {
                return skill;
            }
        }
        return null;
    }

    private SkillConfig getPrimaryActiveSkill(Player player) {
        if (player == null) {
            return null;
        }
        IUser user = plugin.getUserManager().getUser(player);
        return user == null ? null : getPrimaryActiveSkill(user.getKit());
    }

    private SkillConfig getPrimaryActiveSkill(IKit kit) {
        for (SkillConfig skill : getSkillsForKit(kit)) {
            if (skill.active) {
                return skill;
            }
        }
        return null;
    }

    private List<SkillConfig> getSkillsForKit(IKit kit) {
        if (kit == null) {
            return Collections.emptyList();
        }
        List<SkillConfig> result = new ArrayList<>();
        for (String key : getSkillKeys(kit)) {
            SkillConfig skill = skills.get(normalize(key));
            if (skill != null) {
                result.add(skill);
            }
        }
        return result;
    }

    private List<String> getSkillKeys(IKit kit) {
        if (kit == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        addSkillKeys(result, kit.getOptionalConfiguration("skills"));
        addSkillKeys(result, kit.getOptionalConfiguration("skill"));
        return result;
    }

    @SuppressWarnings("unchecked")
    private void addSkillKeys(List<String> result, Object value) {
        if (value instanceof Collection<?>) {
            for (Object object : (Collection<Object>) value) {
                if (object != null) {
                    result.add(String.valueOf(object));
                }
            }
            return;
        }
        if (value != null) {
            result.add(String.valueOf(value));
        }
    }

    private boolean checkCooldown(Player player, SkillConfig skill) {
        long now = System.currentTimeMillis();
        long activeEndsAt = activeUntil
                .getOrDefault(player.getUniqueId(), Collections.emptyMap())
                .getOrDefault(skill.key, 0L);
        if (activeEndsAt > now) {
            long seconds = (long) Math.ceil((activeEndsAt - now) / 1000.0d);
            player.sendMessage(color("&b技能持续中，还剩 &f" + seconds + " &b秒。"));
            return false;
        }
        if (activeEndsAt > 0L) {
            finishActive(player, skill, activeEndsAt);
        }
        long until = cooldowns
                .getOrDefault(player.getUniqueId(), Collections.emptyMap())
                .getOrDefault(skill.key, 0L);
        if (until <= now) {
            return true;
        }
        long seconds = (long) Math.ceil((until - now) / 1000.0d);
        player.sendMessage(color("&c技能冷却中，还剩 &f" + seconds + " &c秒。"));
        return false;
    }

    private void markActive(Player player, SkillConfig skill) {
        if (skill.durationSeconds > 0 && skill.hasDurationBar()) {
            long expiresAt = System.currentTimeMillis() + skill.durationSeconds * 1000L;
            activeUntil.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>())
                    .put(skill.key, expiresAt);
            Bukkit.getScheduler().runTaskLater(plugin, () -> finishActive(player, skill, expiresAt), 20L * skill.durationSeconds);
        } else {
            setCooldown(player, skill);
        }
        updateSkillBar(player, skill);
    }

    private void finishActive(Player player, SkillConfig skill) {
        finishActive(player, skill, 0L);
    }

    private void finishActive(Player player, SkillConfig skill, long expectedExpiresAt) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Map<String, Long> playerActive = activeUntil.get(player.getUniqueId());
        boolean removed = false;
        if (playerActive != null) {
            Long currentExpiresAt = playerActive.get(skill.key);
            if (currentExpiresAt != null && (expectedExpiresAt <= 0L || currentExpiresAt == expectedExpiresAt)) {
                playerActive.remove(skill.key);
                removed = true;
            }
        }
        if (skill.hasDurationBar() && !removed) {
            return;
        }
        setCooldown(player, skill);
        updateSkillBar(player, skill);
    }

    private void setCooldown(Player player, SkillConfig skill) {
        cooldowns.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>())
                .put(skill.key, System.currentTimeMillis() + skill.cooldownSeconds * 1000L);
        updateSkillBar(player, skill);
    }

    private void updateSkillBar(Player player, SkillConfig skill) {
        if (player == null || skill == null) {
            return;
        }
        BossBar bossBar = skillBars.get(player.getUniqueId());
        if (bossBar == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long activeEndsAt = activeUntil
                .getOrDefault(player.getUniqueId(), Collections.emptyMap())
                .getOrDefault(skill.key, 0L);
        if (activeEndsAt > now) {
            double progress = (activeEndsAt - now) / Math.max(1.0d, skill.durationSeconds * 1000.0d);
            bossBar.setColor(BarColor.BLUE);
            bossBar.setProgress(clampProgress(progress));
            bossBar.setTitle(formatSkillBarTitle(skill, "&b&l持续中"));
            return;
        }
        if (activeEndsAt > 0L) {
            finishActive(player, skill, activeEndsAt);
            return;
        }

        long cooldownEndsAt = cooldowns
                .getOrDefault(player.getUniqueId(), Collections.emptyMap())
                .getOrDefault(skill.key, 0L);
        if (cooldownEndsAt > now) {
            double elapsed = skill.cooldownSeconds * 1000.0d - (cooldownEndsAt - now);
            double progress = elapsed / Math.max(1.0d, skill.cooldownSeconds * 1000.0d);
            bossBar.setColor(BarColor.RED);
            bossBar.setProgress(clampProgress(progress));
            bossBar.setTitle(formatSkillBarTitle(skill, "&c&l冷却中"));
            return;
        }

        bossBar.setColor(BarColor.GREEN);
        bossBar.setProgress(1.0d);
        bossBar.setTitle(formatSkillBarTitle(skill, "&a&l就绪"));
    }

    private String formatSkillBarTitle(SkillConfig skill, String state) {
        return color(skill.displayName + " " + state + " &7右键触发");
    }

    private double clampProgress(double progress) {
        return Math.max(0.0d, Math.min(1.0d, progress));
    }

    private void damageBySkill(Player player, LivingEntity target, double damage) {
        if (damage <= 0.0d || target == null || target.isDead()) {
            return;
        }
        skillDamageSources.add(player.getUniqueId());
        try {
            target.damage(damage, player);
        } finally {
            skillDamageSources.remove(player.getUniqueId());
        }
    }

    private double getPercentDamage(LivingEntity target, double percent) {
        return Math.max(0.0d, VersionUtils.getMaxHealth(target) * percent);
    }

    private void heal(Player player, double amount) {
        double maxHealth = VersionUtils.getMaxHealth(player);
        player.setHealth(Math.min(maxHealth, player.getHealth() + Math.max(0.0d, amount)));
    }

    private PotionEffectType getResistanceType() {
        PotionEffectType type = PotionEffectType.getByName("DAMAGE_RESISTANCE");
        return type == null ? PotionEffectType.getByName("RESISTANCE") : type;
    }

    private boolean isSkillTntFromArena(Entity entity, Arena arena) {
        if (entity == null || arena == null || !entity.hasMetadata(TNT_METADATA)) {
            return false;
        }
        for (MetadataValue metadata : entity.getMetadata(TNT_ARENA_METADATA)) {
            if (plugin.equals(metadata.getOwningPlugin()) && arena.getId().equals(metadata.asString())) {
                return true;
            }
        }
        for (MetadataValue metadata : entity.getMetadata(TNT_METADATA)) {
            if (!plugin.equals(metadata.getOwningPlugin())) {
                continue;
            }
            try {
                Player owner = Bukkit.getPlayer(UUID.fromString(metadata.asString()));
                if (owner != null && arena.equals(plugin.getArenaRegistry().getArena(owner))) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
        return false;
    }

    private Player getSourcePlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private enum SkillType {
        RAGE,
        BLADE,
        EXPLOSION,
        STONE_WALL,
        DECOY,
        HEAL,
        RESISTANCE,
        LIGHTNING,
        KNOCKBACK,
        BLACK_HOLE,
        EXECUTE,
        LOOTER,
        GOLEM,
        PARTICLE_ARROW
    }

    private static final class SkillConfig {
        private final String key;
        private final boolean enabled;
        private final SkillType type;
        private final boolean active;
        private final String displayName;
        private final int cooldownSeconds;
        private final int durationSeconds;
        private final double multiplier;
        private final double maxDamageMultiplier;
        private final double damagePercent;
        private final double chancePercent;
        private final double healthThresholdPercent;
        private final double maxDistance;
        private final double speedPerSecond;
        private final int count;
        private final int intervalTicks;
        private final int fuseSeconds;
        private final double frontDistance;
        private final double power;
        private final int width;
        private final int height;
        private final double health;
        private final String decoyName;
        private final double healPerSecond;
        private final int amplifier;
        private final double radius;
        private final double knockbackStrength;
        private final double knockbackY;
        private final double pullStrength;
        private final String particle;

        private SkillConfig(String key, boolean enabled, SkillType type, boolean active, String displayName, int cooldownSeconds, int durationSeconds,
                            double multiplier, double maxDamageMultiplier, double damagePercent, double chancePercent, double healthThresholdPercent,
                            double maxDistance, double speedPerSecond, int count, int intervalTicks, int fuseSeconds, double frontDistance, double power,
                            int width, int height, double health, String decoyName, double healPerSecond, int amplifier,
                            double radius, double knockbackStrength, double knockbackY, double pullStrength, String particle) {
            this.key = key;
            this.enabled = enabled;
            this.type = type;
            this.active = active;
            this.displayName = displayName;
            this.cooldownSeconds = cooldownSeconds;
            this.durationSeconds = durationSeconds;
            this.multiplier = multiplier;
            this.maxDamageMultiplier = maxDamageMultiplier;
            this.damagePercent = damagePercent;
            this.chancePercent = chancePercent;
            this.healthThresholdPercent = healthThresholdPercent;
            this.maxDistance = maxDistance;
            this.speedPerSecond = speedPerSecond;
            this.count = count;
            this.intervalTicks = intervalTicks;
            this.fuseSeconds = fuseSeconds;
            this.frontDistance = frontDistance;
            this.power = power;
            this.width = width;
            this.height = height;
            this.health = health;
            this.decoyName = decoyName;
            this.healPerSecond = healPerSecond;
            this.amplifier = amplifier;
            this.radius = radius;
            this.knockbackStrength = knockbackStrength;
            this.knockbackY = knockbackY;
            this.pullStrength = pullStrength;
            this.particle = particle;
        }

        private static SkillConfig from(String key, ConfigurationSection section) {
            SkillType type = parseType(section.getString("Type", key));
            return new SkillConfig(
                    normalizeStatic(key),
                    section.getBoolean("Enabled", true),
                    type,
                    type != SkillType.LIGHTNING && type != SkillType.LOOTER && type != SkillType.EXECUTE,
                    section.getString("Display-Name", section.getString("Item.Name", section.getString("Name", key))),
                    section.getInt("Cooldown", 120),
                    section.getInt("Duration", defaultDuration(type)),
                    section.getDouble("Multiplier", 1.5d),
                    section.getDouble("Max-Damage-Multiplier", 3.0d),
                    percent(section.getDouble("Damage-Percent", 0.0d)),
                    percent(section.getDouble("Chance-Percent", 0.0d)),
                    percent(section.getDouble("Health-Threshold-Percent", 15.0d)),
                    section.getDouble("Max-Distance", 20.0d),
                    section.getDouble("Speed-Per-Second", 5.0d),
                    section.getInt("Count", 1),
                    section.getInt("Interval-Ticks", 4),
                    section.getInt("Fuse-Seconds", 2),
                    section.getDouble("Front-Distance", 1.0d),
                    section.getDouble("Power", 4.0d),
                    section.getInt("Width", 7),
                    section.getInt("Height", 3),
                    section.getDouble("Health", 40.0d),
                    section.getString("Name", "&e诱饵"),
                    section.getDouble("Heal-Per-Second", 4.0d),
                    section.getInt("Amplifier", 2),
                    section.getDouble("Radius", 6.0d),
                    section.getDouble("Knockback-Strength", 2.0d),
                    section.getDouble("Knockback-Y", 0.5d),
                    section.getDouble("Pull-Strength", 0.6d),
                    section.getString("Particle", defaultParticle(type))
            );
        }

        private boolean hasDurationBar() {
            return active && durationSeconds > 0;
        }

        private static SkillType parseType(String value) {
            if (value == null) {
                return SkillType.RAGE;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            try {
                return SkillType.valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                return SkillType.RAGE;
            }
        }

        private static int defaultDuration(SkillType type) {
            switch (type) {
                case RAGE:
                case HEAL:
                case RESISTANCE:
                case STONE_WALL:
                    return 20;
                case BLACK_HOLE:
                    return 7;
                case DECOY:
                    return 15;
                case BLADE:
                case EXPLOSION:
                case KNOCKBACK:
                case LIGHTNING:
                case EXECUTE:
                case LOOTER:
                case GOLEM:
                case PARTICLE_ARROW:
                default:
                    return 0;
            }
        }

        private static String defaultParticle(SkillType type) {
            switch (type) {
                case BLACK_HOLE:
                    return "PORTAL";
                case PARTICLE_ARROW:
                    return "SPELL_WITCH";
                default:
                    return "FIREWORKS_SPARK";
            }
        }

        private static double percent(double value) {
            return value > 1.0d ? value / 100.0d : Math.max(0.0d, value);
        }

        private static String normalizeStatic(String value) {
            return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        }
    }
}
