package com.lukeonuke.pvptoggle.event;

import com.lukeonuke.pvptoggle.service.PvpService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;

public class OnCommandListener implements Listener {

    private static final List<String> BLOCKED_COMMANDS = List.of(
            "/warp",
            "/home",
            "/tpahere",
            "/tpaccept",
            "/tpa",
            "/lobby",
            "/sethome"
    );

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!PvpService.isInCombat(event.getPlayer())) return;
        String message = event.getMessage().toLowerCase();
        for (String cmd : BLOCKED_COMMANDS) {
            if (message.startsWith(cmd)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cDu kan inte använda det kommandot under combat!");
                return;
            }
        }
    }
}