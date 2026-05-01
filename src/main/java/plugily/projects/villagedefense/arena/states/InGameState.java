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

package plugily.projects.villagedefense.arena.states;

import plugily.projects.minigamesbox.api.arena.IArenaState;
import plugily.projects.minigamesbox.classic.arena.PluginArena;
import plugily.projects.minigamesbox.classic.arena.states.PluginInGameState;
import plugily.projects.minigamesbox.classic.handlers.language.MessageBuilder;
import plugily.projects.minigamesbox.classic.utils.version.ServerVersion;
import plugily.projects.villagedefense.arena.Arena;

/**
 * IN_GAME 状态的村庄守卫主循环。
 *
 * <p>miniGameBox 每个 arena tick 会调用这里。父类处理通用倒计时和状态维护；
 * 本类处理本玩法的胜负判断、刷怪、卡怪清理和波次切换。</p>
 *
 * @author Plajer
 * <p>
 * Created at 03.06.2019
 */
public class InGameState extends PluginInGameState {

    /**
     * 每tick执行，游戏循环
     * @param arena 区域
     */
    @Override
    public void handleCall(PluginArena arena) {
        super.handleCall(arena);
        // 框架传入通用 PluginArena，这里转回本插件 Arena 以访问敌人、村民和波次数据。
        Arena pluginArena = (Arena) getPlugin().getArenaRegistry().getArena(arena.getId());
        if (pluginArena == null) {
            return;
        }
        // 内部有计数器，不是每 tick 全量扫描实体。
        pluginArena.getEnemySpawnManager().spawnGlitchCheck();

        // 村民全灭或存活玩家全灭即结束。ENDING 状态下避免重复 stopGame。
        if (pluginArena.getVillagers().isEmpty() || arena.getPlayersLeft().isEmpty() && arena.getArenaState() != IArenaState.ENDING) {
            getPlugin().getArenaManager().stopGame(false, arena);
            return;
        }
        // 剩余僵尸数
        int zombiesLeft = pluginArena.getZombiesLeft();
        getPlugin().getDebugger().debug("Arena {0} Zombies to spawn {1} Zombies left {2} Fighting {3}", arena.getId(), arena.getArenaOption("ZOMBIES_TO_SPAWN"), zombiesLeft, pluginArena.isFighting());
        if (pluginArena.isFighting()) {
            if (ServerVersion.Version.isCurrentHigher(ServerVersion.Version.v1_8_8)) {
                // 1.9+ 路径没有 1.8 NMS 那套目标 AI，运行中主动刷新目标。
                pluginArena.getCreatureTargetManager().targetCreatures();
                pluginArena.getCreatureTargetManager().targetRideableCreatures();
            }
            if (zombiesLeft <= 0) {
                // 待生成和已生成敌人均清空，当前波结束。
                pluginArena.setFighting(false);
                pluginArena.getPlugin().getArenaManager().endWave(pluginArena);
            } else if (arena.getArenaOption("ZOMBIES_TO_SPAWN") > 0) {
                // 还有待生成敌人时继续刷，并把波次超时清理计时器拉长。
                pluginArena.getEnemySpawnManager().spawnEnemies();
                setArenaTimer(500);
            }
            if (ServerVersion.Version.isCurrentEqualOrHigher(ServerVersion.Version.v1_9)) {
                // 高版本提供 glowing，用于提示最后几只怪的位置。
                int zombiesLeftFrom = getPlugin().getConfig().getInt("Glowing-Status.Creatures-Left");
                int startingWave;
                if (zombiesLeftFrom > 0 && zombiesLeft <= zombiesLeftFrom
                        && (startingWave = getPlugin().getConfig().getInt("Glowing-Status.Starting-Wave")) > 0
                        && pluginArena.getWave() >= startingWave) {
                    for (org.bukkit.entity.Creature remaining : pluginArena.getEnemies()) {
                        if (!remaining.isGlowing()) { // To avoid setting glowing property every time
                            remaining.setGlowing(true);
                        }
                    }
                }
            }
            if (arena.getTimer() == 0) {
                // 兜底清怪：防止路径问题或卡实体导致波次永远无法结束。
                pluginArena.getMapRestorerManager().clearEnemiesFromArena();
                if (pluginArena.getZombiesLeft() > 0) {
                    new MessageBuilder("IN_GAME_MESSAGES_VILLAGE_WAVE_STUCK_ZOMBIES").asKey().arena(arena).sendArena();
                }
                arena.setArenaOption("ZOMBIES_TO_SPAWN", 0);
            }
            if (arena.getArenaOption("ZOMBIES_TO_SPAWN") < 0) {
                // 加权刷怪器可能一次扣多个权重，防止显示和判断出现负数。
                arena.setArenaOption("ZOMBIES_TO_SPAWN", 0);
            }
        } else if (arena.getTimer() <= 0) {
            // 非战斗阶段倒计时结束后进入下一波。
            pluginArena.setFighting(true);
            pluginArena.getPlugin().getArenaManager().startWave(pluginArena);
        }
    }

}
