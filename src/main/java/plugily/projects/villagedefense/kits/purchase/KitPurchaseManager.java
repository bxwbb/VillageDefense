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

package plugily.projects.villagedefense.kits.purchase;

import com.xigua.baseAPI.BaseAPI;
import com.xigua.cumulus.form.SimpleForm;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import plugily.projects.minigamesbox.api.kit.IKit;
import plugily.projects.minigamesbox.classic.utils.configuration.ConfigUtils;
import plugily.projects.villagedefense.Main;
import plugily.projects.villagedefense.utils.BedrockSupport;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Handles persistent kit purchases while keeping the original kit selector compatible.
 */
public class KitPurchaseManager implements Listener {

    private static final String CONFIG_NAME = "kit_shop";
    private static final String PURCHASE_FILE = "kit_purchases.yml";
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.##");
    private static final Set<String> COMMAND_ALIASES = new HashSet<>();

    static {
        COMMAND_ALIASES.add("kitshop");
        COMMAND_ALIASES.add("kitbuy");
        COMMAND_ALIASES.add("buykit");
        COMMAND_ALIASES.add("shopkit");
    }

    private final Main plugin;
    private final File purchasesFile;
    private final Map<UUID, PermissionAttachment> permissionAttachments = new HashMap<>();
    private final VaultEconomyBridge economyBridge = new VaultEconomyBridge();
    private FileConfiguration config;
    private FileConfiguration purchases;

    public KitPurchaseManager(Main plugin) {
        this.plugin = plugin;
        this.config = ConfigUtils.getConfig(plugin, CONFIG_NAME);
        this.purchasesFile = new File(plugin.getDataFolder(), PURCHASE_FILE);
        this.purchases = YamlConfiguration.loadConfiguration(purchasesFile);

        ensureKitPermissions();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyPurchasedPermissions(player);
        }
    }

    public void reload() {
        config = ConfigUtils.getConfig(plugin, CONFIG_NAME);
        purchases = YamlConfiguration.loadConfiguration(purchasesFile);
        ensureKitPermissions();
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyPurchasedPermissions(player);
        }
        if (plugin.getBedrockKitSelectionManager() != null) {
            plugin.getBedrockKitSelectionManager().reload();
        }
    }

    public void shutdown() {
        savePurchases();
        for (Map.Entry<UUID, PermissionAttachment> entry : new ArrayList<>(permissionAttachments.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                try {
                    player.removeAttachment(entry.getValue());
                } catch (IllegalArgumentException ignored) {
                    // Attachment was already removed by Bukkit.
                }
            }
        }
        permissionAttachments.clear();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyPurchasedPermissions(event.getPlayer()), 5L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        PermissionAttachment attachment = permissionAttachments.remove(event.getPlayer().getUniqueId());
        if (attachment != null) {
            try {
                event.getPlayer().removeAttachment(attachment);
            } catch (IllegalArgumentException ignored) {
                // Attachment was already removed by Bukkit.
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerAdminReload(PlayerCommandPreprocessEvent event) {
        if (isAdminReloadCommand(event.getMessage())) {
            Bukkit.getScheduler().runTask(plugin, this::reload);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsoleAdminReload(ServerCommandEvent event) {
        if (isAdminReloadCommand(event.getCommand())) {
            Bukkit.getScheduler().runTask(plugin, this::reload);
        }
    }

    @EventHandler
    public void onKitShopCommand(PlayerCommandPreprocessEvent event) {
        String[] parts = splitCommand(event.getMessage());
        if (parts.length < 2 || !isVillageDefenseCommand(parts[0]) || !COMMAND_ALIASES.contains(parts[1].toLowerCase(Locale.ROOT))) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (parts.length >= 3) {
            buyKit(player, parts[2], true);
            return;
        }
        open(player);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof KitShopHolder holder)) {
            return;
        }

        event.setCancelled(true);
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getInventory().getSize()) {
            return;
        }

        String kitKey = holder.getKitKey(rawSlot);
        if (kitKey == null) {
            return;
        }
        buyKit(player, kitKey, true);
    }

    public void open(Player player) {
        if (BedrockSupport.isBedrockPlayer(plugin, player)) {
            openBedrock(player);
            return;
        }
        openJava(player);
    }

    public boolean isPurchased(Player player, String kitKey) {
        return player != null && isPurchased(player.getUniqueId(), kitKey);
    }

    private void openJava(Player player) {
        List<KitShopEntry> entries = getEntries();
        if (entries.isEmpty()) {
            player.sendMessage(color(getMessage("Settings.Kit-Unavailable-Message", "&c当前没有可购买职业。")));
            return;
        }

        int rows = Math.max(1, Math.min(6, config.getInt("Settings.Rows", 6)));
        KitShopHolder holder = new KitShopHolder();
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, color(config.getString("Settings.Title", "&2职业购买")));
        holder.setInventory(inventory);

        int nextSlot = 0;
        Set<Integer> usedSlots = new HashSet<>();
        for (KitShopEntry entry : entries) {
            IKit kit = getKit(entry.key);
            if (kit == null) {
                continue;
            }

            int slot = entry.slot;
            if (slot < 0 || slot >= inventory.getSize() || usedSlots.contains(slot)) {
                slot = findNextEmptySlot(inventory.getSize(), usedSlots, nextSlot);
            }
            if (slot < 0) {
                break;
            }

            usedSlots.add(slot);
            nextSlot = slot + 1;
            inventory.setItem(slot, createKitItem(player, kit, entry));
            holder.setKitKey(slot, entry.key);
        }
        player.openInventory(inventory);
    }

    private void openBedrock(Player player) {
        Plugin basePlugin = Bukkit.getPluginManager().getPlugin("BaseAPI");
        if (!(basePlugin instanceof BaseAPI)) {
            openJava(player);
            return;
        }

        List<KitShopEntry> entries = getEntries();
        if (entries.isEmpty()) {
            player.sendMessage(color(getMessage("Settings.Kit-Unavailable-Message", "&c当前没有可购买职业。")));
            return;
        }

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(color(config.getString("Settings.Title", "&2职业购买")))
                .content(color(String.join("\n", config.getStringList("Settings.Bedrock-Content"))));

        for (KitShopEntry entry : entries) {
            IKit kit = getKit(entry.key);
            if (kit == null) {
                continue;
            }
            String button = color(getDisplayName(kit, entry) + " &7" + formatPriceRaw(entry.price) + "游戏币\n"
                    + getBedrockOwnershipStatus(player, kit, entry));
            builder.button(button, response -> Bukkit.getScheduler().runTask(plugin, () -> openBedrockDetail(player, entry.key)));
        }
        ((BaseAPI) basePlugin).sendForm(player.getUniqueId(), builder.build());
    }

    private void openBedrockDetail(Player player, String rawKitKey) {
        Plugin basePlugin = Bukkit.getPluginManager().getPlugin("BaseAPI");
        if (!(basePlugin instanceof BaseAPI)) {
            openJava(player);
            return;
        }

        String kitKey = normalizeKitKey(rawKitKey);
        KitShopEntry entry = getEntry(kitKey);
        IKit kit = getKit(kitKey);
        if (entry == null || kit == null || !entry.enabled) {
            player.sendMessage(color(getMessage("Settings.Kit-Unavailable-Message", "&c该职业当前不可购买。")));
            return;
        }

        boolean owned = isOwned(player, kit, entry.key);
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(color(getDisplayName(kit, entry)))
                .content(color(buildBedrockDetailContent(player, kit, entry)));

        if (!owned) {
            builder.button(color(config.getString("Settings.Bedrock-Buy-Button", "&a点击购买")),
                    response -> Bukkit.getScheduler().runTask(plugin, () ->
                            buyKit(player, entry.key, false, () -> openBedrockDetail(player, entry.key))));
        }
        builder.button(color(config.getString("Settings.Bedrock-Back-Button", "&c返回")),
                response -> Bukkit.getScheduler().runTask(plugin, () -> openBedrock(player)));
        ((BaseAPI) basePlugin).sendForm(player.getUniqueId(), builder.build());
    }

    private String buildBedrockDetailContent(Player player, IKit kit, KitShopEntry entry) {
        List<String> lines = new ArrayList<>();
        lines.add(getDisplayName(kit, entry));
        lines.add("&7价格: &b" + formatPriceRaw(entry.price) + " 游戏币");
        lines.add("&7状态: " + getBedrockOwnershipStatus(player, kit, entry));
        lines.add("");
        if (kit.getDescription().isEmpty()) {
            for (String line : buildPlainLore(player, kit, entry, getStatus(player, kit, entry))) {
                if (!ChatColor.stripColor(color(line)).contains("点击购买")) {
                    lines.add(line);
                }
            }
        } else {
            lines.addAll(kit.getDescription());
        }
        return String.join("\n", lines);
    }

    private boolean buyKit(Player player, String rawKitKey, boolean refreshJavaMenu) {
        return buyKit(player, rawKitKey, refreshJavaMenu, null);
    }

    private boolean buyKit(Player player, String rawKitKey, boolean refreshJavaMenu, Runnable bedrockSuccessCallback) {
        String kitKey = normalizeKitKey(rawKitKey);
        KitShopEntry entry = getEntry(kitKey);
        IKit kit = getKit(kitKey);
        if (entry == null || kit == null || !entry.enabled) {
            player.sendMessage(color(getMessage("Settings.Kit-Unavailable-Message", "&c该职业当前不可购买。")));
            return false;
        }

        if (isOwned(player, kit, kitKey)) {
            player.sendMessage(color(replacePlaceholders(getMessage("Settings.Already-Owned-Message", "&e你已经拥有职业 &f%kit_name%&e。"), player, kit, entry, getStatus(player, kit, entry))));
            return false;
        }

        if (entry.price > 0 && !economyBridge.isReady()) {
            player.sendMessage(color(getMessage("Settings.Vault-Missing-Message", "&c未找到 Vault 或经济插件，无法购买职业。")));
            return false;
        }

        if (entry.price > 0 && !economyBridge.has(player, entry.price)) {
            player.sendMessage(color(replacePlaceholders(getMessage("Settings.Not-Enough-Money-Message",
                    "&c金币不足，购买 &f%kit_name% &c需要 &6%price%&c，当前余额 &6%balance%&c。"),
                    player, kit, entry, getStatus(player, kit, entry))));
            return false;
        }

        if (entry.price > 0 && !economyBridge.withdraw(player, entry.price)) {
            player.sendMessage(color(getMessage("Settings.Purchase-Failed-Message", "&c购买失败，请稍后再试。")));
            return false;
        }

        markPurchased(player.getUniqueId(), kitKey);
        applyPurchasedPermissions(player);
        player.sendMessage(color(replacePlaceholders(getMessage("Settings.Bought-Message", "&a你成功购买了职业 &f%kit_name%&a！"), player, kit, entry, getStatus(player, kit, entry))));
        playClick(player, 1.4F);

        if (refreshJavaMenu && player.getOpenInventory() != null && player.getOpenInventory().getTopInventory().getHolder() instanceof KitShopHolder) {
            Bukkit.getScheduler().runTask(plugin, () -> openJava(player));
        } else if (bedrockSuccessCallback != null) {
            Bukkit.getScheduler().runTaskLater(plugin, bedrockSuccessCallback, 2L);
        } else if (!refreshJavaMenu && BedrockSupport.isBedrockPlayer(plugin, player)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> openBedrock(player), 2L);
        }
        return true;
    }

    private ItemStack createKitItem(Player player, IKit kit, KitShopEntry entry) {
        ItemStack item = getBaseItem(kit, entry);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String status = getStatus(player, kit, entry);
            meta.setDisplayName(color(getDisplayName(kit, entry)));
            meta.setLore(buildLore(player, kit, entry, status));
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack getBaseItem(IKit kit, KitShopEntry entry) {
        Material configured = Material.matchMaterial(entry.material == null ? "" : entry.material);
        if (configured != null && configured != Material.AIR) {
            return new ItemStack(configured);
        }

        ItemStack kitItem = kit.getItemStack();
        if (kitItem != null && kitItem.getType() != Material.AIR) {
            return kitItem.clone();
        }
        return new ItemStack(Material.CHEST);
    }

    private List<String> buildLore(Player player, IKit kit, KitShopEntry entry, String status) {
        List<String> lore = new ArrayList<>();
        for (String line : buildPlainLore(player, kit, entry, status)) {
            lore.add(color(line));
        }
        return lore;
    }

    private List<String> buildPlainLore(Player player, IKit kit, KitShopEntry entry, String status) {
        List<String> lore = new ArrayList<>();
        List<String> template = entry.lore.isEmpty() ? getDefaultLore() : entry.lore;
        for (String line : template) {
            if (line != null && line.contains("%description%")) {
                if (kit.getDescription().isEmpty()) {
                    lore.add(replacePlaceholders(line.replace("%description%", ""), player, kit, entry, status));
                    continue;
                }
                for (String descriptionLine : kit.getDescription()) {
                    lore.add(replacePlaceholders(descriptionLine, player, kit, entry, status));
                }
                continue;
            }
            lore.add(replacePlaceholders(line, player, kit, entry, status));
        }
        return lore;
    }

    private List<String> getDefaultLore() {
        List<String> lore = new ArrayList<>();
        lore.add("&7价格: &6%price%");
        lore.add("&7状态: %status%");
        lore.add("");
        lore.add("%description%");
        lore.add("");
        lore.add("&e点击购买");
        return lore;
    }

    private String getStatus(Player player, IKit kit, KitShopEntry entry) {
        if (isOwned(player, kit, entry.key)) {
            return config.getString("Settings.Status-Owned", "&a已拥有");
        }
        if (entry.price > 0 && economyBridge.isReady() && !economyBridge.has(player, entry.price)) {
            return config.getString("Settings.Status-No-Money", "&c金币不足");
        }
        return config.getString("Settings.Status-Available", "&e可购买");
    }

    private String getBedrockOwnershipStatus(Player player, IKit kit, KitShopEntry entry) {
        if (isOwned(player, kit, entry.key)) {
            return config.getString("Settings.Bedrock-Status-Owned", "&a已拥有");
        }
        return config.getString("Settings.Bedrock-Status-Unowned", "&7未拥有");
    }

    private boolean isOwned(Player player, IKit kit, String kitKey) {
        if (isPurchased(player.getUniqueId(), kitKey) || kit.isUnlockedOnDefault()) {
            return true;
        }
        try {
            return kit.isUnlockedByPlayer(player);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void applyPurchasedPermissions(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        PermissionAttachment oldAttachment = permissionAttachments.remove(player.getUniqueId());
        if (oldAttachment != null) {
            try {
                player.removeAttachment(oldAttachment);
            } catch (IllegalArgumentException ignored) {
                // Attachment was already removed by Bukkit.
            }
        }

        PermissionAttachment attachment = player.addAttachment(plugin);
        permissionAttachments.put(player.getUniqueId(), attachment);
        for (String kitKey : getPurchasedKits(player.getUniqueId())) {
            attachment.setPermission(getKitPermission(kitKey), true);
        }
        player.recalculatePermissions();
    }

    private void ensureKitPermissions() {
        for (IKit kit : plugin.getKitRegistry().getKits()) {
            if (kit == null) {
                continue;
            }
            try {
                Method getPermission = kit.getClass().getMethod("getKitPermission");
                String permission = (String) getPermission.invoke(kit);
                if (permission != null && !permission.trim().isEmpty()) {
                    continue;
                }
                Method setPermission = kit.getClass().getMethod("setKitPermission", String.class);
                setPermission.invoke(kit, getKitPermission(kit.getKey()));
            } catch (ReflectiveOperationException ignored) {
                // MiniGamesBox kit implementations without this method still use the hard-coded permission below.
            }
        }
    }

    private void markPurchased(UUID uuid, String kitKey) {
        Set<String> purchasedKits = new LinkedHashSet<>(getPurchasedKits(uuid));
        purchasedKits.add(normalizeKitKey(kitKey));
        purchases.set(getPlayerPath(uuid) + ".kits", new ArrayList<>(purchasedKits));
        savePurchases();
    }

    private boolean isPurchased(UUID uuid, String kitKey) {
        return getPurchasedKits(uuid).contains(normalizeKitKey(kitKey));
    }

    private List<String> getPurchasedKits(UUID uuid) {
        if (uuid == null) {
            return Collections.emptyList();
        }
        List<String> kits = new ArrayList<>();
        for (String key : purchases.getStringList(getPlayerPath(uuid) + ".kits")) {
            kits.add(normalizeKitKey(key));
        }
        return kits;
    }

    private void savePurchases() {
        try {
            purchases.save(purchasesFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Cannot save kit purchases", ex);
        }
    }

    private List<KitShopEntry> getEntries() {
        ConfigurationSection section = config.getConfigurationSection("Kits");
        if (section == null) {
            return Collections.emptyList();
        }

        List<KitShopEntry> entries = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            KitShopEntry entry = getEntry(key);
            if (entry != null && entry.enabled && getKit(entry.key) != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private KitShopEntry getEntry(String rawKey) {
        String key = normalizeKitKey(rawKey);
        ConfigurationSection section = config.getConfigurationSection("Kits." + key);
        if (section == null) {
            return null;
        }
        return new KitShopEntry(
                key,
                section.getBoolean("Enabled", true),
                Math.max(0.0D, section.getDouble("Price", 0.0D)),
                section.getInt("Slot", -1),
                section.getString("Display-Name", ""),
                section.getString("Material", ""),
                section.getStringList("Lore")
        );
    }

    private IKit getKit(String key) {
        return plugin.getKitRegistry().getKitByKey(normalizeKitKey(key));
    }

    private String getDisplayName(IKit kit, KitShopEntry entry) {
        if (entry.displayName != null && !entry.displayName.trim().isEmpty()) {
            return entry.displayName;
        }
        return kit.getName();
    }

    private String replacePlaceholders(String text, Player player, IKit kit, KitShopEntry entry, String status) {
        String value = text == null ? "" : text;
        value = value.replace("%kit_key%", entry.key);
        value = value.replace("%kit_name%", getDisplayName(kit, entry));
        value = value.replace("%price%", formatMoney(entry.price));
        value = value.replace("%price_raw%", MONEY_FORMAT.format(entry.price));
        value = value.replace("%balance%", economyBridge.isReady() ? economyBridge.format(economyBridge.getBalance(player)) : "0");
        value = value.replace("%status%", status == null ? "" : status);
        return value;
    }

    private int findNextEmptySlot(int size, Set<Integer> usedSlots, int start) {
        for (int slot = Math.max(0, start); slot < size; slot++) {
            if (!usedSlots.contains(slot)) {
                return slot;
            }
        }
        for (int slot = 0; slot < Math.max(0, start); slot++) {
            if (!usedSlots.contains(slot)) {
                return slot;
            }
        }
        return -1;
    }

    private String getKitPermission(String kitKey) {
        return plugin.getPluginNamePrefixLong() + ".kit." + normalizeKitKey(kitKey);
    }

    private String getPlayerPath(UUID uuid) {
        return "players." + uuid;
    }

    private String normalizeKitKey(String kitKey) {
        return kitKey == null ? "" : kitKey.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isVillageDefenseCommand(String command) {
        String normalized = normalizeCommandName(command);
        return normalized.equals("vd") || normalized.equals("villagedefense") || normalized.equals("villaged");
    }

    private boolean isVillageDefenseAdminCommand(String command) {
        String normalized = normalizeCommandName(command);
        return normalized.equals("vda") || normalized.equals("villagedefenseadmin") || normalized.equals("villageadmin");
    }

    private boolean isAdminReloadCommand(String rawCommand) {
        String[] parts = splitCommand(rawCommand);
        return parts.length >= 2 && isVillageDefenseAdminCommand(parts[0]) && parts[1].equalsIgnoreCase("reload");
    }

    private String[] splitCommand(String rawCommand) {
        if (rawCommand == null) {
            return new String[0];
        }
        String normalized = rawCommand.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return new String[0];
        }
        return normalized.split("\\s+");
    }

    private String normalizeCommandName(String command) {
        String normalized = command == null ? "" : command.toLowerCase(Locale.ROOT);
        int namespaceIndex = normalized.indexOf(':');
        if (namespaceIndex >= 0 && namespaceIndex + 1 < normalized.length()) {
            normalized = normalized.substring(namespaceIndex + 1);
        }
        return normalized;
    }

    private String getMessage(String path, String fallback) {
        return config.getString(path, fallback);
    }

    private String formatMoney(double amount) {
        return economyBridge.isReady() ? economyBridge.format(amount) : MONEY_FORMAT.format(amount);
    }

    private String formatPriceRaw(double amount) {
        return MONEY_FORMAT.format(amount);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private void playClick(Player player, float pitch) {
        try {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, pitch);
        } catch (IllegalArgumentException ignored) {
            // Sound name can differ on very old server versions.
        }
    }

    private static final class KitShopEntry {
        private final String key;
        private final boolean enabled;
        private final double price;
        private final int slot;
        private final String displayName;
        private final String material;
        private final List<String> lore;

        private KitShopEntry(String key, boolean enabled, double price, int slot, String displayName, String material, List<String> lore) {
            this.key = key;
            this.enabled = enabled;
            this.price = price;
            this.slot = slot;
            this.displayName = displayName;
            this.material = material;
            this.lore = lore == null ? Collections.emptyList() : new ArrayList<>(lore);
        }
    }

    private static final class KitShopHolder implements InventoryHolder {
        private final Map<Integer, String> kitSlots = new LinkedHashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private void setKitKey(int slot, String kitKey) {
            kitSlots.put(slot, kitKey);
        }

        private String getKitKey(int slot) {
            return kitSlots.get(slot);
        }
    }

    private final class VaultEconomyBridge {
        private Object economy;
        private Method hasOfflineMethod;
        private Method hasNameMethod;
        private Method withdrawOfflineMethod;
        private Method withdrawNameMethod;
        private Method balanceOfflineMethod;
        private Method balanceNameMethod;
        private Method formatMethod;

        private boolean isReady() {
            return economy != null || setup();
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private boolean setup() {
            Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
            if (vault == null || !vault.isEnabled()) {
                return false;
            }
            try {
                Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
                RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration((Class) economyClass);
                if (provider == null) {
                    return false;
                }
                economy = provider.getProvider();
                hasOfflineMethod = findMethod(economyClass, "has", OfflinePlayer.class, double.class);
                hasNameMethod = findMethod(economyClass, "has", String.class, double.class);
                withdrawOfflineMethod = findMethod(economyClass, "withdrawPlayer", OfflinePlayer.class, double.class);
                withdrawNameMethod = findMethod(economyClass, "withdrawPlayer", String.class, double.class);
                balanceOfflineMethod = findMethod(economyClass, "getBalance", OfflinePlayer.class);
                balanceNameMethod = findMethod(economyClass, "getBalance", String.class);
                formatMethod = findMethod(economyClass, "format", double.class);
                return true;
            } catch (ClassNotFoundException | LinkageError ex) {
                economy = null;
                return false;
            }
        }

        private boolean has(Player player, double amount) {
            if (amount <= 0) {
                return true;
            }
            Object result = invokePlayerMethod(player, hasOfflineMethod, hasNameMethod, amount);
            return Boolean.TRUE.equals(result);
        }

        private boolean withdraw(Player player, double amount) {
            if (amount <= 0) {
                return true;
            }
            Object result = invokePlayerMethod(player, withdrawOfflineMethod, withdrawNameMethod, amount);
            if (result == null) {
                return false;
            }
            try {
                Method success = result.getClass().getMethod("transactionSuccess");
                return Boolean.TRUE.equals(success.invoke(result));
            } catch (ReflectiveOperationException ex) {
                return false;
            }
        }

        private double getBalance(Player player) {
            Object result = invokePlayerMethod(player, balanceOfflineMethod, balanceNameMethod);
            if (result instanceof Number number) {
                return number.doubleValue();
            }
            return 0.0D;
        }

        private String format(double amount) {
            if (!isReady() || formatMethod == null) {
                return MONEY_FORMAT.format(amount);
            }
            try {
                Object result = formatMethod.invoke(economy, amount);
                return result == null ? MONEY_FORMAT.format(amount) : String.valueOf(result);
            } catch (ReflectiveOperationException ex) {
                return MONEY_FORMAT.format(amount);
            }
        }

        private Method findMethod(Class<?> owner, String name, Class<?>... parameters) {
            try {
                return owner.getMethod(name, parameters);
            } catch (NoSuchMethodException ex) {
                return null;
            }
        }

        private Object invokePlayerMethod(Player player, Method offlineMethod, Method nameMethod, Object... args) {
            if (!isReady()) {
                return null;
            }
            try {
                if (offlineMethod != null) {
                    Object[] fullArgs = new Object[args.length + 1];
                    fullArgs[0] = player;
                    System.arraycopy(args, 0, fullArgs, 1, args.length);
                    return offlineMethod.invoke(economy, fullArgs);
                }
                if (nameMethod != null) {
                    Object[] fullArgs = new Object[args.length + 1];
                    fullArgs[0] = player.getName();
                    System.arraycopy(args, 0, fullArgs, 1, args.length);
                    return nameMethod.invoke(economy, fullArgs);
                }
            } catch (IllegalAccessException | InvocationTargetException ex) {
                return null;
            }
            return null;
        }
    }
}
