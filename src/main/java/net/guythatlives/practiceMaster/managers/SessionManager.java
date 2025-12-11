package net.guythatlives.practiceMaster.managers;

import net.guythatlives.practiceMaster.PracticeMaster;
import net.guythatlives.practiceMaster.arena.Arena;
import net.guythatlives.practiceMaster.session.PracticeSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SessionManager {

    private final PracticeMaster plugin;
    private final Map<UUID, PracticeSession> activeSessions;

    public SessionManager(PracticeMaster plugin) {
        this.plugin = plugin;
        this.activeSessions = new HashMap<>();
    }

    public void startSession(Player player, Arena arena, boolean timerEnabled) {
        // End existing session if any
        if (isInSession(player)) {
            endSession(player);
        }

        // Get a random play location (not the base location)
        Location playLocation = arena.getRandomPlayLocation();
        if (playLocation == null) {
            player.sendMessage("§cNo play locations configured for this arena!");
            player.sendMessage("§7Add locations with: §e/practice arena addplaylocation " + arena.getName());
            return;
        }

        PracticeSession session = new PracticeSession(player, arena, playLocation, timerEnabled);
        activeSessions.put(player.getUniqueId(), session);
    }

    public void endSession(Player player) {
        PracticeSession session = activeSessions.get(player.getUniqueId());
        if (session != null) {
            session.endSession();
        }
    }

    public void removeSession(UUID uuid) {
        activeSessions.remove(uuid);
    }

    public boolean isInSession(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    public PracticeSession getSession(Player player) {
        return activeSessions.get(player.getUniqueId());
    }

    public void endAllSessions() {
        for (PracticeSession session : activeSessions.values()) {
            session.endSession();
        }
        activeSessions.clear();
    }
}