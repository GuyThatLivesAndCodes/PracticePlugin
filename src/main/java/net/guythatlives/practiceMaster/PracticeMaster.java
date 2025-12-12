package net.guythatlives.practiceMaster;

import net.guythatlives.practiceMaster.commands.PracticeCommand;
import net.guythatlives.practiceMaster.listeners.PlayerListener;
import net.guythatlives.practiceMaster.managers.ArenaManager;
import net.guythatlives.practiceMaster.managers.SessionManager;
import net.guythatlives.practiceMaster.managers.InventoryManager;
import net.guythatlives.practiceMaster.managers.SchematicManager;
import net.guythatlives.practiceMaster.stats.StatsManager;
import org.bukkit.plugin.java.JavaPlugin;

public class PracticeMaster extends JavaPlugin {

    private static PracticeMaster instance;
    private ArenaManager arenaManager;
    private SessionManager sessionManager;
    private InventoryManager inventoryManager;
    private SchematicManager schematicManager;
    private StatsManager statsManager;

    @Override
    public void onEnable() {
        instance = this;

        // Create data folder
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Save default config
        saveDefaultConfig();

        // Initialize managers
        schematicManager = new SchematicManager(this);
        arenaManager = new ArenaManager(this);
        sessionManager = new SessionManager(this);
        inventoryManager = new InventoryManager(this);
        statsManager = new StatsManager(this);

        // Register commands
        PracticeCommand cmd = new PracticeCommand(this);
        getCommand("practice").setExecutor(cmd);
        getCommand("practice").setTabCompleter(cmd);

        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        // Load arenas
        arenaManager.loadArenas();

        getLogger().info("PracticeMaster has been enabled!");
        getLogger().info("Loaded " + arenaManager.getArenas().size() + " arena(s)");

        if (schematicManager.isWorldEditAvailable()) {
            getLogger().info("WorldEdit integration: ENABLED");
        } else {
            getLogger().warning("WorldEdit integration: DISABLED (using YAML fallback)");
        }
    }

    @Override
    public void onDisable() {
        // Save all arenas
        if (arenaManager != null) {
            arenaManager.saveArenas();
        }

        // Save all player stats
        if (statsManager != null) {
            statsManager.saveAllStats();
        }

        // End all sessions
        if (sessionManager != null) {
            sessionManager.endAllSessions();
        }

        getLogger().info("PracticeMaster has been disabled!");
    }

    public static PracticeMaster getInstance() {
        return instance;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public InventoryManager getInventoryManager() {
        return inventoryManager;
    }

    public SchematicManager getSchematicManager() {
        return schematicManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }
}
