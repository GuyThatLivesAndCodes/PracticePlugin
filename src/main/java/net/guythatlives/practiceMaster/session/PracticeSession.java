package net.guythatlives.practiceMaster.session;

import net.guythatlives.practiceMaster.PracticeMaster;
import net.guythatlives.practiceMaster.arena.Arena;
import net.guythatlives.practiceMaster.arena.ArenaEvent;
import net.guythatlives.practiceMaster.stats.PlayerStats;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Random;
import java.util.UUID;

public class PracticeSession {

    private final UUID playerUUID;
    private final Arena arena;
    private final Location playLocation;
    private final Location previousLocation;
    private final GameMode previousGameMode;
    private final boolean timerEnabled;
    private final long startTimeMs;
    private int timeElapsedSeconds;
    private BukkitTask timerTask;
    private BukkitTask eventTask;
    private final Random random;

    // Stats tracking
    private final PlayerStats.SessionMetrics metrics;
    private Location lastBlockPlacement;
    private long lastPlacementTime;
    private boolean hasStartedMoving;
    private double lowestYPosition;

    public PracticeSession(Player player, Arena arena, Location playLocation, boolean timerEnabled) {
        this.playerUUID = player.getUniqueId();
        this.arena = arena;
        this.playLocation = playLocation;
        this.previousLocation = player.getLocation().clone();
        this.previousGameMode = player.getGameMode();
        this.timerEnabled = timerEnabled;
        this.startTimeMs = System.currentTimeMillis();
        this.timeElapsedSeconds = 0;
        this.random = new Random();

        // Initialize metrics tracking
        this.metrics = new PlayerStats.SessionMetrics();
        this.lastBlockPlacement = null;
        this.lastPlacementTime = 0;
        this.hasStartedMoving = false;
        this.lowestYPosition = player.getLocation().getY();

        // Record session start
        PracticeMaster.getInstance().getStatsManager().getStats(player).startSession();

        startSession(player);
    }

    private void startSession(Player player) {
        // Save and clear inventory
        PracticeMaster.getInstance().getInventoryManager().saveAndClearInventory(player);

        // Load arena at the play location
        arena.loadArenaAt(playLocation);

        // Teleport to arena spawn
        Location spawn = arena.getSpawnForPlayLocation(playLocation);
        if (spawn != null) {
            player.teleport(spawn);
            this.lowestYPosition = spawn.getY();
        }

        // Set gamemode
        player.setGameMode(GameMode.SURVIVAL);

        // Give arena kit
        PracticeMaster.getInstance().getInventoryManager().giveArenaKit(player, arena);

        // Start timer if enabled
        if (timerEnabled) {
            startTimer();
        }

        // Start arena events
        startArenaEvents();

        player.sendMessage("§a§l[PracticeMaster] §7Practice started in arena: §e" + arena.getName());
        if (timerEnabled) {
            player.sendMessage("§7Timer: §aEnabled");
        }

        // Show player's current stats
        PlayerStats stats = PracticeMaster.getInstance().getStatsManager().getStats(player);
        player.sendMessage("§7Your ELO: " + stats.getEloRank() + " §7(" + String.format("%.0f", stats.getElo()) + ")");
    }

    private void startTimer() {
        timerTask = Bukkit.getScheduler().runTaskTimer(PracticeMaster.getInstance(), () -> {
            Player player = getPlayer();
            if (player == null) {
                endSession(false);
                return;
            }

            timeElapsedSeconds++;

            // Display timer on action bar
            int minutes = timeElapsedSeconds / 60;
            int seconds = timeElapsedSeconds % 60;
            String timeStr = String.format("%02d:%02d", minutes, seconds);

            // Show style points if any
            String styleStr = metrics.stylePoints > 0 ? " §d+" + metrics.stylePoints + " style" : "";

            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText("§e⏱ " + timeStr + styleStr));

        }, 20L, 20L);
    }

    private void startArenaEvents() {
        if (arena.getArenaEvent() == ArenaEvent.CUSTOM && arena.getCustomEventName() != null) {
            startCustomEvents();
            return;
        }

        if (arena.getArenaEvent() == null || arena.getArenaEvent().name().equals("NONE")) {
            return;
        }

        int minDelay = PracticeMaster.getInstance().getConfig().getInt("event_timing.min_delay_seconds", 3);
        int maxDelay = PracticeMaster.getInstance().getConfig().getInt("event_timing.max_delay_seconds", 8);

        scheduleNextEvent(minDelay, maxDelay);
    }

    private void startCustomEvents() {
        int minDelay = PracticeMaster.getInstance().getConfig().getInt("event_timing.min_delay_seconds", 3);
        int maxDelay = PracticeMaster.getInstance().getConfig().getInt("event_timing.max_delay_seconds", 8);

        scheduleNextCustomEvent(minDelay, maxDelay);
    }

    private void scheduleNextCustomEvent(int minDelay, int maxDelay) {
        int delay = (random.nextInt(maxDelay - minDelay + 1) + minDelay) * 20;

        eventTask = Bukkit.getScheduler().runTaskLater(PracticeMaster.getInstance(), () -> {
            Player player = getPlayer();
            if (player == null) {
                return;
            }

            if (arena.isInBoundsAt(player.getLocation(), playLocation)) {
                ArenaEvent.triggerCustomEvent(player, arena.getCustomEventName());
            }

            scheduleNextCustomEvent(minDelay, maxDelay);

        }, delay);
    }

    private void scheduleNextEvent(int minDelay, int maxDelay) {
        int delay = (random.nextInt(maxDelay - minDelay + 1) + minDelay) * 20;

        eventTask = Bukkit.getScheduler().runTaskLater(PracticeMaster.getInstance(), () -> {
            Player player = getPlayer();
            if (player == null) {
                return;
            }

            if (arena.isInBoundsAt(player.getLocation(), playLocation)) {
                arena.getArenaEvent().trigger(player);
            }

            scheduleNextEvent(minDelay, maxDelay);

        }, delay);
    }

    // === Stats Tracking Methods ===

    /**
     * Called when player places a block
     */
    public void recordBlockPlacement(Location location, boolean wasUseful) {
        long now = System.currentTimeMillis();

        metrics.addBlockPlaced(wasUseful);

        // Check for consecutive placements (bridging style)
        if (lastBlockPlacement != null) {
            double distance = lastBlockPlacement.distance(location);
            long timeDiff = now - lastPlacementTime;

            // If placed within 1 second and reasonably close, count as consecutive
            if (timeDiff <= 1000 && distance <= 2.0) {
                metrics.recordConsecutivePlacement();
            } else {
                metrics.breakStreak();
            }
        }

        lastBlockPlacement = location.clone();
        lastPlacementTime = now;
        hasStartedMoving = true;
    }

    /**
     * Called when player recovers from a near-fall
     */
    public void recordClutchSave() {
        metrics.recordClutchSave();
        Player player = getPlayer();
        if (player != null) {
            player.sendMessage("§d§l+5 CLUTCH! §7Nice recovery!");
        }
    }

    /**
     * Updates the lowest Y position (for fall detection)
     */
    public void updatePosition(Location location) {
        if (location.getY() < lowestYPosition) {
            lowestYPosition = location.getY();
        }
    }

    /**
     * Check if player just made a clutch save
     */
    public boolean checkForClutchSave(Location from, Location to) {
        // If player was falling (Y decreasing significantly) and now recovered
        if (hasStartedMoving && from.getY() - to.getY() > 0.5) {
            // Check if they're close to out of bounds
            Location[] bounds = arena.getBoundsForPlayLocation(playLocation);
            if (bounds != null) {
                double minY = Math.min(bounds[0].getY(), bounds[1].getY());
                if (to.getY() <= minY + 2 && to.getY() > minY) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Ends the session with success
     */
    public void endWithSuccess() {
        endSession(true);
    }

    /**
     * Ends the session with failure
     */
    public void endWithFailure() {
        endSession(false);
    }

    private void endSession(boolean success) {
        Player player = getPlayer();
        long durationMs = System.currentTimeMillis() - startTimeMs;

        // Cancel tasks
        if (timerTask != null) {
            timerTask.cancel();
        }
        if (eventTask != null) {
            eventTask.cancel();
        }

        // Calculate final metrics
        metrics.calculateFinalStats(durationMs);

        // Record stats
        if (player != null) {
            PlayerStats stats = PracticeMaster.getInstance().getStatsManager().getStats(player);

            if (success) {
                stats.recordCompletion(durationMs, arena.getName(), metrics);

                // Format time
                long seconds = durationMs / 1000;
                long millis = durationMs % 1000;
                int minutes = (int) (seconds / 60);
                int secs = (int) (seconds % 60);
                String timeStr = String.format("%02d:%02d.%03d", minutes, secs, millis);

                player.sendMessage("§a§l✓ SUCCESS! §7Arena completed!");
                player.sendMessage("§7Time: §e" + timeStr);
                player.sendMessage("§7Style Points: §d+" + metrics.stylePoints);
                player.sendMessage("§7Blocks Placed: §b" + metrics.blocksPlaced);

                if (metrics.blocksPlaced > 0) {
                    double accuracy = ((double) (metrics.blocksPlaced - metrics.blocksWasted) / metrics.blocksPlaced) * 100;
                    player.sendMessage("§7Accuracy: §a" + String.format("%.1f%%", accuracy));
                }

                if (metrics.wasPerfectRun) {
                    player.sendMessage("§6§l★ PERFECT RUN! §7+10 style points");
                }

                // Show new ELO
                player.sendMessage("§7New ELO: " + stats.getEloRank() + " §7(" + String.format("%.0f", stats.getElo()) + ")");

                // Check for personal best
                if (durationMs == stats.getBestTimeMs()) {
                    player.sendMessage("§6§l★ NEW PERSONAL BEST!");
                }
            } else {
                stats.recordFailure(durationMs, arena.getName());
                player.sendMessage("§c§lFAILED! §7Better luck next time!");
                player.sendMessage("§7Blocks Placed: §b" + metrics.blocksPlaced);
            }

            // Save stats
            PracticeMaster.getInstance().getStatsManager().saveStats(stats);

            // Restore player
            player.teleport(previousLocation);
            player.setGameMode(previousGameMode);
            PracticeMaster.getInstance().getInventoryManager().restoreInventory(player);
        }

        // Remove from session manager
        PracticeMaster.getInstance().getSessionManager().removeSession(playerUUID);
    }

    /**
     * Legacy endSession method for compatibility
     */
    public void endSession() {
        endSession(false);
    }

    public boolean isPlayerInBounds() {
        Player player = getPlayer();
        if (player == null) return false;
        return arena.isInBoundsAt(player.getLocation(), playLocation);
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(playerUUID);
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public Arena getArena() {
        return arena;
    }

    public Location getPlayLocation() {
        return playLocation;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public int getTimeElapsedSeconds() {
        return timeElapsedSeconds;
    }

    public boolean isTimerEnabled() {
        return timerEnabled;
    }

    public PlayerStats.SessionMetrics getMetrics() {
        return metrics;
    }
}
