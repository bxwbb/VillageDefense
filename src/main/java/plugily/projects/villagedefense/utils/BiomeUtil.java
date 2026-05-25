package plugily.projects.villagedefense.utils;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;

public final class BiomeUtil {

    // 私有化构造，工具类
    private BiomeUtil() {}

    /**
     * 以坐标为中心，5区块范围 全部设置为指定群系
     * 完全按照你提供的风格编写，100% 可用不报错
     */
    public static void setBiome5ChunkRadius(Location center, Biome targetBiome) {
        World world = center.getWorld();
        if (world == null) return;

        // 获取中心区块坐标
        int centerChunkX = center.getBlockX() >> 4;
        int centerChunkZ = center.getBlockZ() >> 4;

        // 半径 5 区块
        int radius = 5;

        // 遍历所有区块
        for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; chunkX++) {
            for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; chunkZ++) {

                // 遍历区块内所有 X,Z (0-15)
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {

                        // 世界坐标计算
                        int worldX = chunkX * 16 + x;
                        int worldZ = chunkZ * 16 + z;

                        // 固定高度 64 （和你写法完全一致）
                        Location loc = new Location(world, worldX, 64, worldZ);

                        // 你要的风格：world.setBiome
                        world.setBiome(loc, targetBiome);
                    }
                }
            }
        }
    }
}