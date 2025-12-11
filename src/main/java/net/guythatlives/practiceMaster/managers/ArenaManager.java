package net.guythatlives.practiceMaster.managers;

import net.guythatlives.practiceMaster.PracticeMaster;
import net.guythatlives.practiceMaster.arena.Arena;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ArenaManager {

    private final PracticeMaster plugin;
    private final Map<String, Arena> arenas;
    private final File arenasFile;

    public ArenaManager(PracticeMaster plugin) {
        this.plugin = plugin;
        this.arenas = new HashMap<>();
        this.arenasFile = new File(plugin.getDataFolder(), "arenas.yml");
    }

    public void createArena(Arena arena) {
        arenas.put(arena.getName().toLowerCase(), arena);
        saveArenas();
    }

    public void deleteArena(String name) {
        arenas.remove(name.toLowerCase());
        saveArenas();
    }

    public Arena getArena(String name) {
        return arenas.get(name.toLowerCase());
    }

    public Map<String, Arena> getArenas() {
        return new HashMap<>(arenas);
    }

    public boolean arenaExists(String name) {
        return arenas.containsKey(name.toLowerCase());
    }

    public void saveArenas() {
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<String, Arena> entry : arenas.entrySet()) {
            config.set("arenas." + entry.getKey(), entry.getValue().serialize());
        }

        try {
            config.save(arenasFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save arenas: " + e.getMessage());
        }
    }

    public void loadArenas() {
        arenas.clear(); // Clear existing arenas before loading

        if (!arenasFile.exists()) {
            try {
                arenasFile.createNewFile();
                plugin.getLogger().info("Created new arenas.yml file");
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create arenas file: " + e.getMessage());
                return;
            }
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(arenasFile);

        if (config.getConfigurationSection("arenas") == null) {
            plugin.getLogger().info("No arenas found in arenas.yml");
            return;
        }

        plugin.getLogger().info("Loading arenas from arenas.yml...");

        for (String key : config.getConfigurationSection("arenas").getKeys(false)) {
            try {
                // Get the configuration section for this arena
                org.bukkit.configuration.ConfigurationSection arenaSection = config.getConfigurationSection("arenas." + key);
                if (arenaSection == null) {
                    plugin.getLogger().warning("Arena '" + key + "' has no data!");
                    continue;
                }

                // Convert ConfigurationSection to Map
                Map<String, Object> data = new HashMap<>();
                for (String dataKey : arenaSection.getKeys(true)) {
                    data.put(dataKey, arenaSection.get(dataKey));
                }

                Arena arena = new Arena(data);
                arenas.put(key.toLowerCase(), arena);

                // Log arena status
                String status = arena.isComplete() ? "COMPLETE" : "INCOMPLETE";
                plugin.getLogger().info("  - Loaded arena: " + key + " [" + status + "]");

                if (!arena.isComplete()) {
                    plugin.getLogger().warning("    Arena '" + key + "' is incomplete. Missing components:");
                    if (arena.getCorner1() == null) plugin.getLogger().warning("      - corner1");
                    if (arena.getCorner2() == null) plugin.getLogger().warning("      - corner2");
                    if (arena.getSpawnLocation() == null) plugin.getLogger().warning("      - spawnLocation");
                    if (arena.getBaseLocation() == null) plugin.getLogger().warning("      - baseLocation");
                    if (arena.getSavedBlocks().isEmpty()) plugin.getLogger().warning("      - savedBlocks (run /practice arena save " + key + ")");
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load arena '" + key + "': " + e.getMessage());
                e.printStackTrace();
            }
        }

        plugin.getLogger().info("Loaded " + arenas.size() + " arena(s) total");
    }
}