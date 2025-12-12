package net.guythatlives.practiceMaster.listeners;

import net.guythatlives.practiceMaster.PracticeMaster;
import net.guythatlives.practiceMaster.session.PracticeSession;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final PracticeMaster plugin;

    public PlayerListener(PracticeMaster plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getSessionManager().isInSession(player)) {
            return;
        }

        PracticeSession session = plugin.getSessionManager().getSession(player);

        // Update position tracking
        session.updatePosition(event.getTo());

        // Check for clutch save (recovering from near-fall)
        if (event.getFrom().getY() > event.getTo().getY()) {
            // Player is falling - tracked by updatePosition
        } else if (event.getFrom().getY() < event.getTo().getY() &&
                   session.checkForClutchSave(event.getFrom(), event.getTo())) {
            // Player recovered from a fall near the boundary
            session.recordClutchSave();
        }

        // Check if player fell out of bounds
        if (!session.isPlayerInBounds()) {
            player.sendMessage("§c§lFAILED! §7You went out of bounds!");
            session.endWithFailure();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getSessionManager().isInSession(player)) {
            return;
        }

        PracticeSession session = plugin.getSessionManager().getSession(player);

        // Track block placement
        Block block = event.getBlock();

        // For now, assume all placed blocks are useful
        // Future: Could check if block extends bridge path
        boolean wasUseful = true;

        session.recordBlockPlacement(block.getLocation(), wasUseful);
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
            session.endWithSuccess();
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
                material == Material.POLISHED_BLACKSTONE_PRESSURE_PLATE ||
                material == Material.MANGROVE_PRESSURE_PLATE ||
                material == Material.CHERRY_PRESSURE_PLATE ||
                material == Material.BAMBOO_PRESSURE_PLATE;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (!plugin.getSessionManager().isInSession(player)) {
            return;
        }

        PracticeSession session = plugin.getSessionManager().getSession(player);
        player.sendMessage("§c§lFAILED! §7You died!");
        session.endWithFailure();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (plugin.getSessionManager().isInSession(player)) {
            PracticeSession session = plugin.getSessionManager().getSession(player);
            session.endWithFailure();
        }
    }
}
