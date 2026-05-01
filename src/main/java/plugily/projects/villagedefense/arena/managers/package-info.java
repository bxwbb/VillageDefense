/**
 * 单局 Arena 内部管理器。
 *
 * <p>这里按职责拆分商店、刷怪、目标选择、地图恢复、计分板和门处理逻辑。
 * 管理器通常持有 Arena 引用，通过 Arena 方法读写运行期状态。</p>
 */
package plugily.projects.villagedefense.arena.managers;
