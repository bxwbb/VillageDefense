package plugily.projects.villagedefense.network;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import plugily.projects.minigamesbox.api.arena.IArenaState;
import plugily.projects.minigamesbox.api.events.game.PlugilyGameStateChangeEvent;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.arena.Arena;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkRoomManager implements Listener, PluginMessageListener {

    private static final String CHANNEL = "BungeeCord";

    private final Main plugin;
    private final Map<String, RoomSnapshot> localRooms = new ConcurrentHashMap<>();
    private final RedisRoomDirectory redisRoomDirectory;
    private final String configuredServerName;
    private BukkitTask queuedPublishTask;
    private long queuedPublishAt;
    private volatile String detectedServerName;
    private volatile boolean serverNameRequested;

    public NetworkRoomManager(Main plugin) {
        this.plugin = plugin;
        this.redisRoomDirectory = new RedisRoomDirectory(plugin);
        this.configuredServerName = safe(plugin.getConfig().getString("Network.Server-Name", ""), "");
        this.detectedServerName = configuredServerName.isEmpty() ? null : configuredServerName;

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);

        long interval = 20L * Math.max(5, plugin.getConfig().getInt("Network.Rooms.Publish-Interval-Seconds", 10));
        Bukkit.getScheduler().runTaskTimer(plugin, this::publishLocalRooms, 20L, interval);
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        try {
            Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
            Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        } catch (IllegalArgumentException ignored) {
        }
        if (queuedPublishTask != null) {
            queuedPublishTask.cancel();
            queuedPublishTask = null;
        }
        redisRoomDirectory.close();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("Network.Enabled", false) && redisRoomDirectory.isEnabled();
    }

    public List<RoomSnapshot> getRoomsForSelection() {
        refreshLocalRoomsFromArenas();
        if (!isEnabled()) {
            return new ArrayList<>(localRooms.values());
        }
        requestRoomPublish();

        Map<String, RoomSnapshot> merged = new LinkedHashMap<>();
        for (RoomSnapshot snapshot : redisRoomDirectory.loadAllRooms()) {
            merged.put(roomKey(snapshot.getServerName(), snapshot.getArenaId()), snapshot);
        }
        for (RoomSnapshot snapshot : localRooms.values()) {
            merged.put(roomKey(snapshot.getServerName(), snapshot.getArenaId()), snapshot);
        }
        return new ArrayList<>(merged.values());
    }

    public void joinRoom(Player player, RoomSnapshot snapshot) {
        if (player == null || snapshot == null) {
            return;
        }

        if (isLocalRoom(snapshot)) {
            joinLocalArena(player, snapshot.getArenaId());
            return;
        }

        if (!snapshot.isJoinable()) {
            player.sendMessage(color("&c目标房间已满，暂时无法加入。"));
            return;
        }

        if (!isEnabled()) {
            player.sendMessage(color("&c当前服务器未开启跨服房间联动。"));
            return;
        }

        String serverName = safe(snapshot.getServerName(), "");
        if (serverName.isEmpty()) {
            player.sendMessage(color("&c目标房间缺少服务器名，无法跨服加入。"));
            return;
        }

        int expireSeconds = Math.max(10, plugin.getConfig().getInt("Network.Pending-Join.Expire-Seconds", 30));
        redisRoomDirectory.savePendingJoin(player.getUniqueId(), serverName, snapshot.getArenaId(), System.currentTimeMillis() + expireSeconds * 1000L);
        player.sendMessage(color("&a正在切换到房间 &f" + serverName + "&a ..."));
        connectPlayer(player, serverName);
    }

    public void handleJoinOnServer(Player player) {
        if (player == null || !isEnabled()) {
            return;
        }

        PendingJoin pending = redisRoomDirectory.getPendingJoin(player.getUniqueId());
        if (pending == null) {
            return;
        }
        if (pending.isExpired()) {
            redisRoomDirectory.clearPendingJoin(player.getUniqueId());
            return;
        }
        if (!hasResolvedServerName()) {
            requestLocalServerName(player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> handleJoinOnServer(player), 20L);
            return;
        }
        if (!pending.getServerName().equalsIgnoreCase(getLocalServerName())) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> joinLocalArena(player, pending.getArenaId()));
        redisRoomDirectory.clearPendingJoin(player.getUniqueId());
    }

    public void refreshLocalRoomsFromArenas() {
        localRooms.clear();
        for (Arena arena : plugin.getArenaRegistry().getPluginArenas()) {
            localRooms.put(roomKey(getLocalServerName(), arena.getId()), fromArena(arena));
        }
    }

    public boolean isRemoteRoom(RoomSnapshot snapshot) {
        return isEnabled() && snapshot != null && !isLocalRoom(snapshot);
    }

    public void requestRoomPublish() {
        requestRoomPublish(1L);
    }

    public void requestRoomPublish(long delayTicks) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> requestRoomPublish(delayTicks));
            return;
        }
        if (!isEnabled()) {
            return;
        }

        long normalizedDelay = Math.max(0L, delayTicks);
        long requestedAt = System.currentTimeMillis() + normalizedDelay * 50L;
        if (queuedPublishTask != null && queuedPublishAt <= requestedAt) {
            return;
        }
        if (queuedPublishTask != null) {
            queuedPublishTask.cancel();
        }

        queuedPublishAt = requestedAt;
        queuedPublishTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            queuedPublishTask = null;
            queuedPublishAt = 0L;
            publishLocalRooms();
        }, normalizedDelay);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        requestLocalServerName(event.getPlayer());
        Bukkit.getScheduler().runTaskLater(plugin, () -> handleJoinOnServer(event.getPlayer()), 20L);
        requestRoomPublish(40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        requestRoomPublish(20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameStateChange(PlugilyGameStateChangeEvent event) {
        requestRoomPublish(1L);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().trim();
        String lower = message.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("/vd ") && !lower.startsWith("/villagedefense ") && !lower.startsWith("/villaged ")) {
            return;
        }

        String[] parts = message.startsWith("/") ? message.substring(1).split("\\s+") : message.split("\\s+");
        if (parts.length < 2 || !isVillageDefenseCommand(parts[0].toLowerCase(Locale.ROOT))) {
            return;
        }

        String argument = parts[1].toLowerCase(Locale.ROOT);
        if ("randomjoin".equals(argument)) {
            event.setCancelled(true);
            String requestedMode = parts.length >= 3 ? parseMode(parts[2]) : null;
            if (parts.length >= 3 && requestedMode == null) {
                event.getPlayer().sendMessage(color("&c未知游戏模式，可用: easy, hard, endless。"));
                return;
            }
            joinBestRandomRoom(event.getPlayer(), requestedMode);
            return;
        }
        if (!"join".equals(argument)) {
            return;
        }

        if (!isEnabled()) {
            return;
        }

        if (parts.length == 2) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(color("&c用法: /vd join <房间ID>"));
            return;
        }

        JoinTarget target = parseJoinTarget(parts[2]);
        if (target == null) {
            return;
        }

        RoomSnapshot targetRoom = findBestTargetRoom(target.getArenaId(), target.getServerName());
        if (targetRoom == null) {
            if (target.getServerName() == null && plugin.getArenaRegistry().getArena(target.getArenaId()) != null) {
                return;
            }
            event.setCancelled(true);
            event.getPlayer().sendMessage(color("&c没有找到目标跨服房间。"));
            return;
        }

        event.setCancelled(true);
        joinRoom(event.getPlayer(), targetRoom);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || message.length == 0) {
            return;
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(message))) {
            String subChannel = input.readUTF();
            if ("GetServer".equalsIgnoreCase(subChannel)) {
                String serverName = safe(input.readUTF(), "");
                if (!serverName.isEmpty()) {
                    detectedServerName = serverName;
                    serverNameRequested = false;
                    requestRoomPublish(0L);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void publishLocalRooms() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::publishLocalRooms);
            return;
        }
        if (!isEnabled()) {
            return;
        }
        if (!hasResolvedServerName()) {
            requestLocalServerName();
            return;
        }
        refreshLocalRoomsFromArenas();
        List<RoomSnapshot> snapshots = new ArrayList<>(localRooms.values());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> snapshots.forEach(redisRoomDirectory::saveRoom));
    }

    private void joinBestRandomRoom(Player player, String mode) {
        RoomSnapshot randomRoom = findBestRandomRoom(mode);
        if (randomRoom == null) {
            player.sendMessage(color("&c当前没有可随机加入的等待中房间。"));
            return;
        }
        joinRoom(player, randomRoom);
    }

    private void joinLocalArena(Player player, String arenaId) {
        Arena arena = plugin.getArenaRegistry().getArena(arenaId);
        if (arena == null) {
            player.sendMessage(color("&c没有找到目标房间。"));
            return;
        }
        plugin.getArenaManager().joinAttempt(player, arena);
        requestRoomPublish(2L);
    }

    private void requestLocalServerName(Player player) {
        if (player == null || hasResolvedServerName() || serverNameRequested) {
            return;
        }
        serverNameRequested = true;
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(stream);
            out.writeUTF("GetServer");
            player.sendPluginMessage(plugin, CHANNEL, stream.toByteArray());
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!hasResolvedServerName()) {
                    serverNameRequested = false;
                }
            }, 40L);
        } catch (IOException ex) {
            serverNameRequested = false;
        }
    }

    private void requestLocalServerName() {
        if (hasResolvedServerName() || serverNameRequested) {
            return;
        }
        Player player = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (player != null) {
            requestLocalServerName(player);
        }
    }

    private void connectPlayer(Player player, String serverName) {
        if (player == null || serverName == null || serverName.isEmpty()) {
            return;
        }
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(stream);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            player.sendPluginMessage(plugin, CHANNEL, stream.toByteArray());
        } catch (IOException ex) {
            player.sendMessage(color("&c无法连接到目标服务器。"));
        }
    }

    private RoomSnapshot fromArena(Arena arena) {
        return new RoomSnapshot(
                getLocalServerName(),
                arena.getId(),
                arena.getMapName(),
                arena.getGameMode() == null ? "ENDLESS" : arena.getGameMode().name(),
                arena.getArenaState() == IArenaState.IN_GAME,
                arena.getPlayers().size(),
                arena.getMaximumPlayers(),
                Bukkit.getOnlinePlayers().size(),
                arena.getWave(),
                arena.getFinalWave(),
                System.currentTimeMillis()
        );
    }

    private RoomSnapshot findBestRandomRoom(String mode) {
        RoomSnapshot bestOccupied = null;
        RoomSnapshot bestEmpty = null;
        for (RoomSnapshot room : getRoomsForSelection()) {
            if (room == null || room.isInGame() || !room.isJoinable()) {
                continue;
            }
            if (mode != null && !mode.equalsIgnoreCase(safe(room.getMode(), ""))) {
                continue;
            }
            if (room.getPlayers() > 0) {
                if (bestOccupied == null || compareOccupiedRoom(room, bestOccupied) < 0) {
                    bestOccupied = room;
                }
            } else if (bestEmpty == null || compareEmptyRoom(room, bestEmpty) < 0) {
                bestEmpty = room;
            }
        }
        return bestOccupied != null ? bestOccupied : bestEmpty;
    }

    private RoomSnapshot findBestTargetRoom(String arenaId, String serverName) {
        if (arenaId == null || arenaId.trim().isEmpty()) {
            return null;
        }

        RoomSnapshot bestWaiting = null;
        RoomSnapshot bestAny = null;
        for (RoomSnapshot room : getRoomsForSelection()) {
            if (room == null || room.getArenaId() == null || !arenaId.equalsIgnoreCase(room.getArenaId()) || !room.isJoinable()) {
                continue;
            }
            if (serverName != null && (room.getServerName() == null || !serverName.equalsIgnoreCase(room.getServerName()))) {
                continue;
            }
            if (bestAny == null || compareTargetRoom(room, bestAny) < 0) {
                bestAny = room;
            }
            if (!room.isInGame() && (bestWaiting == null || compareTargetRoom(room, bestWaiting) < 0)) {
                bestWaiting = room;
            }
        }
        return bestWaiting != null ? bestWaiting : bestAny;
    }

    private int compareOccupiedRoom(RoomSnapshot first, RoomSnapshot second) {
        int roomPlayers = Integer.compare(second.getPlayers(), first.getPlayers());
        if (roomPlayers != 0) {
            return roomPlayers;
        }
        int serverPlayers = Integer.compare(first.getServerPlayers(), second.getServerPlayers());
        if (serverPlayers != 0) {
            return serverPlayers;
        }
        return compareRoomIdentity(first, second);
    }

    private int compareEmptyRoom(RoomSnapshot first, RoomSnapshot second) {
        int serverPlayers = Integer.compare(first.getServerPlayers(), second.getServerPlayers());
        if (serverPlayers != 0) {
            return serverPlayers;
        }
        return compareRoomIdentity(first, second);
    }

    private int compareTargetRoom(RoomSnapshot first, RoomSnapshot second) {
        if (first.getPlayers() > 0 || second.getPlayers() > 0) {
            return compareOccupiedRoom(first, second);
        }
        return compareEmptyRoom(first, second);
    }

    private int compareRoomIdentity(RoomSnapshot first, RoomSnapshot second) {
        int serverName = safe(first.getServerName(), "").compareToIgnoreCase(safe(second.getServerName(), ""));
        if (serverName != 0) {
            return serverName;
        }
        return safe(first.getArenaId(), "").compareToIgnoreCase(safe(second.getArenaId(), ""));
    }

    private boolean isLocalRoom(RoomSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        String localServerName = getLocalServerName();
        String roomServerName = safe(snapshot.getServerName(), "");
        if (localServerName.isEmpty()) {
            return roomServerName.isEmpty();
        }
        return roomServerName.equalsIgnoreCase(localServerName);
    }

    private boolean isVillageDefenseCommand(String root) {
        return "vd".equals(root) || "villagedefense".equals(root) || "villaged".equals(root);
    }

    private String parseMode(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "easy":
            case "simple":
            case "简单":
                return "EASY";
            case "hard":
            case "difficult":
            case "困难":
                return "HARD";
            case "endless":
            case "unlimited":
            case "infinite":
            case "无尽":
                return "ENDLESS";
            default:
                return null;
        }
    }

    private JoinTarget parseJoinTarget(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }

        String serverName = null;
        String arenaId = value;
        String[] separators = {":", "@", "|", "/"};
        for (String separator : separators) {
            int index = value.indexOf(separator);
            if (index > 0 && index < value.length() - 1) {
                serverName = value.substring(0, index).trim();
                arenaId = value.substring(index + 1).trim();
                break;
            }
        }
        if (arenaId.isEmpty()) {
            return null;
        }
        return new JoinTarget(serverName == null || serverName.isEmpty() ? null : serverName, arenaId);
    }

    private String roomKey(String serverName, String arenaId) {
        return safe(serverName, "").toLowerCase(Locale.ROOT) + ":" + safe(arenaId, "").toLowerCase(Locale.ROOT);
    }

    private String getLocalServerName() {
        String detected = safe(detectedServerName, "");
        if (!detected.isEmpty()) {
            return detected;
        }
        return configuredServerName.isEmpty() ? "" : configuredServerName;
    }

    private boolean hasResolvedServerName() {
        return !getLocalServerName().isEmpty();
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    public static final class RoomSnapshot {
        private final String serverName;
        private final String arenaId;
        private final String mapName;
        private final String mode;
        private final boolean inGame;
        private final int players;
        private final int maxPlayers;
        private final int serverPlayers;
        private final int wave;
        private final int finalWave;
        private final long updatedAt;

        public RoomSnapshot(String serverName, String arenaId, String mapName, String mode, boolean inGame, int players, int maxPlayers, int serverPlayers, int wave, int finalWave, long updatedAt) {
            this.serverName = serverName;
            this.arenaId = arenaId;
            this.mapName = mapName;
            this.mode = mode;
            this.inGame = inGame;
            this.players = players;
            this.maxPlayers = maxPlayers;
            this.serverPlayers = serverPlayers;
            this.wave = wave;
            this.finalWave = finalWave;
            this.updatedAt = updatedAt;
        }

        public String getServerName() {
            return serverName;
        }

        public String getArenaId() {
            return arenaId;
        }

        public String getMapName() {
            return mapName;
        }

        public String getMode() {
            return mode;
        }

        public boolean isInGame() {
            return inGame;
        }

        public int getPlayers() {
            return Math.max(0, players);
        }

        public int getMaxPlayers() {
            return maxPlayers;
        }

        public int getServerPlayers() {
            return Math.max(0, serverPlayers);
        }

        public int getWave() {
            return wave;
        }

        public int getFinalWave() {
            return finalWave;
        }

        public long getUpdatedAt() {
            return updatedAt;
        }

        public boolean isJoinable() {
            return maxPlayers <= 0 || players < maxPlayers;
        }
    }

    public static final class PendingJoin {
        private final String serverName;
        private final String arenaId;
        private final long expireAt;

        public PendingJoin(String serverName, String arenaId, long expireAt) {
            this.serverName = serverName;
            this.arenaId = arenaId;
            this.expireAt = expireAt;
        }

        public String getServerName() {
            return serverName;
        }

        public String getArenaId() {
            return arenaId;
        }

        public long getExpireAt() {
            return expireAt;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    private static final class JoinTarget {
        private final String serverName;
        private final String arenaId;

        private JoinTarget(String serverName, String arenaId) {
            this.serverName = serverName;
            this.arenaId = arenaId;
        }

        public String getServerName() {
            return serverName;
        }

        public String getArenaId() {
            return arenaId;
        }
    }
}
