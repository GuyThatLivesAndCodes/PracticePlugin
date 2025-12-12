package net.guythatlives.practiceMaster.listeners;

import net.guythatlives.practiceMaster.PracticeMaster;
import net.guythatlives.practiceMaster.session.PracticeSession;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerListener implements Listener {

    private final PracticeMaster plugin;
    // Track players who are being respawned to prevent double-handling
    private final Set<UUID> respawningPlayers = new HashSet<>();

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

    /**
     * Prevents lethal damage and respawns player at arena spawn instead
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();

        if (!plugin.getSessionManager().isInSession(player)) {
            return;
        }

        // Check if this damage would kill the player
        double healthAfterDamage = player.getHealth() - event.getFinalDamage();

        if (healthAfterDamage <= 0) {
            // Cancel the lethal damage
            event.setCancelled(true);

            // Handle as a failure
            handlePlayerFailed(player, "You took lethal damage!");
        }
    }

    /**
     * Backup handler in case death somehow occurs - cancel it and handle properly
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (!plugin.getSessionManager().isInSession(player)) {
            return;
        }

        // Clear drops - player shouldn't lose items in practice
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Set custom death message or hide it
        event.setDeathMessage(null);

        // Mark player as respawning to handle in respawn event
        respawningPlayers.add(player.getUniqueId());

        // End the session with failure
        // Note: We delay this slightly to ensure respawn happens first
        PracticeSession session = plugin.getSessionManager().getSession(player);
        if (session != null) {
            player.sendMessage("§c§lFAILED! §7You died!");
            session.endWithFailure();
        }
    }

    /**
     * Handle respawn to return player to their previous location
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        if (!respawningPlayers.remove(player.getUniqueId())) {
            return;
        }

        // The session already ended in death handler, but we need to ensure
        // the player respawns at the right location. The endSession method
        // should have already teleported them, but this is a backup.
        // Since session ended, there's nothing more to do here.
    }

    /**
     * Handles player failure - cancels damage, restores health, and ends session
     */
    private void handlePlayerFailed(Player player, String reason) {
        // Prevent double-handling
        if (respawningPlayers.contains(player.getUniqueId())) {
            return;
        }
        respawningPlayers.add(player.getUniqueId());

        // Restore health immediately to prevent death
        double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        player.setHealth(maxHealth);

        // Clear any negative effects
        player.setFireTicks(0);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));

        // Get session and end it
        PracticeSession session = plugin.getSessionManager().getSession(player);
        if (session != null) {
            player.sendMessage("§c§lFAILED! §7" + reason);
            session.endWithFailure();
        }

        // Clean up respawning flag after a tick
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            respawningPlayers.remove(player.getUniqueId());
        }, 1L);
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
