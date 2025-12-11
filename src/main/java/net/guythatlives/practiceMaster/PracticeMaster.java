package net.guythatlives.practiceMaster;

import net.guythatlives.practiceMaster.commands.PracticeCommand;
import net.guythatlives.practiceMaster.listeners.PlayerListener;
import net.guythatlives.practiceMaster.managers.ArenaManager;
import net.guythatlives.practiceMaster.managers.SessionManager;
import net.guythatlives.practiceMaster.managers.InventoryManager;
import net.guythatlives.practiceMaster.managers.SchematicManager;
import org.bukkit.plugin.java.JavaPlugin;

public class PracticeMaster extends JavaPlugin {

    private static PracticeMaster instance;
    private ArenaManager arenaManager;
    private SessionManager sessionManager;
    private InventoryManager inventoryManager;
    private SchematicManager schematicManager;

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

        // Register commands
        getCommand("practice").setExecutor(new PracticeCommand(this));
        getCommand("practice").setTabCompleter(new PracticeCommand(this));

        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        // Load arenas
        arenaManager.loadArenas();

        getLogger().info("PracticeMaster has been enabled!");
        getLogger().info("Loaded " + arenaManager.getArenas().size() + " arena(s)");
    }

    @Override
    public void onDisable() {
        // Save all arenas
        if (arenaManager != null) {
            arenaManager.saveArenas();
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
}