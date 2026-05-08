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

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import plugily.projects.minigamesbox.api.arena.IArenaState;
import plugily.projects.minigamesbox.api.user.IUser;
import plugily.projects.minigamesbox.classic.arena.PluginArenaEvents;
import plugily.projects.minigamesbox.classic.handlers.items.SpecialItem;
import plugily.projects.minigamesbox.classic.handlers.language.MessageBuilder;
import plugily.projects.minigamesbox.classic.utils.misc.complement.ComplementAccessor;
import plugily.projects.minigamesbox.classic.utils.version.ServerVersion;
import plugily.projects.minigamesbox.classic.utils.version.VersionUtils;
import plugily.projects.minigamesbox.classic.utils.version.events.api.PlugilyEntityPickupItemEvent;
import plugily.projects.minigamesbox.classic.utils.version.xseries.XEntityType;
import plugily.projects.minigamesbox.classic.utils.version.xseries.XMaterial;
import plugily.projects.minigamesbox.classic.utils.version.xseries.XSound;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.creatures.CreatureUtils;

import java.util.Random;

/**
 * 村庄守卫的竞技场事件扩展。
 *
 * <p>{@link PluginArenaEvents} 已处理通用小游戏事件。本类只处理玩法相关规则：
 * 村民受伤/死亡、宠物死亡、腐肉掉落、玩家死亡转旁观、狼击杀归属等。</p>
 *
 * @author Plajer
 * <p>
 * Created at 13.03.2018
 */
public class ArenaEvents extends PluginArenaEvents {

    private final Main plugin;

    public ArenaEvents(Main plugin) {
        super(plugin);
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    //override WorldGuard build deny flag where villagers cannot be damaged
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVillagerDamage(EntityDamageByEntityEvent e) {
        // 只允许竞技场内敌人伤害本竞技场村民，不影响普通世界村民。
        if (e.getEntityType() != XEntityType.VILLAGER.get() || !(e.getDamager() instanceof Creature)) {
            return;
        }

        for (Arena arena : plugin.getArenaRegistry().getPluginArenas()) {
            if (arena.getVillagers().contains(e.getEntity()) && arena.getEnemies().contains(e.getDamager())) {
                e.setCancelled(false);
                e.getEntity().setCustomName(CreatureUtils.getHealthNameTagPreDamage((Creature) e.getEntity(), e.getFinalDamage()));
                XSound.ENTITY_VILLAGER_HURT.play(e.getEntity().getLocation(), 30.0f, 1.0f);
                break;
            }
        }
    }

    @EventHandler
    public void onDieEntity(EntityDamageByEntityEvent e) {
        // Bukkit 不会把狼击杀算作玩家击杀，这里给狼主人补统计和经验。
        if (!(e.getDamager() instanceof Wolf && e.getEntity() instanceof Creature)) {
            return;
        }

        if (e.getDamage() >= ((Creature) e.getEntity()).getHealth()) {

            //trick to get non player killer of zombie
            for (Arena arena : plugin.getArenaRegistry().getPluginArenas()) {
                if (arena.getEnemies().contains(e.getEntity())) {
                    org.bukkit.entity.AnimalTamer owner = ((Wolf) e.getDamager()).getOwner();

                    if (owner instanceof Player) { //prevent offline player cast error
                        Player player = (Player) owner;

                        if (plugin.getArenaRegistry().getArena(player) != null) {
                            plugin.getUserManager().addStat(player, plugin.getStatsStorage().getStatisticType("KILLS"));
                            plugin.getUserManager().addExperience(player, 2 * arena.getArenaOption("CREATURE_DIFFICULTY_MULTIPLIER"));
                        }
                    }

                    break;
                }
            }
        }
    }

    @EventHandler
    public void onItemDrop(ItemSpawnEvent e) {
        org.bukkit.entity.Item item = e.getEntity();

        // 腐肉是本玩法资源，其他掉落不进入 Arena 跟踪集合。
        if (item.getItemStack().getType() != Material.ROTTEN_FLESH) {
            return;
        }

        Location itemLoc = item.getLocation();

        for (Arena arena : plugin.getArenaRegistry().getPluginArenas()) {
            Location start = arena.getStartLocation();

            // 只接管竞技场附近腐肉，避免误处理其他小游戏或普通世界掉落。
            if (itemLoc.getWorld() != start.getWorld() || itemLoc.distance(start) > 150) {
                continue;
            }

            arena.addDroppedFlesh(item);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        // 友方实体死亡由 Arena 集合统一管理，避免默认死亡流程产生重复掉落或残留引用。
        if (event.getEntityType() != XEntityType.IRON_GOLEM.get() && event.getEntityType() != XEntityType.WOLF.get())
            return;

        for (Arena arena : plugin.getArenaRegistry().getPluginArenas()) {
            switch (XEntityType.of(event.getEntityType())) {
                case IRON_GOLEM:
                    if (!arena.getIronGolems().contains(event.getEntity())) {
                        continue;
                    }

                    IronGolem ironGolem = (IronGolem) event.getEntity();

                    if (ironGolem.getHealth() <= event.getDamage()) {
                        event.setCancelled(true);
                        event.setDamage(0);
                        arena.removeIronGolem(ironGolem);
                    }
                    return;
                case PILLAGER:
                    if (!arena.getPillagers().contains(event.getEntity())) {
                        continue;
                    }

                    Pillager pillager = (Pillager) event.getEntity();

                    if (pillager.getHealth() <= event.getDamage()) {
                        event.setCancelled(true);
                        event.setDamage(0);
                        arena.removePillager(pillager);
                    }
                    return;
                case WOLF:
                    if (!arena.getWolves().contains(event.getEntity())) {
                        continue;
                    }

                    Wolf wolf = (Wolf) event.getEntity();
                    if (wolf.getHealth() <= event.getDamage()) {
                        event.setCancelled(true);
                        event.setDamage(0);

                        java.util.UUID ownerUUID = (wolf.getOwner() != null) ? wolf.getOwner().getUniqueId() : null;

                        if (ownerUUID != null) {
                            Player playerOwner = plugin.getServer().getPlayer(ownerUUID);

                            if (playerOwner != null)
                                new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_WAVE_ENTITIES_WOLF_DEATH").asKey().player(playerOwner).sendPlayer();
                        }
                        arena.removeWolf(wolf);
                    }
                    return;
                case GHAST:
                case BLAZE:
                case WITHER_SKELETON:
                case VILLAGER:
                    return;
                default:
                    Entity entity = event.getEntity();
                    if (entity instanceof LivingEntity) {
                        LivingEntity livingEntity = (LivingEntity) entity;
                        double h = livingEntity.getHealth() / VersionUtils.getMaxHealth(livingEntity);
                        Random random = new Random();
                        if (h < 0.5d && h >= 0.3d && random.nextInt(100) < 20) {

                        }
                    }
            }
        }
    }

    @EventHandler
    public void onVillagerDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Creature)) {
            return;
        }
        for (Arena arena : plugin.getArenaRegistry().getPluginArenas()) {
            if (event.getEntityType() == XEntityType.VILLAGER.get()) {
                if (!arena.getVillagers().contains(entity)) {
                    continue;
                }
                // 村民死亡是核心失败条件之一，清空掉落并触发奖励/节日效果。
                arena.getStartLocation().getWorld().strikeLightningEffect(entity.getLocation());
                event.getDrops().clear();
                event.setDroppedExp(0);
                arena.removeVillager((Villager) entity);
                plugin.getRewardsHandler().performReward(null, arena, plugin.getRewardsHandler().getRewardType("VILLAGER_DEATH"));
                plugin.getHolidayManager().applyHolidayDeathEffects(entity);
                new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_VILLAGER_DIED").asKey().arena(arena).sendArena();
            } else if (ServerVersion.Version.isCurrentEqualOrLower(ServerVersion.Version.v1_8_8)) {
                // 1.8 NMS 自定义实体的死亡统计走这里；高版本在 v1_9_UP 事件中处理。
                if (!arena.getEnemies().contains(entity)) {
                    continue;
                }
                arena.removeEnemy((Creature) entity);
                arena.changeArenaOptionBy("TOTAL_KILLED_ZOMBIES", 1);

                Player killer = entity.getKiller();
                Arena killerArena = plugin.getArenaRegistry().getArena(killer);

                if (killerArena != null) {
                    plugin.getUserManager().addStat(killer, plugin.getStatsStorage().getStatisticType("KILLS"));
                    plugin.getUserManager().addExperience(killer, 2 * arena.getArenaOption("CREATURE_DIFFICULTY_MULTIPLIER"));
                    plugin.getRewardsHandler().performReward(killer, plugin.getRewardsHandler().getRewardType("ZOMBIE_KILL"));
                    plugin.getPowerupRegistry().spawnPowerup(entity.getLocation(), killerArena);
                }
            }
            break;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDie(PlayerDeathEvent e) {
        Arena arena = plugin.getArenaRegistry().getArena(e.getEntity());
        if (arena == null) {
            return;
        }

        final Player player = e.getEntity();
        PlayerInventory inventory = player.getInventory();

        // 手动掉落背包，再清空 Bukkit 默认掉落，避免死亡和旁观切换过程中重复物品。
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && Material.AIR != item.getType() && item.getType().isItem()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }

        plugin.getRewardsHandler().performReward(player, arena, plugin.getRewardsHandler().getRewardType("PLAYER_DEATH"));
        ComplementAccessor.getComplement().setDeathMessage(e, "");
        e.getDrops().clear();
        e.setDroppedExp(0);
        plugin.getHolidayManager().applyHolidayDeathEffects(player);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> player.spigot().respawn(), 5);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (arena.getArenaState() == IArenaState.STARTING) {
                // 开始倒计时死亡不进入本波旁观逻辑，直接拉回起点。
                VersionUtils.teleport(player, arena.getStartLocation());
                return;
            }

            if (arena.getArenaState() == IArenaState.ENDING || arena.getArenaState() == IArenaState.RESTARTING) {
                // 结束/重启阶段只做清理和传送，避免玩家卡在竞技场世界。
                inventory.clear();
                player.setFlying(false);
                player.setAllowFlight(false);
                plugin.getUserManager().getUser(player).setStatistic("ORBS", 0);
                VersionUtils.teleport(player, arena.getEndLocation());
                return;
            }

            IUser user = plugin.getUserManager().getUser(player);

            // 战斗阶段死亡后成为本波旁观者，是否下一波复活由配置控制。
            plugin.getUserManager().addStat(user, plugin.getStatsStorage().getStatisticType("DEATHS"));
            VersionUtils.teleport(player, arena.getStartLocation());
            user.setSpectator(true);
            player.setGameMode(GameMode.SURVIVAL);

            modifyUserOrbs(user);

            ArenaUtils.hidePlayer(player, arena);
            player.setAllowFlight(true);
            player.setFlying(true);
            inventory.clear();
            VersionUtils.sendTitle(player, new MessageBuilder("IN_GAME_DEATH_SCREEN").asKey().build(), 0, 5 * 20, 0);
            sendSpectatorActionBar(user, arena);
            new MessageBuilder(MessageBuilder.ActionType.DEATH).arena(arena).player(player).sendArena();

            plugin.getSpecialItemManager().addSpecialItemsOfStage(player, SpecialItem.DisplayStage.SPECTATOR);

            arena.getCreatureTargetManager().unTargetPlayerFromZombies(player, arena);
        });
    }

    private void sendSpectatorActionBar(IUser user, Arena arena) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (arena.getArenaState() == IArenaState.ENDING || !user.isSpectator()) {
                    cancel();
                    return;
                }
                Player player = user.getPlayer();
                if (player == null) {
                    cancel();
                } else {
                    VersionUtils.sendActionBar(player, new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_WAVE_RESPAWN_ON_NEXT").asKey().player(player).arena(arena).build());
                }
            }
        }.runTaskTimer(plugin, 30, 30);
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        Arena arena = plugin.getArenaRegistry().getArena(e.getPlayer());
        if (arena == null) {
            return;
        }
        Player player = e.getPlayer();
        player.setAllowFlight(true);
        player.setFlying(true);
        IUser user = plugin.getUserManager().getUser(player);
        if (!user.isSpectator()) {
            user.setSpectator(true);
            player.setGameMode(GameMode.SURVIVAL);
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            player.removePotionEffect(PotionEffectType.SPEED);

            modifyUserOrbs(user);
        }
        e.setRespawnLocation(arena.getStartLocation());
    }

    private void modifyUserOrbs(IUser user) {
        int deathValue = plugin.getConfig().getInt("Orbs.Death.Value", 50);
        int current = user.getStatistic("ORBS");
        // 死亡后的宝珠处理是玩法配置：保留、加减固定值、设定值或百分比缩放。
        switch (getOrbDeathType()) {
            case KEEP:
                return;
            case AMOUNT:
                user.setStatistic("ORBS", (Math.max(current + deathValue, 0)));
                break;
            case SET:
                user.setStatistic("ORBS", deathValue);
                break;
            case PERCENTAGE:
                user.setStatistic("ORBS", current * (deathValue / 100));
                break;
            default:
                break;
        }
    }

    private OrbDeathType getOrbDeathType() {
        return OrbDeathType.valueOf(plugin.getConfig().getString("Orbs.Death.Type", "KEEP"));
    }

    private enum OrbDeathType {
        PERCENTAGE, AMOUNT, SET, KEEP
    }

    @EventHandler
    public void onPickup(PlugilyEntityPickupItemEvent e) {
        // 只拦截玩家拾取腐肉，旁观者不可拾取，正常玩家拾取后从 Arena 跟踪集合移除。
        if (e.getEntity().getType() != XEntityType.PLAYER.get() || e.getItem().getItemStack().getType() != XMaterial.ROTTEN_FLESH.get()) {
            return;
        }
        Player player = (Player) e.getEntity();
        Arena arena = plugin.getArenaRegistry().getArena(player);
        if (arena == null) {
            return;
        }
        if (plugin.getUserManager().getUser(player).isSpectator()) {
            e.setCancelled(true);
        }
        arena.removeDroppedFlesh(e.getItem());
    }


}
