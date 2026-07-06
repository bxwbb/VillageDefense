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

package plugily.projects.villagedefense.creatures;

import org.bukkit.ChatColor;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import plugily.projects.minigamesbox.classic.handlers.language.MessageBuilder;
import plugily.projects.minigamesbox.classic.utils.version.ServerVersion;
import plugily.projects.minigamesbox.classic.utils.version.VersionUtils;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.arena.Arena;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * 实体相关的跨版本工具入口。
 *
 * <p>真正的实体创建委托给 {@link BaseCreatureInitializer} 的版本实现。
 * 这里集中处理敌我判断、生命条显示、实体属性调整和少量 NMS 反射访问。</p>
 *
 * @author Plajer
 * <p>
 * Created at 2017
 */
public class CreatureUtils {

    private static String[] villagerNames = ("Jagger,Kelsey,Kelton,Haylie,Harlow,Howard,Wulffric,Winfred,Ashley,Bailey,Beckett,Alfredo,Alfred,Adair,Edgar,ED,Eadwig,Edgaras,Buckley,Stanley,Nuffley,"
            + "Mary,Jeffry,Rosaly,Elliot,Harry,Sam,Rosaline,Tom,Ivan,Kevin,Adam,Emma,Mira,Jeff,Isac,Nico").split(",");
    private static Main plugin;
    private static BaseCreatureInitializer creatureInitializer;
    private static final List<CachedObject> cachedObjects = new ArrayList<>();

    private CreatureUtils() {
    }

    public static void init(Main plugin) {
        CreatureUtils.plugin = plugin;
        // 村民名字从语言文件读取，方便服务器自行本地化或扩充名字池。
        villagerNames = new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_VILLAGER_NAMES").asKey().build().split(",");
        creatureInitializer = initCreatureInitializer();
    }

    public static BaseCreatureInitializer initCreatureInitializer() {
        // 1.8 走 NMS 注册自定义实体；其他版本尽量走 Bukkit/兼容层实现。
        return new plugily.projects.villagedefense.creatures.v1_9_UP.CreatureInitializer();
    }

    public static Object getPrivateField(String fieldName, Class<?> clazz, Object object) {
        // NMS 反射字段会被频繁访问，按 class + fieldName 缓存已取出的对象。
        for (CachedObject cachedObject : cachedObjects) {
            if (cachedObject.getClazz().equals(clazz) && cachedObject.getFieldName().equals(fieldName)) {
                return cachedObject.getObject();
            }
        }
        try {
            Field field = clazz.getDeclaredField(fieldName);

            // 兼容旧运行环境的安全管理器限制。
            AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                field.setAccessible(true);
                return null;
            });

            Object o = field.get(object);
            cachedObjects.add(new CachedObject(fieldName, clazz, o));
            return o;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to retrieve private field of object " + object.getClass() + "!");
            plugin.getLogger().log(Level.WARNING, e.getMessage() + " (fieldName " + fieldName + ", class " + clazz.getName() + ")");
        }
        return null;
    }

    /**
     * Check if the given entity is a arena's enemy.
     * We define the enemy as it's not the player, the villager, the wolf and the iron golem
     *
     * @param entity the entity
     * @return true if it is
     */
    public static boolean isEnemy(Entity entity) {
        return entity instanceof Creature && !(entity instanceof Player || entity instanceof Villager || entity instanceof Wolf || entity instanceof IronGolem);
    }

    /**
     * Applies arena attributes (follow range and optional health bar) to target creature.
     *
     * @param creature creature to apply attributes for
     * @param arena    arena to get health multiplier from
     */
    public static void applyAttributes(Creature creature, Arena arena) {
        creatureInitializer.applyFollowRange(creature);
        if (plugin.getConfigPreferences().getOption("CREATURES_HEALTHBAR")) {
            // 名字通过 metadata 保留，显示名则动态拼接血量。
            creature.setCustomNameVisible(true);
            creature.setMetadata(CreatureUtils.getCreatureInitializer().getCreatureCustomNameMetadata(), new FixedMetadataValue(plugin, ""));
            creature.setCustomName(CreatureUtils.getHealthNameTag(creature));
            // old method
            // creature.setCustomName(StringFormatUtils.getProgressBar((int) creature.getHealth(), (int) VersionUtils.getMaxHealth(creature), 50, "|", ChatColor.YELLOW + "", ChatColor.GRAY + ""));
        }
    }

    /**
     * In damage events, health is modified after all events are listened to
     * we must apply health bar change pre damage event
     *
     * @param creature target to generate health bar for
     * @param damage   final damage taken by enemy before all events have finished
     * @return health bar adjusted to the events' damage
     */
    public static String getHealthNameTagPreDamage(Creature creature, double damage) {
        double health = creature.getHealth() - damage;
        if (health < 0) {
            health = 0;
        }
        double maxHealth = VersionUtils.getMaxHealth(creature);
        ChatColor hpColor;
        if (health >= maxHealth * 0.75) {
            hpColor = ChatColor.GREEN;
        } else if (health >= maxHealth * 0.5) {
            hpColor = ChatColor.GOLD;
        } else if (health >= maxHealth * 0.25) {
            hpColor = ChatColor.YELLOW;
        } else {
            hpColor = ChatColor.RED;
        }

        String name;
        String metaKey = creatureInitializer.getCreatureCustomNameMetadata();
        List<MetadataValue> metaList = creature.getMetadata(metaKey);
        if (!metaList.isEmpty()) {
            MetadataValue metaVal = metaList.get(0);
            name = metaVal.asString();
        } else {
            name = creature.getName();
        }
        return name + " " + hpColor + ChatColor.BOLD + Math.round(health)
                + ChatColor.GRAY + ChatColor.BOLD + "/"
                + ChatColor.GREEN + ChatColor.BOLD + Math.round(maxHealth) + " ❤";
    }

    public static String getHealthNameTag(Creature creature) {
        return getHealthNameTagPreDamage(creature, 0);
    }

    public static float getZombieSpeed() {
        return 1.3f;
    }

    public static float getBabyZombieSpeed() {
        return 2.0f;
    }

    public static String[] getVillagerNames() {
        // 返回副本，避免外部调用者修改静态名字池。
        return villagerNames.clone();
    }

    public static String getRandomVillagerName() {
        return getVillagerNames()[villagerNames.length == 1 ? 0 : ThreadLocalRandom.current().nextInt(villagerNames.length)];
    }

    public static Main getPlugin() {
        return plugin;
    }

    public static BaseCreatureInitializer getCreatureInitializer() {
        return creatureInitializer;
    }
}
