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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PvpService {
    private static final NamespacedKey isPvpEnabledKey = new NamespacedKey(PvpToggle.getPlugin(), "isPvpEnabled");
    private static final NamespacedKey pvpToggledTimestampKey = new NamespacedKey(PvpToggle.getPlugin(), "pvpToggledTimestamp");
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final ConcurrentHashMap<Player, ScheduledFuture<?>> activeTasks = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> expirationTimes = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> combatTagged = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, BukkitTask> combatTasks = new ConcurrentHashMap<>();
    private static final int COMBAT_DURATION_SECONDS = 10;

    public static boolean isPvpEnabled(@NotNull Player player) {
        PersistentDataContainer dataContainer = player.getPersistentDataContainer();
        return Boolean.TRUE.equals(dataContainer.get(isPvpEnabledKey, PersistentDataType.BOOLEAN));
    }

    public static void setPvpEnabled(Player player, boolean enabled) {
        final ConfigurationService configurationService = ConfigurationService.getInstance();
        PersistentDataContainer dataContainer = player.getPersistentDataContainer();
        dataContainer.set(isPvpEnabledKey, PersistentDataType.BOOLEAN, enabled);
        dataContainer.set(pvpToggledTimestampKey, PersistentDataType.LONG, Instant.now().toEpochMilli());
        if (configurationService.getLimitedTime() < 0) return;
        if (enabled) {
            ScheduledFuture<?> existingTask = activeTasks.remove(player);
            if (existingTask != null) {
                existingTask.cancel(false);
            }
            return;
        }
        ScheduledFuture<?> existingTask = activeTasks.get(player);
        if (existingTask != null) {
            existingTask.cancel(false);
        }
        Runnable task = () -> {
            if (player.isOnline()) {
                setPvpEnabled(player, true);
                if (!configurationService.getLimitedMessage().isEmpty()) {
                    player.sendMessage(ChatFormatterService.addPrefix(configurationService.getLimitedMessage().replace("%s", ChatFormatterService.booleanHumanReadable(false))));
                }
            } else {
                resetPvpStatus(player);
            }
        };
        ScheduledFuture<?> scheduledTask = scheduler.schedule(task, configurationService.getLimitedTime(), TimeUnit.SECONDS);
        activeTasks.put(player, scheduledTask);
    }

    public static void tagPlayer(Player player) {
        long expireTime = System.currentTimeMillis() + (COMBAT_DURATION_SECONDS * 1000);
        combatTagged.put(player.getUniqueId(), expireTime);
        if (combatTasks.containsKey(player.getUniqueId())) return;
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(PvpToggle.getPlugin(), () -> {
            if (!player.isOnline()) {
                removeCombatTag(player);
                return;
            }
            if (!isInCombat(player)) {
                removeCombatTag(player);
                return;
            }
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cDu är i combat!"));
        }, 0L, 20L);
        combatTasks.put(player.getUniqueId(), task);
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
        combatTagged.remove(player.getUniqueId());
        BukkitTask task = combatTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    public static void handlePlayerLeave(Player player) {
        final ConfigurationService configurationService = ConfigurationService.getInstance();
        if (isInCombat(player)) {
            player.setHealth(0.0);
            removeCombatTag(player);
        }
        ScheduledFuture<?> existingTask = activeTasks.remove(player);
        if (existingTask != null) {
            existingTask.cancel(false);
        }
        expirationTimes.put(player.getUniqueId().toString(), System.currentTimeMillis() + configurationService.getLimitedTime() * 1000);
    }

    public static void handlePlayerJoin(Player player) {
        final ConfigurationService configurationService = ConfigurationService.getInstance();
        String uuid = player.getUniqueId().toString();
        Long expirationTime = expirationTimes.get(uuid);
        if (configurationService.getLimitedTime() < 0) {
            return;
        }
        if (expirationTime != null) {
            if (System.currentTimeMillis() > expirationTime) {
                setPvpEnabled(player, true);
                if (!configurationService.getLimitedMessage().isEmpty()) {
                    player.sendMessage(ChatFormatterService.addPrefix(configurationService.getLimitedMessage().replace("%s", ChatFormatterService.booleanHumanReadable(false))));
                }
            } else {
                setPvpEnabled(player, false);
            }
        } else {
            resetPvpStatus(player);
        }
    }

    private static void resetPvpStatus(Player player) {
        final ConfigurationService configurationService = ConfigurationService.getInstance();
        player.getPersistentDataContainer().set(isPvpEnabledKey, PersistentDataType.BOOLEAN, configurationService.getDefaultPvp());
    }

    public static void setPvpCooldownTimestamp(Player player) {
        PersistentDataContainer dataContainer = player.getPersistentDataContainer();
        dataContainer.set(pvpToggledTimestampKey, PersistentDataType.LONG, Instant.now().toEpochMilli());
    }

    public static Instant getPvpCooldownTimestamp(Player player) {
        PersistentDataContainer dataContainer = player.getPersistentDataContainer();
        if (!dataContainer.has(pvpToggledTimestampKey, PersistentDataType.LONG))
            dataContainer.set(pvpToggledTimestampKey, PersistentDataType.LONG, 0L);
        return Instant.ofEpochMilli(Objects.requireNonNull(dataContainer.get(pvpToggledTimestampKey, PersistentDataType.LONG)));
    }

    public static boolean isPvpCooldownDone(Player player) {
        if (player.hasPermission("pvptoggle.nocooldown")) {
            return true;
        }
        final ConfigurationService configurationService = ConfigurationService.getInstance();
        return Instant.now().isAfter(Instant.ofEpochMilli(getPvpCooldownTimestamp(player).toEpochMilli() + configurationService.getCooldownDuration() * 1000));
    }

    public static void shutdown() {
        scheduler.shutdown();
        activeTasks.clear();
        expirationTimes.clear();
        combatTagged.clear();
        combatTasks.values().forEach(BukkitTask::cancel);
        combatTasks.clear();
    }
}