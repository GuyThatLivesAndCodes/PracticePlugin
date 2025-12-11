package net.guythatlives.practiceMaster.session;

import net.guythatlives.practiceMaster.PracticeMaster;
import net.guythatlives.practiceMaster.arena.Arena;
import net.guythatlives.practiceMaster.arena.ArenaEvent;
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
    private final Location playLocation; // The specific instance location
    private final Location previousLocation;
    private final GameMode previousGameMode;
    private final boolean timerEnabled;
    private long startTime;
    private int timeElapsed;
    private BukkitTask timerTask;
    private BukkitTask eventTask;
    private final Random random;

    public PracticeSession(Player player, Arena arena, Location playLocation, boolean timerEnabled) {
        this.playerUUID = player.getUniqueId();
        this.arena = arena;
        this.playLocation = playLocation;
        this.previousLocation = player.getLocation().clone();
        this.previousGameMode = player.getGameMode();
        this.timerEnabled = timerEnabled;
        this.startTime = System.currentTimeMillis();
        this.timeElapsed = 0;
        this.random = new Random();

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
    }

    private void startTimer() {
        timerTask = Bukkit.getScheduler().runTaskTimer(PracticeMaster.getInstance(), () -> {
            Player player = getPlayer();
            if (player == null) {
                endSession();
                return;
            }

            timeElapsed++;

            // Display timer on action bar
            int minutes = timeElapsed / 60;
            int seconds = timeElapsed % 60;
            String timeStr = String.format("%02d:%02d", minutes, seconds);

            // Send action bar using Spigot method
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText("§e⏱ " + timeStr));

        }, 20L, 20L); // Run every second
    }

    private void startArenaEvents() {
        // Check if using custom event
        if (arena.getArenaEvent() == ArenaEvent.CUSTOM && arena.getCustomEventName() != null) {
            startCustomEvents();
            return;
        }

        // Legacy hardcoded events
        if (arena.getArenaEvent() == null || arena.getArenaEvent().name().equals("NONE")) {
            return;
        }

        // Get event timing from config
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
        int delay = (random.nextInt(maxDelay - minDelay + 1) + minDelay) * 20; // Convert to ticks

        eventTask = Bukkit.getScheduler().runTaskLater(PracticeMaster.getInstance(), () -> {
            Player player = getPlayer();
            if (player == null) {
                return;
            }

            // Only trigger if player is in arena bounds
            if (arena.isInBoundsAt(player.getLocation(), playLocation)) {
                arena.getArenaEvent().trigger(player);
            }

            // Schedule next event
            scheduleNextEvent(minDelay, maxDelay);

        }, delay);
    }

    public void endSession() {
        Player player = getPlayer();

        // Cancel tasks
        if (timerTask != null) {
            timerTask.cancel();
        }
        if (eventTask != null) {
            eventTask.cancel();
        }

        // Restore player
        if (player != null) {
            player.teleport(previousLocation);
            player.setGameMode(previousGameMode);

            // Restore inventory
            PracticeMaster.getInstance().getInventoryManager().restoreInventory(player);

            if (timerEnabled) {
                int minutes = timeElapsed / 60;
                int seconds = timeElapsed % 60;
                String timeStr = String.format("%02d:%02d", minutes, seconds);
                player.sendMessage("§a§l[PracticeMaster] §7Practice ended! Time: §e" + timeStr);
            } else {
                player.sendMessage("§a§l[PracticeMaster] §7Practice ended!");
            }
        }

        // Remove from session manager
        PracticeMaster.getInstance().getSessionManager().removeSession(playerUUID);
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

    public long getStartTime() {
        return startTime;
    }

    public int getTimeElapsed() {
        return timeElapsed;
    }

    public boolean isTimerEnabled() {
        return timerEnabled;
    }
}