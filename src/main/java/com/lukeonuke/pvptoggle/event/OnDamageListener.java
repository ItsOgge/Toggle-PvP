package com.lukeonuke.pvptoggle.event;

import com.lukeonuke.pvptoggle.service.ChatFormatterService;
import com.lukeonuke.pvptoggle.service.ConfigurationService;
import com.lukeonuke.pvptoggle.service.PvpService;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class OnDamageListener implements Listener {

    public OnDamageListener() {
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        Player player;
        Player damager = null;
        Tameable pet = null;
        final ConfigurationService cs = ConfigurationService.getInstance();
        if (entity instanceof Tameable) {
            pet = (Tameable) entity;
            if (pet.getOwner() instanceof Player) {
                player = (Player) pet.getOwner();
            } else return;
        } else if (!(entity instanceof Player)) return;
        else player = (Player) entity;
        if(cs.getDisabledWorlds().contains(player.getWorld().getName())) {
            return;
        }
        if (event.getDamager() instanceof Player damagerLocal) {
            damager = damagerLocal;
            if (damager.hasPermission("pvptoggle.bypass")) {
                return;
            }
            if (!PvpService.isPvpEnabled(damager) || !PvpService.isPvpEnabled(player)) {
                event.setCancelled(true);
            }
            if (pet != null && !cs.getFriendlyFire() && damager.equals(player)) {
                event.setCancelled(true);
            }
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            damager = shooter;
            if (pet != null && !cs.getFriendlyFire() && damager.equals(player)) {
                event.setCancelled(true);
            }
            if (!((damager == player || event.getDamageSource().getCausingEntity() == player) && cs.getHitSelf())) {
                if (!PvpService.isPvpEnabled(player) || !PvpService.isPvpEnabled(damager)) {
                    event.setCancelled(true);
                }
            }
        }
        if (event.getDamageSource().getCausingEntity() instanceof Player cause) {
            if (!((damager == player || event.getDamageSource().getCausingEntity() == player) && cs.getHitSelf())) {
                if (!PvpService.isPvpEnabled(player) || !PvpService.isPvpEnabled(cause)) {
                    event.setCancelled(true);
                    if (cs.getSpawnParticles()) {
                        cause.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, Objects.requireNonNullElse(pet, player).getLocation(), 10);
                    }
                    if (cs.getSendFeedback()) {
                        TextComponent actionBarMessage = getActionBarMessage(pet, player, cause);
                        cause.spigot().sendMessage(ChatMessageType.ACTION_BAR, actionBarMessage);
                    }
                    if (!event.isCancelled() && cs.getAntiAbuse()) {
                        PvpService.setPvpCooldownTimestamp(player);
                    }
                    return;
                }
            }
            if (pet != null && !cs.getFriendlyFire() && (damager == player || event.getDamageSource().getCausingEntity() == player)) {
                event.setCancelled(true);
                if (cs.getSpawnParticles()) {
                    cause.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, pet.getLocation(), 10);
                }
                if (cs.getSendFeedback()) {
                    TextComponent actionBarMessage = getActionBarMessage(pet, player, cause);
                    cause.spigot().sendMessage(ChatMessageType.ACTION_BAR, actionBarMessage);
                }
                if (!event.isCancelled() && cs.getAntiAbuse()) {
                    PvpService.setPvpCooldownTimestamp(player);
                }
                return;
            }
        }
        if (event.getDamager() instanceof Tameable tamableAttacker) {
            if (tamableAttacker.getOwner() instanceof Player attackerOwner) {
                if (!PvpService.isPvpEnabled(player) || !PvpService.isPvpEnabled(attackerOwner)) {
                    event.setCancelled(true);
                    if (tamableAttacker instanceof Wolf wolf) {
                        wolf.setAngry(false);
                    }
                }
            }
        }
        if (event.isCancelled() && damager != null) {
            if (cs.getSpawnParticles()) {
                damager.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, Objects.requireNonNullElse(pet, player).getLocation(), 10);
            }
            if (cs.getSendFeedback()) {
                TextComponent actionBarMessage = getActionBarMessage(pet, player, damager);
                damager.spigot().sendMessage(ChatMessageType.ACTION_BAR, actionBarMessage);
            }
        }
        if (!event.isCancelled() && cs.getAntiAbuse()) {
            PvpService.setPvpCooldownTimestamp(player);
        }
        if (!event.isCancelled() && damager != null && player != null) {
            PvpService.tagPlayer(player);
            PvpService.tagPlayer(damager);
        }
    }

    private static @NotNull TextComponent getActionBarMessage(Tameable pet, Player player, Player damager) {
        String message;
        final ConfigurationService cs = ConfigurationService.getInstance();
        if (pet != null) {
            if (damager.equals(player)) message = ChatFormatterService.addPrefix(cs.getFfMessage());
            else message = ChatFormatterService.addPrefix(cs.getPetPvpMessage().replace("%s", player.getDisplayName() + ChatColor.RESET).replace("%r", pet.getName()));
        } else {
            message = ChatFormatterService.addPrefix(cs.getFeedbackMessage().replace("%s", player.getDisplayName() + ChatColor.RESET));
        }
        return new TextComponent(message);
    }
}