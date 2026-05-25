package plugily.projects.villagedefense.network;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.network.NetworkRoomManager.PendingJoin;
import plugily.projects.villagedefense.network.NetworkRoomManager.RoomSnapshot;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public class RedisRoomDirectory {

    private final Main plugin;
    private final Gson gson = new Gson();
    private final String host;
    private final int port;
    private final String password;
    private final int database;
    private final int timeoutMillis;
    private final String roomsKey;
    private final String pendingJoinPrefix;
    private final int roomExpireSeconds;
    private final int pendingJoinExpireSeconds;
    private final boolean enabled;
    private final AtomicLong lastWarning = new AtomicLong();

    public RedisRoomDirectory(Main plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("Network.Enabled", false);
        this.host = plugin.getConfig().getString("Network.Redis.Host", "127.0.0.1");
        this.port = Math.max(1, plugin.getConfig().getInt("Network.Redis.Port", 6379));
        this.password = plugin.getConfig().getString("Network.Redis.Password", "");
        this.database = Math.max(0, plugin.getConfig().getInt("Network.Redis.Database", 0));
        this.timeoutMillis = Math.max(1000, plugin.getConfig().getInt("Network.Redis.Timeout-Millis", 2000));
        this.roomsKey = plugin.getConfig().getString("Network.Rooms.Key", "VillageDefense:rooms");
        this.pendingJoinPrefix = plugin.getConfig().getString("Network.Pending-Join.Key-Prefix", "VillageDefense:pending:");
        this.roomExpireSeconds = Math.max(10, plugin.getConfig().getInt("Network.Rooms.Expire-Seconds", 30));
        this.pendingJoinExpireSeconds = Math.max(10, plugin.getConfig().getInt("Network.Pending-Join.Expire-Seconds", 30));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void close() {
        // 每次操作独立建连，这里保留给后续需要时扩展连接池。
    }

    public void saveRoom(RoomSnapshot snapshot) {
        if (!enabled || snapshot == null) {
            return;
        }
        try (RedisConnection connection = open()) {
            connection.hset(roomsKey, roomField(snapshot), gson.toJson(snapshot));
            connection.expire(roomsKey, roomExpireSeconds * 2);
        } catch (Exception ex) {
            warn("无法写入跨服房间信息", ex);
        }
    }

    public List<RoomSnapshot> loadAllRooms() {
        if (!enabled) {
            return Collections.emptyList();
        }

        try (RedisConnection connection = open()) {
            Map<String, String> payload = connection.hgetAll(roomsKey);
            if (payload.isEmpty()) {
                return Collections.emptyList();
            }

            long now = System.currentTimeMillis();
            List<RoomSnapshot> rooms = new ArrayList<>();
            List<String> staleKeys = new ArrayList<>();
            for (Map.Entry<String, String> entry : payload.entrySet()) {
                RoomSnapshot snapshot = parseRoomSnapshot(entry.getValue());
                if (snapshot == null || isExpired(snapshot, now)) {
                    staleKeys.add(entry.getKey());
                    continue;
                }
                rooms.add(snapshot);
            }

            if (!staleKeys.isEmpty()) {
                connection.hdel(roomsKey, staleKeys.toArray(new String[0]));
            }
            return rooms;
        } catch (Exception ex) {
            warn("无法读取跨服房间列表", ex);
            return Collections.emptyList();
        }
    }

    public void savePendingJoin(UUID playerId, String serverName, String arenaId, long expireAt) {
        if (!enabled || playerId == null || serverName == null || arenaId == null) {
            return;
        }

        PendingJoinRecord record = new PendingJoinRecord(serverName, arenaId, expireAt);
        try (RedisConnection connection = open()) {
            connection.setex(pendingJoinKey(playerId), pendingJoinExpireSeconds, gson.toJson(record));
        } catch (Exception ex) {
            warn("无法写入跨服待加入信息", ex);
        }
    }

    public PendingJoin getPendingJoin(UUID playerId) {
        if (!enabled || playerId == null) {
            return null;
        }

        try (RedisConnection connection = open()) {
            String json = connection.get(pendingJoinKey(playerId));
            if (json == null || json.isEmpty()) {
                return null;
            }

            PendingJoinRecord record = gson.fromJson(json, PendingJoinRecord.class);
            if (record == null || record.isExpired()) {
                connection.del(pendingJoinKey(playerId));
                return null;
            }
            return new PendingJoin(record.serverName, record.arenaId, record.expireAt);
        } catch (Exception ex) {
            warn("无法读取跨服待加入信息", ex);
            return null;
        }
    }

    public void clearPendingJoin(UUID playerId) {
        if (!enabled || playerId == null) {
            return;
        }

        try (RedisConnection connection = open()) {
            connection.del(pendingJoinKey(playerId));
        } catch (Exception ex) {
            warn("无法清理跨服待加入信息", ex);
        }
    }

    private RoomSnapshot parseRoomSnapshot(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return gson.fromJson(json, RoomSnapshot.class);
        } catch (JsonSyntaxException ex) {
            warn("跨服房间数据格式错误", ex);
            return null;
        }
    }

    private boolean isExpired(RoomSnapshot snapshot, long now) {
        return snapshot == null
                || snapshot.getServerName() == null
                || snapshot.getArenaId() == null
                || snapshot.getUpdatedAt() <= 0L
                || now - snapshot.getUpdatedAt() > roomExpireSeconds * 1000L;
    }

    private String roomField(RoomSnapshot snapshot) {
        return safe(snapshot.getServerName()).toLowerCase(Locale.ROOT) + ":" + safe(snapshot.getArenaId()).toLowerCase(Locale.ROOT);
    }

    private String pendingJoinKey(UUID playerId) {
        return pendingJoinPrefix + playerId;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private RedisConnection open() throws IOException {
        RedisConnection connection = new RedisConnection(host, port, timeoutMillis);
        if (password != null && !password.trim().isEmpty()) {
            connection.expectOk(connection.execute("AUTH", password));
        }
        if (database > 0) {
            connection.expectOk(connection.execute("SELECT", String.valueOf(database)));
        }
        return connection;
    }

    private void warn(String message, Exception ex) {
        long now = System.currentTimeMillis();
        long last = lastWarning.get();
        if (now - last < 60_000L && lastWarning.get() != 0L) {
            return;
        }
        if (!lastWarning.compareAndSet(last, now)) {
            return;
        }
        plugin.getLogger().log(Level.WARNING, message, ex);
    }

    private static final class PendingJoinRecord {
        private String serverName;
        private String arenaId;
        private long expireAt;

        private PendingJoinRecord() {
        }

        private PendingJoinRecord(String serverName, String arenaId, long expireAt) {
            this.serverName = serverName;
            this.arenaId = arenaId;
            this.expireAt = expireAt;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    private static final class RedisConnection implements Closeable {

        private final Socket socket;
        private final BufferedInputStream in;
        private final BufferedOutputStream out;

        private RedisConnection(String host, int port, int timeoutMillis) throws IOException {
            this.socket = new Socket();
            this.socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            this.socket.setSoTimeout(timeoutMillis);
            this.in = new BufferedInputStream(socket.getInputStream());
            this.out = new BufferedOutputStream(socket.getOutputStream());
        }

        private Object execute(String... parts) throws IOException {
            writeCommand(parts);
            return readResponse();
        }

        private void expectOk(Object response) throws IOException {
            if (!"OK".equals(response)) {
                throw new IOException("Redis returned unexpected response: " + response);
            }
        }

        private void hset(String key, String field, String value) throws IOException {
            execute("HSET", key, field, value);
        }

        private Map<String, String> hgetAll(String key) throws IOException {
            Object response = execute("HGETALL", key);
            Map<String, String> result = new LinkedHashMap<>();
            if (!(response instanceof List<?> list)) {
                return result;
            }
            for (int i = 0; i + 1 < list.size(); i += 2) {
                Object field = list.get(i);
                Object value = list.get(i + 1);
                if (field != null && value != null) {
                    result.put(field.toString(), value.toString());
                }
            }
            return result;
        }

        private void setex(String key, int seconds, String value) throws IOException {
            execute("SETEX", key, String.valueOf(Math.max(1, seconds)), value);
        }

        private String get(String key) throws IOException {
            Object response = execute("GET", key);
            return response == null ? null : response.toString();
        }

        private void del(String key) throws IOException {
            execute("DEL", key);
        }

        private void hdel(String key, String... fields) throws IOException {
            if (fields == null || fields.length == 0) {
                return;
            }
            String[] parts = new String[fields.length + 2];
            parts[0] = "HDEL";
            parts[1] = key;
            System.arraycopy(fields, 0, parts, 2, fields.length);
            execute(parts);
        }

        private void expire(String key, int seconds) throws IOException {
            execute("EXPIRE", key, String.valueOf(Math.max(1, seconds)));
        }

        private void writeCommand(String... parts) throws IOException {
            out.write(('*'));
            out.write(String.valueOf(parts.length).getBytes(StandardCharsets.UTF_8));
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            for (String part : parts) {
                byte[] bytes = part == null ? new byte[0] : part.getBytes(StandardCharsets.UTF_8);
                out.write('$');
                out.write(String.valueOf(bytes.length).getBytes(StandardCharsets.UTF_8));
                out.write("\r\n".getBytes(StandardCharsets.UTF_8));
                out.write(bytes);
                out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            out.flush();
        }

        private Object readResponse() throws IOException {
            int prefix = in.read();
            if (prefix == -1) {
                throw new IOException("Redis connection closed");
            }

            switch (prefix) {
                case '+':
                    return readLine();
                case '-':
                    throw new IOException(readLine());
                case ':':
                    return Long.parseLong(readLine());
                case '$':
                    return readBulkString();
                case '*':
                    return readArray();
                default:
                    throw new IOException("Unknown Redis response prefix: " + (char) prefix);
            }
        }

        private String readBulkString() throws IOException {
            int length = Integer.parseInt(readLine());
            if (length == -1) {
                return null;
            }

            byte[] buffer = readBytes(length);
            consumeCRLF();
            return new String(buffer, StandardCharsets.UTF_8);
        }

        private List<Object> readArray() throws IOException {
            int length = Integer.parseInt(readLine());
            if (length == -1) {
                return null;
            }

            List<Object> values = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                values.add(readResponse());
            }
            return values;
        }

        private byte[] readBytes(int length) throws IOException {
            byte[] buffer = new byte[length];
            int offset = 0;
            while (offset < length) {
                int read = in.read(buffer, offset, length - offset);
                if (read < 0) {
                    throw new IOException("Redis stream ended unexpectedly");
                }
                offset += read;
            }
            return buffer;
        }

        private void consumeCRLF() throws IOException {
            int first = in.read();
            int second = in.read();
            if (first != '\r' || second != '\n') {
                throw new IOException("Invalid Redis line ending");
            }
        }

        private String readLine() throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int previous = -1;
            while (true) {
                int current = in.read();
                if (current == -1) {
                    throw new IOException("Redis stream ended unexpectedly");
                }
                if (previous == '\r' && current == '\n') {
                    byte[] bytes = buffer.toByteArray();
                    return new String(bytes, 0, Math.max(0, bytes.length - 1), StandardCharsets.UTF_8);
                }
                buffer.write(current);
                previous = current;
            }
        }

        @Override
        public void close() throws IOException {
            try {
                out.close();
            } finally {
                try {
                    in.close();
                } finally {
                    socket.close();
                }
            }
        }
    }
}
