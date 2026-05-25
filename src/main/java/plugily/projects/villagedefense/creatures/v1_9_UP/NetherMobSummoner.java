package plugily.projects.villagedefense.creatures.v1_9_UP;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Spider;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.arena.Arena;
import plugily.projects.villagedefense.arena.managers.spawner.SimpleEnemySpawner;

import java.util.*;

public class NetherMobSummoner {

    private static final long COOLDOWN = 15 * 1000L;
    private static final Map<UUID, Long> COOLDOWN_MAP = new HashMap<>();
    private static final Set<UUID> TRIGGERED_ENTITY = new HashSet<>();

    private static final int[][] FRAME = {
            {-2,0},{-1,0},{0,0},{1,0},{2,0},
            {-2,1},            {2,1},
            {-2,2},            {2,2},
            {-2,3},            {2,3},
            {-2,4},{-1,4},{0,4},{1,4},{2,4}
    };

    private final Main plugin;
    private final Random random = new Random();
    private final NormalWitherSkeleton witherSkeleton;
    private final NormalBlaze blaze;
    private final NormalGhast ghast;

    private final Set<UUID> summoning = new HashSet<>();
    private final Map<UUID, Boolean> originalAIState = new HashMap<>();

    public NetherMobSummoner(Main plugin) {
        this.plugin = plugin;
        this.witherSkeleton = new NormalWitherSkeleton(plugin);
        this.blaze = new NormalBlaze(plugin);
        this.ghast = new NormalGhast(plugin);
    }

    public void startSummon(LivingEntity entity, Arena arena, int wave) {
        if (entity == null || !entity.isValid() || entity.isDead())
            return;

        UUID uuid = entity.getUniqueId();
        if (TRIGGERED_ENTITY.contains(uuid) || summoning.contains(uuid))
            return;

        long now = System.currentTimeMillis();
        if (COOLDOWN_MAP.getOrDefault(uuid, 0L) > now)
            return;

        // 必须在地面
        if (!entity.isOnGround())
            return;

        // 禁止飞行生物、蜘蛛
        if (entity instanceof org.bukkit.entity.Flying || entity instanceof org.bukkit.entity.Blaze || entity instanceof Spider)
            return;

        World world = entity.getWorld();
        if (world == null || world.getName().equalsIgnoreCase("world_nether"))
            return;

        Location center = entity.getLocation().clone();
        center = new Location(world, center.getX(), center.getY() + 3, center.getZ());
        if (!checkPortalSpace(center)) {
            spawnFailParticle(entity.getLocation());
            return;
        }

        // 永久标记已触发
        TRIGGERED_ENTITY.add(uuid);
        COOLDOWN_MAP.put(uuid, now + COOLDOWN);
        summoning.add(uuid);

        // 保存并关闭AI
        if (entity instanceof Mob) {
            originalAIState.put(uuid, ((Mob) entity).hasAI());
            ((Mob) entity).setAI(false);
        }
        entity.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 99999, 0, false, false));

        List<Block> frame = new ArrayList<>();
        Location finalCenter = center;

        // 生成黑曜石框架
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (entity.isDead() || !entity.isValid() || !entity.getWorld().equals(world)) {
                    end(entity, frame);
                    cancel();
                    return;
                }
                if (step >= FRAME.length) {
                    cancel();
                    activatePortal(finalCenter, frame, arena, wave);
                    return;
                }
                int[] pos = FRAME[step];
                Block b = finalCenter.clone().add(pos[0], pos[1], 0).getBlock();
                if (b.getType().isAir()) {
                    b.setType(org.bukkit.Material.OBSIDIAN);
                    frame.add(b);
                }
                step++;
            }
        }.runTaskTimer(plugin, 0, 2);

        // 生物旋转粒子
        new BukkitRunnable() {
            double angle = 0;

            @Override
            public void run() {
                if (entity.isDead() || !entity.isValid() || !entity.getWorld().equals(world)) {
                    end(entity, frame);
                    cancel();
                    return;
                }
                Location loc = entity.getLocation();
                loc.setYaw((float) (loc.getYaw() + 7));
                entity.teleport(loc);

                double x = Math.cos(angle) * 1.4;
                double z = Math.sin(angle) * 1.4;
                Location pl = loc.clone().add(x, 0.8, z);
                entity.getWorld().spawnParticle(Particle.SOUL, pl, 1, 0, 0.1, 0, 0);
                entity.getWorld().spawnParticle(Particle.LARGE_SMOKE, pl.add(0, 0.3, 0), 1, 0, 0.12, 0, 0);
                angle += Math.PI / 16;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private boolean checkPortalSpace(Location center) {
        for (int[] pos : FRAME) {
            Block b = center.clone().add(pos[0], pos[1], 0).getBlock();
            if (!b.getType().isAir()) return false;
        }
        for (int y = 1; y <= 3; y++) {
            for (int x = -1; x <= 1; x++) {
                Block b = center.clone().add(x, y, 0).getBlock();
                if (!b.getType().isAir()) return false;
            }
        }
        return true;
    }

    private void spawnFailParticle(Location loc) {
        loc.getWorld().spawnParticle(Particle.FLAME, loc.add(0,1,0), 20, 0.5, 0.5, 0.5, 0.1);
        loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 15, 0.4, 0.4, 0.4, 0.1);
    }

    // ========== 假传送门：只播放紫色传送门粒子，不生成方块 ==========
    private void activatePortal(Location center, List<Block> frame, Arena arena, int wave) {
        World world = center.getWorld();

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick > 22) {
                    cancel();

                    // 敌人数量 < 60 才生成
                    if (arena.getEnemies().size() < 60) {
                        spawnMobs(center, arena, wave);
                    }

                    // 延迟关闭：播放玻璃破碎声音 + 移除框架
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            world.playSound(center, Sound.BLOCK_GLASS_BREAK, 1.5f, 1.0f);
                            for (Block b : frame) b.setType(org.bukkit.Material.AIR);
                            frame.clear();
                        }
                    }.runTaskLater(plugin, 60);
                    return;
                }

                // 假传送门紫色粒子效果
                for (double y = 1; y <= 3; y += 0.5) {
                    for (double x = -1; x <= 1; x += 0.5) {
                        Location pLoc = center.clone().add(x, y, 0);
                        world.spawnParticle(Particle.PORTAL, pLoc, 2, 0.2, 0.4, 0.2, 0);
                    }
                }

                world.spawnParticle(Particle.FLAME, center.clone().add(0, 2, 0), 10, 1, 1, 1, 0);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(0, 2, 0), 8, 0.8, 0.8, 0.8, 0);
                tick++;
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    private void spawnMobs(Location center, Arena arena, int wave) {
        World world = center.getWorld();
        if (world == null || world.getName().equalsIgnoreCase("world_nether"))
            return;

        int base = 2 + (wave / 10);
        int amount = Math.min(base + random.nextInt(3), 7);

        for (int i = 0; i < amount; i++) {
            double x = (random.nextDouble() - 0.5) * 3;
            double z = (random.nextDouble() - 0.5) * 3;
            Location spawnLoc = center.clone().add(x, -2, z);

            int rnd = random.nextInt(100);
            SimpleEnemySpawner spawner;

            if (wave < 50) {
                spawner = (rnd < 60) ? blaze : witherSkeleton;
            } else if (wave < 70) {
                if (rnd < 40) spawner = blaze;
                else if (rnd < 80) spawner = witherSkeleton;
                else spawner = ghast;
            } else {
                if (rnd < 30) spawner = blaze;
                else if (rnd < 70) spawner = witherSkeleton;
                else spawner = ghast;
            }
            spawn(random, arena, i, spawner, spawnLoc);
        }
    }

    private void spawn(Random random, Arena arena, int spawn, SimpleEnemySpawner simpleEnemySpawner, Location spawnLoc) {
        int wave = arena.getWave();
        int phase = arena.getArenaOption("ZOMBIE_SPAWN_COUNTER");
        int spawnAmount = simpleEnemySpawner.getFinalAmount(arena, wave, phase, spawn);
        double spawnRate = simpleEnemySpawner.getSpawnRate(arena, wave, phase, spawn);
        int weight = simpleEnemySpawner.getSpawnWeight(arena, wave, phase, spawn);

        for (int i = 0; i < spawnAmount; i++) {
            int zombiesToSpawn = arena.getArenaOption("ZOMBIES_TO_SPAWN");
            if (zombiesToSpawn >= weight && spawnRate != 0 && (spawnRate == 1 || random.nextDouble() < spawnRate)) {
                simpleEnemySpawner.spawn(spawnLoc, arena);
                arena.setArenaOption("ZOMBIES_TO_SPAWN", zombiesToSpawn - weight);
            }
        }
    }

    // 恢复生物状态
    private void end(LivingEntity e, List<Block> frame) {
        UUID uuid = e.getUniqueId();
        summoning.remove(uuid);

        if (e instanceof Mob) {
            Mob mob = (Mob) e;
            if (originalAIState.containsKey(uuid)) {
                mob.setAI(originalAIState.get(uuid));
            }
        }

        e.removePotionEffect(PotionEffectType.REGENERATION);
        originalAIState.remove(uuid);

        // 安全清理
        for (Block b : frame) b.setType(org.bukkit.Material.AIR);
        frame.clear();
    }
}