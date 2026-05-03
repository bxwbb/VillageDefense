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

package plugily.projects.villagedefense;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.TestOnly;
import plugily.projects.minigamesbox.api.kit.IKit;
import plugily.projects.minigamesbox.classic.PluginMain;
import plugily.projects.minigamesbox.classic.handlers.setup.SetupInventory;
import plugily.projects.minigamesbox.classic.handlers.setup.categories.PluginSetupCategoryManager;
import plugily.projects.minigamesbox.classic.utils.configuration.ConfigUtils;
import plugily.projects.minigamesbox.classic.utils.services.metrics.Metrics;
import plugily.projects.minigamesbox.classic.utils.version.ServerVersion;
import plugily.projects.villagedefense.arena.*;
import plugily.projects.villagedefense.arena.managers.doors.DoorBreakListener;
import plugily.projects.villagedefense.arena.managers.enemy.spawner.EnemySpawnerRegistry;
import plugily.projects.villagedefense.arena.managers.enemy.spawner.EnemySpawnerRegistryLegacy;
import plugily.projects.villagedefense.boot.AdditionalValueInitializer;
import plugily.projects.villagedefense.boot.MessageInitializer;
import plugily.projects.villagedefense.boot.PlaceholderInitializer;
import plugily.projects.villagedefense.commands.arguments.ArgumentsRegistry;
import plugily.projects.villagedefense.creatures.CreatureUtils;
import plugily.projects.villagedefense.creatures.v1_9_UP.CustomCreatureEvents;
import plugily.projects.villagedefense.creatures.v1_9_UP.NetherMobSummoner;
import plugily.projects.villagedefense.events.PluginEvents;
import plugily.projects.villagedefense.handlers.LanguageMigrator;
import plugily.projects.villagedefense.handlers.powerup.PowerupHandler;
import plugily.projects.villagedefense.handlers.setup.SetupCategoryManager;
import plugily.projects.villagedefense.handlers.upgrade.EntityUpgradeMenu;
import plugily.projects.villagedefense.handlers.upgrade.upgrades.Upgrade;
import plugily.projects.villagedefense.handlers.upgrade.upgrades.UpgradeBuilder;
import plugily.projects.villagedefense.kits.KitAbilityInitializer;
import plugily.projects.villagedefense.kits.KitUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Village Defense 的插件入口，也是本项目接入 miniGameBox 的边界。
 *
 * <p>{@link PluginMain} 负责通用小游戏能力：配置文件、语言、竞技场生命周期、玩家数据、
 * 职业、奖励、权限、占位符、计分板等。本类只注册村庄守卫自己的业务模块。</p>
 *
 * <p>Created by Tom on 12/08/2014.
 * Updated by Tigerpanzer_02 on 03.12.2021</p>
 */
public class Main extends PluginMain {

    private FileConfiguration entityUpgradesConfig;
    private EnemySpawnerRegistryLegacy enemySpawnerRegistry;
    private ArenaRegistry arenaRegistry;
    private ArenaManager arenaManager;
    private ArgumentsRegistry argumentsRegistry;
    private EntityUpgradeMenu entityUpgradeMenu;
    private NetherMobSummoner netherMobSummoner;

    @TestOnly
    public Main() {
        super();
    }

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        // 语言迁移要早于框架加载 language.yml，否则旧键无法映射到当前 Message 注册表。
        new LanguageMigrator(this);
        MessageInitializer messageInitializer = new MessageInitializer(this);

        // 初始化 miniGameBox 的公共管理器。后续 Village Defense 模块都依赖这些 getter。
        super.onEnable();
        getDebugger().debug("[System] [Plugin] Initialization start");

        // 这些初始化只做注册工作，不保存生命周期对象，因此大多不需要字段持有。
        new PlaceholderInitializer(this);
        messageInitializer.registerMessages();
        new AdditionalValueInitializer(this);
        initializePluginClasses();
        addKits();
        getDebugger().debug("Full {0} plugin enabled", getName());
        getDebugger().debug("[System] [Plugin] Initialization finished took {0}ms", System.currentTimeMillis() - start);
    }

    public void initializePluginClasses() {
        // 注册本插件额外的默认配置文件，交给 PluginMain#setupFiles 释放到数据目录。
        addFileName("powerups");
        addFileName("creatures");

        // 框架创建 Arena 时不直接注入 Main，这里用静态 init 维持旧 API 的访问方式。
        Arena.init(this);
        ArenaUtils.init(this);
        new ArenaEvents(this);
        arenaManager = new ArenaManager(this);
        arenaRegistry = new ArenaRegistry(this);
        arenaRegistry.registerArenas();
        getSignManager().loadSigns();
        getSignManager().updateSigns();
        argumentsRegistry = new ArgumentsRegistry(this);

        // 1.8 使用 NMS 自定义实体；高版本使用 Bukkit/兼容层实现，外部统一通过 Legacy 基类访问。
        enemySpawnerRegistry = new EnemySpawnerRegistry(this);

        // 升级系统是可选功能，关闭时避免加载额外配置和事件菜单。
        if (getConfigPreferences().getOption("UPGRADES")) {
            entityUpgradesConfig = ConfigUtils.getConfig(this, "entity_upgrades");
            Upgrade.init(this);
            UpgradeBuilder.init(this);
            entityUpgradeMenu = new EntityUpgradeMenu(this);
        }
        new DoorBreakListener(this);
        CreatureUtils.init(this);
        new PowerupHandler(this);
        new PluginEvents(this);
        netherMobSummoner = new NetherMobSummoner(this);
        addPluginMetrics();
    }

    public void addKits() {
        if (!getConfigPreferences().getOption("KITS")) {
            // Kits are disabled, no kits will be loaded
            return;
        }
        long start = System.currentTimeMillis();
        // 先注册技能处理器，再让 KitRegistry 读取各 kit yml，否则 ability key 无法解析。
        new KitAbilityInitializer(this);
        getDebugger().performance("Kit", "Adding kits...");
        addFileName("kits/archer");
        addFileName("kits/blocker");
        addFileName("kits/cleaner");
        addFileName("kits/dog_friend");
        addFileName("kits/golem_friend");
        addFileName("kits/hardcore");
        addFileName("kits/hardcore_master");
        addFileName("kits/healer");
        addFileName("kits/heavy_tank");
        addFileName("kits/knight");
        addFileName("kits/light_tank");
        addFileName("kits/looter");
        addFileName("kits/medic");
        addFileName("kits/medium_tank");
        addFileName("kits/puncher");
        addFileName("kits/runner");
        addFileName("kits/shotbow_master");
        addFileName("kits/teleporter");
        addFileName("kits/terminator");
        addFileName("kits/tornado");
        addFileName("kits/wild_naked");
        addFileName("kits/wizard");
        addFileName("kits/worker");
        addFileName("kits/zombie_teleporter");
        List<String> optionalConfigurations = new ArrayList<>();
        optionalConfigurations.add("restock");
        optionalConfigurations.add("cooldown");

        // miniGameBox 负责识别职业物品；实际点击效果委托给本插件的 KitUtils。
        getKitRegistry().setHandleItem((player, item) -> KitUtils.handleItem(this, player, item));
        getKitRegistry().registerKits(optionalConfigurations);
        getDebugger().debug(Level.INFO, "Kits loaded: ");
        for (IKit kit : getKitRegistry().getKits()) {
            getDebugger().debug(kit.getName());
        }
        getKitRegistry().setDefaultKit("knight");
        getDebugger().debug("Kit adding finished took {0}ms", System.currentTimeMillis() - start);
    }

    private void addPluginMetrics() {
        getMetrics().addCustomChart(new Metrics.SimplePie("hooked_addons", () -> {
            if (getServer().getPluginManager().getPlugin("VillageDefense-Enhancements") != null) {
                return "Enhancements";
            }
            return "None";
        }));
    }

    public FileConfiguration getEntityUpgradesConfig() {
        return entityUpgradesConfig;
    }

    public EnemySpawnerRegistryLegacy getEnemySpawnerRegistry() {
        return enemySpawnerRegistry;
    }

    @Override
    public ArenaRegistry getArenaRegistry() {
        return arenaRegistry;
    }

    @Override
    public ArgumentsRegistry getArgumentsRegistry() {
        return argumentsRegistry;
    }

    @Override
    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public EntityUpgradeMenu getEntityUpgradeMenu() {
        return entityUpgradeMenu;
    }

    public NetherMobSummoner getNetherMobSummoner() {
        return netherMobSummoner;
    }

    @Override
    public PluginSetupCategoryManager getSetupCategoryManager(SetupInventory setupInventory) {
        return new SetupCategoryManager(setupInventory);
    }
}
