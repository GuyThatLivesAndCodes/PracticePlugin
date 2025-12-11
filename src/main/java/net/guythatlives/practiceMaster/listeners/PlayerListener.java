package net.guythatlives.practiceMaster.listeners;

import net.guythatlives.practiceMaster.PracticeMaster;
import net.guythatlives.practiceMaster.session.PracticeSession;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final PracticeMaster plugin;

    public PlayerListener(PracticeMaster plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getSessionManager().isInSession(player)) {
            return;
        }

        PracticeSession session = plugin.getSessionManager().getSession(player);

        // Check if player fell out of bounds
        if (!session.isPlayerInBounds()) {
            player.sendMessage("§c§lFAILED! §7You went out of bounds!");
            plugin.getSessionManager().endSession(player);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getSessionManager().isInSession(player)) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        // Check if player stepped on a pressure plate (win condition)
        if (isPressurePlate(block.getType())) {
            PracticeSession session = plugin.getSessionManager().getSession(player);

            player.sendMessage("§a§l✓ SUCCESS! §7You completed the arena!");

            if (session.isTimerEnabled()) {
                int minutes = session.getTimeElapsed() / 60;
                int seconds = session.getTimeElapsed() % 60;
                String timeStr = String.format("%02d:%02d", minutes, seconds);
                player.sendMessage("§7Time: §e" + timeStr);
            }

            plugin.getSessionManager().endSession(player);
        }
    }

    private boolean isPressurePlate(Material material) {
        return material == Material.STONE_PRESSURE_PLATE ||
                material == Material.LIGHT_WEIGHTED_PRESSURE_PLATE ||
                material == Material.HEAVY_WEIGHTED_PRESSURE_PLATE ||
                material == Material.OAK_PRESSURE_PLATE ||
                material == Material.SPRUCE_PRESSURE_PLATE ||
                material == Material.BIRCH_PRESSURE_PLATE ||
                material == Material.JUNGLE_PRESSURE_PLATE ||
                material == Material.ACACIA_PRESSURE_PLATE ||
                material == Material.DARK_OAK_PRESSURE_PLATE ||
                material == Material.CRIMSON_PRESSURE_PLATE ||
                material == Material.WARPED_PRESSURE_PLATE ||
                material == Material.POLISHED_BLACKSTONE_PRESSURE_PLATE;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (!plugin.getSessionManager().isInSession(player)) {
            return;
        }

        player.sendMessage("§c§lFAILED! §7You died!");
        plugin.getSessionManager().endSession(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (plugin.getSessionManager().isInSession(player)) {
            plugin.getSessionManager().endSession(player);
        }
    }
}