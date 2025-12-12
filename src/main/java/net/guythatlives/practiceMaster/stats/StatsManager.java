package net.guythatlives.practiceMaster.stats;

import net.guythatlives.practiceMaster.PracticeMaster;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages player statistics persistence and leaderboards
 */
public class StatsManager {

    private final PracticeMaster plugin;
    private final File statsFolder;
    private final Map<UUID, PlayerStats> cachedStats;

    public StatsManager(PracticeMaster plugin) {
        this.plugin = plugin;
        this.statsFolder = new File(plugin.getDataFolder(), "stats");
        this.cachedStats = new HashMap<>();

        if (!statsFolder.exists()) {
            statsFolder.mkdirs();
        }
    }

    /**
     * Gets or creates stats for a player
     */
    public PlayerStats getStats(Player player) {
        return getStats(player.getUniqueId(), player.getName());
    }

    /**
     * Gets or creates stats for a player by UUID
     */
    public PlayerStats getStats(UUID uuid, String playerName) {
        // Check cache first
        if (cachedStats.containsKey(uuid)) {
            PlayerStats stats = cachedStats.get(uuid);
            stats.setPlayerName(playerName); // Update name in case it changed
            return stats;
        }

        // Try to load from file
        PlayerStats stats = loadStats(uuid);
        if (stats == null) {
            stats = new PlayerStats(uuid, playerName);
        } else {
            stats.setPlayerName(playerName);
        }

        cachedStats.put(uuid, stats);
        return stats;
    }

    /**
     * Saves stats for a player
     */
    public void saveStats(PlayerStats stats) {
        File file = new File(statsFolder, stats.getPlayerUUID().toString() + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        Map<String, Object> serialized = stats.serialize();
        for (Map.Entry<String, Object> entry : serialized.entrySet()) {
            config.set(entry.getKey(), entry.getValue());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save stats for " + stats.getPlayerName() + ": " + e.getMessage());
        }
    }

    /**
     * Loads stats from file
     */
    private PlayerStats loadStats(UUID uuid) {
        File file = new File(statsFolder, uuid.toString() + ".yml");
        if (!file.exists()) {
            return null;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Map<String, Object> map = new HashMap<>();

        for (String key : config.getKeys(false)) {
            map.put(key, config.get(key));
        }

        try {
            return new PlayerStats(map);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load stats for " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Saves all cached stats
     */
    public void saveAllStats() {
        for (PlayerStats stats : cachedStats.values()) {
            saveStats(stats);
        }
    }

    /**
     * Clears the cache (for reload)
     */
    public void clearCache() {
        saveAllStats();
        cachedStats.clear();
    }

    // === Leaderboard Methods ===

    /**
     * Gets the ELO leaderboard
     */
    public List<PlayerStats> getEloLeaderboard(int limit) {
        return getAllStats().stream()
                .filter(s -> s.getEloGamesPlayed() >= 5) // Minimum 5 games to rank
                .sorted((a, b) -> Double.compare(b.getElo(), a.getElo()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Gets the best time leaderboard for a specific arena
     */
    public List<LeaderboardEntry> getArenaTimeLeaderboard(String arenaName, int limit) {
        List<LeaderboardEntry> entries = new ArrayList<>();

        for (PlayerStats stats : getAllStats()) {
            PlayerStats.ArenaStats arenaStats = stats.getArenaStats(arenaName);
            if (arenaStats != null && arenaStats.getBestTimeMs() > 0) {
                entries.add(new LeaderboardEntry(
                        stats.getPlayerUUID(),
                        stats.getPlayerName(),
                        arenaStats.getBestTimeMs(),
                        stats.getElo()
                ));
            }
        }

        return entries.stream()
                .sorted(Comparator.comparingLong(LeaderboardEntry::getValue))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Gets the style points leaderboard
     */
    public List<PlayerStats> getStylePointsLeaderboard(int limit) {
        return getAllStats().stream()
                .filter(s -> s.getTotalStylePoints() > 0)
                .sorted((a, b) -> Integer.compare(b.getTotalStylePoints(), a.getTotalStylePoints()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Gets the completion rate leaderboard
     */
    public List<PlayerStats> getCompletionRateLeaderboard(int limit) {
        return getAllStats().stream()
                .filter(s -> s.getTotalSessions() >= 10) // Minimum 10 sessions
                .sorted((a, b) -> Double.compare(b.getCompletionRate(), a.getCompletionRate()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Gets the speed leaderboard (average blocks per second)
     */
    public List<PlayerStats> getSpeedLeaderboard(int limit) {
        return getAllStats().stream()
                .filter(s -> s.getTotalBlocksPlaced() >= 100) // Minimum 100 blocks placed
                .sorted((a, b) -> Double.compare(b.getAverageBlocksPerSecond(), a.getAverageBlocksPerSecond()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Gets all stats (loads all files if needed)
     */
    private List<PlayerStats> getAllStats() {
        // Load all stat files
        File[] files = statsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return new ArrayList<>(cachedStats.values());
        }

        for (File file : files) {
            String uuidStr = file.getName().replace(".yml", "");
            try {
                UUID uuid = UUID.fromString(uuidStr);
                if (!cachedStats.containsKey(uuid)) {
                    PlayerStats stats = loadStats(uuid);
                    if (stats != null) {
                        cachedStats.put(uuid, stats);
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid UUID filename, skip
            }
        }

        return new ArrayList<>(cachedStats.values());
    }

    /**
     * Gets the player's rank on the ELO leaderboard
     */
    public int getEloRank(UUID playerUUID) {
        List<PlayerStats> leaderboard = getAllStats().stream()
                .filter(s -> s.getEloGamesPlayed() >= 5)
                .sorted((a, b) -> Double.compare(b.getElo(), a.getElo()))
                .collect(Collectors.toList());

        for (int i = 0; i < leaderboard.size(); i++) {
            if (leaderboard.get(i).getPlayerUUID().equals(playerUUID)) {
                return i + 1;
            }
        }
        return -1; // Not ranked
    }

    /**
     * Entry for time-based leaderboards
     */
    public static class LeaderboardEntry {
        private final UUID playerUUID;
        private final String playerName;
        private final long value; // Time in ms
        private final double elo;

        public LeaderboardEntry(UUID playerUUID, String playerName, long value, double elo) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.value = value;
            this.elo = elo;
        }

        public UUID getPlayerUUID() { return playerUUID; }
        public String getPlayerName() { return playerName; }
        public long getValue() { return value; }
        public double getElo() { return elo; }

        public String getFormattedTime() {
            long seconds = value / 1000;
            long millis = value % 1000;
            int minutes = (int) (seconds / 60);
            int secs = (int) (seconds % 60);
            return String.format("%02d:%02d.%03d", minutes, secs, millis);
        }
    }
}
