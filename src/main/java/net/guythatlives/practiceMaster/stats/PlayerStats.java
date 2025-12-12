package net.guythatlives.practiceMaster.stats;

import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.util.*;

/**
 * Tracks comprehensive player statistics for practice sessions
 */
public class PlayerStats implements ConfigurationSerializable {

    private final UUID playerUUID;
    private String playerName;

    // Overall stats
    private int totalSessions;
    private int totalCompletions;
    private int totalFailures;
    private long totalPlayTimeMs;

    // ELO Rating
    private double elo;
    private int eloGamesPlayed;

    // Timing stats
    private long bestTimeMs;
    private long averageTimeMs;
    private List<Long> recentTimes; // Last 10 completion times

    // Style points (for bridging etc)
    private int totalStylePoints;
    private int consecutivePlacements; // Track streaks
    private int clutchSaves; // Near-miss recoveries
    private int perfectRuns; // No mistakes

    // Block placement stats (for bridging)
    private int totalBlocksPlaced;
    private int totalBlocksWasted; // Placed but not needed
    private double placementAccuracy; // Percentage of useful placements

    // Speed metrics
    private double averageBlocksPerSecond;
    private double peakBlocksPerSecond;

    // Per-arena stats
    private Map<String, ArenaStats> arenaStats;

    public PlayerStats(UUID playerUUID, String playerName) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.totalSessions = 0;
        this.totalCompletions = 0;
        this.totalFailures = 0;
        this.totalPlayTimeMs = 0;
        this.elo = 1000.0; // Starting ELO
        this.eloGamesPlayed = 0;
        this.bestTimeMs = Long.MAX_VALUE;
        this.averageTimeMs = 0;
        this.recentTimes = new ArrayList<>();
        this.totalStylePoints = 0;
        this.consecutivePlacements = 0;
        this.clutchSaves = 0;
        this.perfectRuns = 0;
        this.totalBlocksPlaced = 0;
        this.totalBlocksWasted = 0;
        this.placementAccuracy = 100.0;
        this.averageBlocksPerSecond = 0;
        this.peakBlocksPerSecond = 0;
        this.arenaStats = new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    public PlayerStats(Map<String, Object> map) {
        this.playerUUID = UUID.fromString((String) map.get("playerUUID"));
        this.playerName = (String) map.get("playerName");
        this.totalSessions = (Integer) map.getOrDefault("totalSessions", 0);
        this.totalCompletions = (Integer) map.getOrDefault("totalCompletions", 0);
        this.totalFailures = (Integer) map.getOrDefault("totalFailures", 0);
        this.totalPlayTimeMs = ((Number) map.getOrDefault("totalPlayTimeMs", 0L)).longValue();
        this.elo = ((Number) map.getOrDefault("elo", 1000.0)).doubleValue();
        this.eloGamesPlayed = (Integer) map.getOrDefault("eloGamesPlayed", 0);
        this.bestTimeMs = ((Number) map.getOrDefault("bestTimeMs", Long.MAX_VALUE)).longValue();
        this.averageTimeMs = ((Number) map.getOrDefault("averageTimeMs", 0L)).longValue();

        List<Number> times = (List<Number>) map.getOrDefault("recentTimes", new ArrayList<>());
        this.recentTimes = new ArrayList<>();
        for (Number time : times) {
            this.recentTimes.add(time.longValue());
        }

        this.totalStylePoints = (Integer) map.getOrDefault("totalStylePoints", 0);
        this.consecutivePlacements = (Integer) map.getOrDefault("consecutivePlacements", 0);
        this.clutchSaves = (Integer) map.getOrDefault("clutchSaves", 0);
        this.perfectRuns = (Integer) map.getOrDefault("perfectRuns", 0);
        this.totalBlocksPlaced = (Integer) map.getOrDefault("totalBlocksPlaced", 0);
        this.totalBlocksWasted = (Integer) map.getOrDefault("totalBlocksWasted", 0);
        this.placementAccuracy = ((Number) map.getOrDefault("placementAccuracy", 100.0)).doubleValue();
        this.averageBlocksPerSecond = ((Number) map.getOrDefault("averageBlocksPerSecond", 0.0)).doubleValue();
        this.peakBlocksPerSecond = ((Number) map.getOrDefault("peakBlocksPerSecond", 0.0)).doubleValue();

        this.arenaStats = new HashMap<>();
        Map<String, Map<String, Object>> arenaStatsMap = (Map<String, Map<String, Object>>) map.getOrDefault("arenaStats", new HashMap<>());
        for (Map.Entry<String, Map<String, Object>> entry : arenaStatsMap.entrySet()) {
            this.arenaStats.put(entry.getKey(), new ArenaStats(entry.getValue()));
        }
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("playerUUID", playerUUID.toString());
        map.put("playerName", playerName);
        map.put("totalSessions", totalSessions);
        map.put("totalCompletions", totalCompletions);
        map.put("totalFailures", totalFailures);
        map.put("totalPlayTimeMs", totalPlayTimeMs);
        map.put("elo", elo);
        map.put("eloGamesPlayed", eloGamesPlayed);
        map.put("bestTimeMs", bestTimeMs);
        map.put("averageTimeMs", averageTimeMs);
        map.put("recentTimes", recentTimes);
        map.put("totalStylePoints", totalStylePoints);
        map.put("consecutivePlacements", consecutivePlacements);
        map.put("clutchSaves", clutchSaves);
        map.put("perfectRuns", perfectRuns);
        map.put("totalBlocksPlaced", totalBlocksPlaced);
        map.put("totalBlocksWasted", totalBlocksWasted);
        map.put("placementAccuracy", placementAccuracy);
        map.put("averageBlocksPerSecond", averageBlocksPerSecond);
        map.put("peakBlocksPerSecond", peakBlocksPerSecond);

        Map<String, Map<String, Object>> arenaStatsMap = new HashMap<>();
        for (Map.Entry<String, ArenaStats> entry : arenaStats.entrySet()) {
            arenaStatsMap.put(entry.getKey(), entry.getValue().serialize());
        }
        map.put("arenaStats", arenaStatsMap);

        return map;
    }

    // === Session tracking ===

    public void startSession() {
        totalSessions++;
    }

    public void recordCompletion(long timeMs, String arenaName, SessionMetrics metrics) {
        totalCompletions++;
        totalPlayTimeMs += timeMs;

        // Update best time
        if (timeMs < bestTimeMs) {
            bestTimeMs = timeMs;
        }

        // Update recent times (keep last 10)
        recentTimes.add(timeMs);
        if (recentTimes.size() > 10) {
            recentTimes.remove(0);
        }

        // Recalculate average
        averageTimeMs = (long) recentTimes.stream().mapToLong(Long::longValue).average().orElse(0);

        // Update style points
        totalStylePoints += metrics.stylePoints;
        if (metrics.wasClutchSave) {
            clutchSaves++;
        }
        if (metrics.wasPerfectRun) {
            perfectRuns++;
        }

        // Update block stats
        totalBlocksPlaced += metrics.blocksPlaced;
        totalBlocksWasted += metrics.blocksWasted;
        if (totalBlocksPlaced > 0) {
            placementAccuracy = ((double) (totalBlocksPlaced - totalBlocksWasted) / totalBlocksPlaced) * 100;
        }

        // Update speed stats
        if (metrics.blocksPerSecond > peakBlocksPerSecond) {
            peakBlocksPerSecond = metrics.blocksPerSecond;
        }
        averageBlocksPerSecond = (averageBlocksPerSecond * (totalCompletions - 1) + metrics.blocksPerSecond) / totalCompletions;

        // Update per-arena stats
        ArenaStats stats = arenaStats.computeIfAbsent(arenaName, k -> new ArenaStats());
        stats.recordCompletion(timeMs, metrics);

        // Update ELO
        updateEloForCompletion(timeMs, arenaName);
    }

    public void recordFailure(long timeMs, String arenaName) {
        totalFailures++;
        totalPlayTimeMs += timeMs;

        ArenaStats stats = arenaStats.computeIfAbsent(arenaName, k -> new ArenaStats());
        stats.recordFailure();

        // Small ELO penalty for failure
        updateEloForFailure();
    }

    // === ELO System ===

    private void updateEloForCompletion(long timeMs, String arenaName) {
        eloGamesPlayed++;

        // K-factor decreases as you play more games (more stable rating)
        double kFactor = Math.max(16, 40 - (eloGamesPlayed * 0.5));

        // Calculate performance score based on time
        // Faster times = higher score, compared to average
        double performanceScore;
        if (averageTimeMs > 0 && timeMs > 0) {
            double timeRatio = (double) averageTimeMs / timeMs;
            performanceScore = Math.min(1.0, Math.max(0.0, (timeRatio - 0.5) / 1.0));
        } else {
            performanceScore = 0.5;
        }

        // ELO change
        double expectedScore = 0.5; // Neutral expectation
        double eloChange = kFactor * (performanceScore - expectedScore);

        // Bonus for style points
        eloChange += totalStylePoints * 0.1;

        elo = Math.max(100, elo + eloChange);
    }

    private void updateEloForFailure() {
        eloGamesPlayed++;
        double kFactor = Math.max(16, 40 - (eloGamesPlayed * 0.5));
        elo = Math.max(100, elo - (kFactor * 0.3)); // 30% of K-factor penalty
    }

    // === Getters ===

    public UUID getPlayerUUID() { return playerUUID; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String name) { this.playerName = name; }

    public int getTotalSessions() { return totalSessions; }
    public int getTotalCompletions() { return totalCompletions; }
    public int getTotalFailures() { return totalFailures; }
    public long getTotalPlayTimeMs() { return totalPlayTimeMs; }

    public double getElo() { return elo; }
    public int getEloGamesPlayed() { return eloGamesPlayed; }
    public String getEloRank() {
        if (elo >= 2000) return "§6§lGrandmaster";
        if (elo >= 1800) return "§5§lMaster";
        if (elo >= 1600) return "§d§lDiamond";
        if (elo >= 1400) return "§b§lPlatinum";
        if (elo >= 1200) return "§e§lGold";
        if (elo >= 1000) return "§7§lSilver";
        if (elo >= 800) return "§c§lBronze";
        return "§8§lUnranked";
    }

    public long getBestTimeMs() { return bestTimeMs == Long.MAX_VALUE ? 0 : bestTimeMs; }
    public long getAverageTimeMs() { return averageTimeMs; }
    public List<Long> getRecentTimes() { return new ArrayList<>(recentTimes); }

    public int getTotalStylePoints() { return totalStylePoints; }
    public int getClutchSaves() { return clutchSaves; }
    public int getPerfectRuns() { return perfectRuns; }

    public int getTotalBlocksPlaced() { return totalBlocksPlaced; }
    public double getPlacementAccuracy() { return placementAccuracy; }
    public double getAverageBlocksPerSecond() { return averageBlocksPerSecond; }
    public double getPeakBlocksPerSecond() { return peakBlocksPerSecond; }

    public double getCompletionRate() {
        if (totalSessions == 0) return 0;
        return ((double) totalCompletions / totalSessions) * 100;
    }

    public ArenaStats getArenaStats(String arenaName) {
        return arenaStats.get(arenaName);
    }

    public Map<String, ArenaStats> getAllArenaStats() {
        return new HashMap<>(arenaStats);
    }

    /**
     * Metrics collected during a single session
     */
    public static class SessionMetrics {
        public int blocksPlaced = 0;
        public int blocksWasted = 0;
        public double blocksPerSecond = 0;
        public int stylePoints = 0;
        public boolean wasClutchSave = false;
        public boolean wasPerfectRun = true;
        public int consecutivePlacements = 0;
        public int maxConsecutivePlacements = 0;

        public void addBlockPlaced(boolean wasUseful) {
            blocksPlaced++;
            if (!wasUseful) {
                blocksWasted++;
                wasPerfectRun = false;
            }
        }

        public void recordConsecutivePlacement() {
            consecutivePlacements++;
            if (consecutivePlacements > maxConsecutivePlacements) {
                maxConsecutivePlacements = consecutivePlacements;
            }
            // Award style points for streaks
            if (consecutivePlacements >= 5) {
                stylePoints += 1;
            }
            if (consecutivePlacements >= 10) {
                stylePoints += 2;
            }
        }

        public void breakStreak() {
            consecutivePlacements = 0;
        }

        public void recordClutchSave() {
            wasClutchSave = true;
            stylePoints += 5;
        }

        public void calculateFinalStats(long durationMs) {
            if (durationMs > 0) {
                blocksPerSecond = (double) blocksPlaced / (durationMs / 1000.0);
            }

            // Bonus style points for speed
            if (blocksPerSecond > 3.0) {
                stylePoints += 3;
            } else if (blocksPerSecond > 2.0) {
                stylePoints += 1;
            }

            // Bonus for accuracy
            if (blocksPlaced > 0) {
                double accuracy = ((double) (blocksPlaced - blocksWasted) / blocksPlaced) * 100;
                if (accuracy >= 95) {
                    stylePoints += 5;
                } else if (accuracy >= 90) {
                    stylePoints += 2;
                }
            }

            // Bonus for perfect run
            if (wasPerfectRun && blocksPlaced > 10) {
                stylePoints += 10;
            }
        }
    }

    /**
     * Statistics for a specific arena
     */
    public static class ArenaStats implements ConfigurationSerializable {
        private int completions;
        private int failures;
        private long bestTimeMs;
        private long averageTimeMs;
        private int totalStylePoints;
        private List<Long> recentTimes;

        public ArenaStats() {
            this.completions = 0;
            this.failures = 0;
            this.bestTimeMs = Long.MAX_VALUE;
            this.averageTimeMs = 0;
            this.totalStylePoints = 0;
            this.recentTimes = new ArrayList<>();
        }

        @SuppressWarnings("unchecked")
        public ArenaStats(Map<String, Object> map) {
            this.completions = (Integer) map.getOrDefault("completions", 0);
            this.failures = (Integer) map.getOrDefault("failures", 0);
            this.bestTimeMs = ((Number) map.getOrDefault("bestTimeMs", Long.MAX_VALUE)).longValue();
            this.averageTimeMs = ((Number) map.getOrDefault("averageTimeMs", 0L)).longValue();
            this.totalStylePoints = (Integer) map.getOrDefault("totalStylePoints", 0);

            List<Number> times = (List<Number>) map.getOrDefault("recentTimes", new ArrayList<>());
            this.recentTimes = new ArrayList<>();
            for (Number time : times) {
                this.recentTimes.add(time.longValue());
            }
        }

        @Override
        public Map<String, Object> serialize() {
            Map<String, Object> map = new HashMap<>();
            map.put("completions", completions);
            map.put("failures", failures);
            map.put("bestTimeMs", bestTimeMs);
            map.put("averageTimeMs", averageTimeMs);
            map.put("totalStylePoints", totalStylePoints);
            map.put("recentTimes", recentTimes);
            return map;
        }

        public void recordCompletion(long timeMs, SessionMetrics metrics) {
            completions++;

            if (timeMs < bestTimeMs) {
                bestTimeMs = timeMs;
            }

            recentTimes.add(timeMs);
            if (recentTimes.size() > 10) {
                recentTimes.remove(0);
            }

            averageTimeMs = (long) recentTimes.stream().mapToLong(Long::longValue).average().orElse(0);
            totalStylePoints += metrics.stylePoints;
        }

        public void recordFailure() {
            failures++;
        }

        public int getCompletions() { return completions; }
        public int getFailures() { return failures; }
        public long getBestTimeMs() { return bestTimeMs == Long.MAX_VALUE ? 0 : bestTimeMs; }
        public long getAverageTimeMs() { return averageTimeMs; }
        public int getTotalStylePoints() { return totalStylePoints; }

        public double getCompletionRate() {
            int total = completions + failures;
            if (total == 0) return 0;
            return ((double) completions / total) * 100;
        }
    }
}
