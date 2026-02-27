package com.lukeonuke.pvptoggle.service;

import com.lukeonuke.pvptoggle.PvpToggle;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PvpService {

    private static final NamespacedKey isPvpEnabledKey =
            new NamespacedKey(PvpToggle.getPlugin(), "isPvpEnabled");

    private static final NamespacedKey pvpToggledTimestampKey =
            new NamespacedKey(PvpToggle.getPlugin(), "pvpToggledTimestamp");

    private static final ConcurrentHashMap<UUID, BukkitTask> activeTasks = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> expirationTimes = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> combatTagged = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, BukkitTask> combatTasks = new ConcurrentHashMap<>();

    private static final int COMBAT_DURATION_SECONDS = 15;

    public static boolean isPvpEnabled(@NotNull Player player) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        return Boolean.TRUE.equals(container.get(isPvpEnabledKey, PersistentDataType.BOOLEAN));
    }

    public static void setPvpEnabled(Player player, boolean enabled) {
        final ConfigurationService config = ConfigurationService.getInstance();
        UUID uuid = player.getUniqueId();

        PersistentDataContainer container = player.getPersistentDataContainer();
        container.set(isPvpEnabledKey, PersistentDataType.BOOLEAN, enabled);
        container.set(pvpToggledTimestampKey, PersistentDataType.LONG, Instant.now().toEpochMilli());

        if (config.getLimitedTime() < 0) return;

        if (enabled) {
            BukkitTask old = activeTasks.remove(uuid);
            if (old != null) old.cancel();
            return;
        }

        BukkitTask old = activeTasks.remove(uuid);
        if (old != null) old.cancel();

        BukkitTask task = Bukkit.getScheduler().runTaskLater(
                PvpToggle.getPlugin(),
                () -> {
                    if (!player.isOnline()) return;

                    setPvpEnabled(player, true);

                    if (!config.getLimitedMessage().isEmpty()) {
                        player.sendMessage(
                                ChatFormatterService.addPrefix(
                                        config.getLimitedMessage()
                                                .replace("%s", ChatFormatterService.booleanHumanReadable(false))
                                )
                        );
                    }
                },
                config.getLimitedTime() * 20L
        );

        activeTasks.put(uuid, task);
    }

    public static void tagPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        long expireTime = System.currentTimeMillis() + (COMBAT_DURATION_SECONDS * 1000);

        combatTagged.put(uuid, expireTime);

        if (combatTasks.containsKey(uuid)) return;

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(
                PvpToggle.getPlugin(),
                () -> {
                    if (!player.isOnline() || !isInCombat(player)) {
                        removeCombatTag(player);
                        return;
                    }

                    player.spigot().sendMessage(
                            ChatMessageType.ACTION_BAR,
                            new TextComponent("§cDu är i combat!")
                    );
                },
                1L,
                20L
        );

        combatTasks.put(uuid, task);
    }

    public static boolean isInCombat(Player player) {
        Long expireTime = combatTagged.get(player.getUniqueId());
        if (expireTime == null) return false;

        if (System.currentTimeMillis() > expireTime) {
            combatTagged.remove(player.getUniqueId());
            return false;
        }

        return true;
    }

    public static void removeCombatTag(Player player) {
        UUID uuid = player.getUniqueId();

        combatTagged.remove(uuid);

        BukkitTask task = combatTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    public static void handlePlayerLeave(Player player) {
        final ConfigurationService config = ConfigurationService.getInstance();
        UUID uuid = player.getUniqueId();

        if (isInCombat(player)) {
            player.setHealth(0.0);
            removeCombatTag(player);
        }

        BukkitTask task = activeTasks.remove(uuid);
        if (task != null) task.cancel();

        expirationTimes.put(
                uuid.toString(),
                System.currentTimeMillis() + config.getLimitedTime() * 1000L
        );
    }

    public static void handlePlayerJoin(Player player) {
        final ConfigurationService config = ConfigurationService.getInstance();
        String uuid = player.getUniqueId().toString();

        if (config.getLimitedTime() < 0) return;

        Long expiration = expirationTimes.get(uuid);

        if (expiration == null) {
            resetPvpStatus(player);
            return;
        }

        if (System.currentTimeMillis() > expiration) {
            setPvpEnabled(player, true);
        } else {
            setPvpEnabled(player, false);
        }
    }

    private static void resetPvpStatus(Player player) {
        final ConfigurationService config = ConfigurationService.getInstance();
        player.getPersistentDataContainer().set(
                isPvpEnabledKey,
                PersistentDataType.BOOLEAN,
                config.getDefaultPvp()
        );
    }

    public static void setPvpCooldownTimestamp(Player player) {
        player.getPersistentDataContainer().set(
                pvpToggledTimestampKey,
                PersistentDataType.LONG,
                Instant.now().toEpochMilli()
        );
    }

    public static Instant getPvpCooldownTimestamp(Player player) {
        PersistentDataContainer container = player.getPersistentDataContainer();

        if (!container.has(pvpToggledTimestampKey, PersistentDataType.LONG)) {
            container.set(pvpToggledTimestampKey, PersistentDataType.LONG, 0L);
        }

        return Instant.ofEpochMilli(
                Objects.requireNonNull(
                        container.get(pvpToggledTimestampKey, PersistentDataType.LONG)
                )
        );
    }

    public static boolean isPvpCooldownDone(Player player) {
        if (player.hasPermission("pvptoggle.nocooldown")) return true;

        final ConfigurationService config = ConfigurationService.getInstance();

        return Instant.now().isAfter(
                Instant.ofEpochMilli(
                        getPvpCooldownTimestamp(player).toEpochMilli()
                                + config.getCooldownDuration() * 1000L
                )
        );
    }

    public static void shutdown() {
        activeTasks.values().forEach(BukkitTask::cancel);
        combatTasks.values().forEach(BukkitTask::cancel);

        activeTasks.clear();
        combatTasks.clear();
        combatTagged.clear();
        expirationTimes.clear();
    }
}
