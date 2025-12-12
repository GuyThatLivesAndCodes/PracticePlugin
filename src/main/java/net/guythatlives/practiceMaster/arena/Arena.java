package net.guythatlives.practiceMaster.arena;

import net.guythatlives.practiceMaster.PracticeMaster;
import net.guythatlives.practiceMaster.managers.SchematicManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class Arena implements ConfigurationSerializable {

    private final String name;
    private Location corner1;
    private Location corner2;
    private Location spawnLocation;
    private Location baseLocation;
    private List<Location> playLocations;
    private int activeSaveVersion; // Which save version is currently active
    private ArenaEvent arenaEvent;
    private String customEventName;
    private int defaultTimerSeconds;
    private boolean timerEnabled;
    private List<ItemStack> kitItems;
    private ItemStack[] kitArmor;

    public Arena(String name) {
        this.name = name;
        this.playLocations = new ArrayList<>();
        this.activeSaveVersion = 0; // 0 means use latest
        this.arenaEvent = ArenaEvent.NONE;
        this.defaultTimerSeconds = 60;
        this.timerEnabled = false;
        this.kitItems = new ArrayList<>();
        this.kitArmor = new ItemStack[4];
    }

    public Arena(String name, Location corner1, Location corner2, Location spawnLocation) {
        this(name);
        this.corner1 = corner1;
        this.corner2 = corner2;
        this.spawnLocation = spawnLocation;
    }

    @SuppressWarnings("unchecked")
    public Arena(Map<String, Object> map) {
        this.name = (String) map.get("name");
        this.corner1 = (Location) map.get("corner1");
        this.corner2 = (Location) map.get("corner2");
        this.spawnLocation = (Location) map.get("spawnLocation");
        this.baseLocation = (Location) map.get("baseLocation");
        this.playLocations = (List<Location>) map.getOrDefault("playLocations", new ArrayList<>());
        this.activeSaveVersion = (Integer) map.getOrDefault("activeSaveVersion", 0);

        this.arenaEvent = ArenaEvent.valueOf((String) map.getOrDefault("arenaEvent", "NONE"));
        this.customEventName = (String) map.get("customEventName");
        this.defaultTimerSeconds = (Integer) map.getOrDefault("defaultTimerSeconds", 60);
        this.timerEnabled = (Boolean) map.getOrDefault("timerEnabled", false);

        List<?> kitItemsList = (List<?>) map.get("kitItems");
        this.kitItems = new ArrayList<>();
        if (kitItemsList != null) {
            for (Object obj : kitItemsList) {
                if (obj instanceof ItemStack) {
                    this.kitItems.add((ItemStack) obj);
                }
            }
        }

        List<?> kitArmorList = (List<?>) map.get("kitArmor");
        this.kitArmor = new ItemStack[4];
        if (kitArmorList != null) {
            for (int i = 0; i < Math.min(kitArmorList.size(), 4); i++) {
                if (kitArmorList.get(i) instanceof ItemStack) {
                    this.kitArmor[i] = (ItemStack) kitArmorList.get(i);
                }
            }
        }
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("corner1", corner1);
        map.put("corner2", corner2);
        map.put("spawnLocation", spawnLocation);
        map.put("baseLocation", baseLocation);
        map.put("playLocations", playLocations);
        map.put("activeSaveVersion", activeSaveVersion);
        map.put("arenaEvent", arenaEvent.name());
        map.put("customEventName", customEventName);
        map.put("defaultTimerSeconds", defaultTimerSeconds);
        map.put("timerEnabled", timerEnabled);
        map.put("kitItems", kitItems);
        map.put("kitArmor", kitArmor != null ? Arrays.asList(kitArmor) : new ArrayList<>());
        return map;
    }

    /**
     * Saves the arena structure with versioning - tries WorldEdit first, falls back to YAML
     */
    public SchematicManager.SaveResult saveArenaStructure(boolean forceYAML) {
        if (corner1 == null || corner2 == null || baseLocation == null) {
            return new SchematicManager.SaveResult(false, "Missing corners or base location", 0, null, null, false);
        }

        SchematicManager.SaveResult result = PracticeMaster.getInstance()
                .getSchematicManager()
                .saveArenaStructure(name, corner1, corner2, baseLocation, forceYAML);

        if (result.success && result.verified) {
            // Update active version to the new save
            this.activeSaveVersion = result.saveIndex;
        }

        return result;
    }

    /**
     * Loads the arena at a specific location
     */
    public void loadArenaAt(Location playLocation) {
        loadArenaAt(playLocation, activeSaveVersion);
    }

    /**
     * Loads a specific version of the arena at a location
     */
    public void loadArenaAt(Location playLocation, int version) {
        SchematicManager sm = PracticeMaster.getInstance().getSchematicManager();

        // Get the save version to check its type
        SchematicManager.SaveVersion sv = null;
        if (version <= 0) {
            sv = sm.getActiveSaveVersion(name);
        } else {
            for (SchematicManager.SaveVersion v : sm.getSaveVersions(name)) {
                if (v.index == version) {
                    sv = v;
                    break;
                }
            }
        }

        Location pasteLocation = playLocation.clone();

        // For schematics, calculate offset from min corner to base location
        // Schematics are saved from min corner, but we want base to end up at playLocation
        if (sv != null && sv.type.equals("Schematic") && corner1 != null && corner2 != null && baseLocation != null) {
            int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
            int minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
            int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());

            // Offset from min corner to base
            int offsetX = baseLocation.getBlockX() - minX;
            int offsetY = baseLocation.getBlockY() - minY;
            int offsetZ = baseLocation.getBlockZ() - minZ;

            // Subtract offset so base ends up at playLocation
            pasteLocation.subtract(offsetX, offsetY, offsetZ);
        }
        // For YAML saves, no adjustment needed - coordinates are already relative to base

        sm.loadArenaAt(name, pasteLocation, version);
    }

    /**
     * Loads the arena at all play locations
     */
    public void loadArenaAtAllLocations() {
        for (Location playLoc : playLocations) {
            loadArenaAt(playLoc);
        }
    }

    /**
     * Gets all available save versions for this arena
     */
    public List<SchematicManager.SaveVersion> getSaveVersions() {
        return PracticeMaster.getInstance()
                .getSchematicManager()
                .getSaveVersions(name);
    }

    /**
     * Sets the active save version to use when loading
     */
    public void setActiveSaveVersion(int version) {
        this.activeSaveVersion = version;
    }

    /**
     * Gets the current active save version
     */
    public int getActiveSaveVersion() {
        return activeSaveVersion;
    }

    /**
     * Checks if the arena has any saved structure
     */
    public boolean hasSavedStructure() {
        return PracticeMaster.getInstance()
                .getSchematicManager()
                .schematicExists(name);
    }

    /**
     * Legacy method for backward compatibility - returns empty list
     * Use hasSavedStructure() instead
     */
    @Deprecated
    public List<Object> getSavedBlocks() {
        // For backward compatibility, return empty list if structure exists in new format
        return hasSavedStructure() ? Collections.emptyList() : Collections.emptyList();
    }

    public Location getSpawnForPlayLocation(Location playLocation) {
        if (spawnLocation == null || baseLocation == null) {
            return playLocation.clone();
        }

        int offsetX = spawnLocation.getBlockX() - baseLocation.getBlockX();
        int offsetY = spawnLocation.getBlockY() - baseLocation.getBlockY();
        int offsetZ = spawnLocation.getBlockZ() - baseLocation.getBlockZ();

        Location spawn = playLocation.clone().add(offsetX, offsetY, offsetZ);
        spawn.setYaw(spawnLocation.getYaw());
        spawn.setPitch(spawnLocation.getPitch());

        return spawn;
    }

    public Location[] getBoundsForPlayLocation(Location playLocation) {
        if (corner1 == null || corner2 == null || baseLocation == null) {
            return null;
        }

        int corner1OffsetX = corner1.getBlockX() - baseLocation.getBlockX();
        int corner1OffsetY = corner1.getBlockY() - baseLocation.getBlockY();
        int corner1OffsetZ = corner1.getBlockZ() - baseLocation.getBlockZ();

        int corner2OffsetX = corner2.getBlockX() - baseLocation.getBlockX();
        int corner2OffsetY = corner2.getBlockY() - baseLocation.getBlockY();
        int corner2OffsetZ = corner2.getBlockZ() - baseLocation.getBlockZ();

        Location relativeCorner1 = playLocation.clone().add(corner1OffsetX, corner1OffsetY, corner1OffsetZ);
        Location relativeCorner2 = playLocation.clone().add(corner2OffsetX, corner2OffsetY, corner2OffsetZ);

        return new Location[]{relativeCorner1, relativeCorner2};
    }

    public boolean isInBoundsAt(Location playerLoc, Location playLocation) {
        Location[] bounds = getBoundsForPlayLocation(playLocation);
        if (bounds == null) return false;

        if (!playerLoc.getWorld().equals(playLocation.getWorld())) return false;

        double minX = Math.min(bounds[0].getX(), bounds[1].getX());
        double maxX = Math.max(bounds[0].getX(), bounds[1].getX());
        double minY = Math.min(bounds[0].getY(), bounds[1].getY());
        double maxY = Math.max(bounds[0].getY(), bounds[1].getY());
        double minZ = Math.min(bounds[0].getZ(), bounds[1].getZ());
        double maxZ = Math.max(bounds[0].getZ(), bounds[1].getZ());

        return playerLoc.getX() >= minX && playerLoc.getX() <= maxX &&
                playerLoc.getY() >= minY && playerLoc.getY() <= maxY &&
                playerLoc.getZ() >= minZ && playerLoc.getZ() <= maxZ;
    }

    public Location getRandomPlayLocation() {
        if (playLocations.isEmpty()) {
            return null;
        }
        return playLocations.get(new Random().nextInt(playLocations.size())).clone();
    }

    public void addPlayLocation(Location location) {
        playLocations.add(location);
    }

    public void removePlayLocation(Location location) {
        playLocations.removeIf(loc ->
                loc.getWorld().equals(location.getWorld()) &&
                        loc.getBlockX() == location.getBlockX() &&
                        loc.getBlockY() == location.getBlockY() &&
                        loc.getBlockZ() == location.getBlockZ()
        );
    }

    public boolean isComplete() {
        return corner1 != null && corner2 != null && spawnLocation != null &&
                baseLocation != null && hasSavedStructure();
    }

    // Getters and Setters
    public String getName() { return name; }
    public Location getCorner1() { return corner1; }
    public void setCorner1(Location corner1) { this.corner1 = corner1; }
    public Location getCorner2() { return corner2; }
    public void setCorner2(Location corner2) { this.corner2 = corner2; }
    public Location getSpawnLocation() { return spawnLocation; }
    public void setSpawnLocation(Location spawnLocation) { this.spawnLocation = spawnLocation; }
    public Location getBaseLocation() { return baseLocation; }
    public void setBaseLocation(Location baseLocation) { this.baseLocation = baseLocation; }
    public List<Location> getPlayLocations() { return playLocations; }
    public ArenaEvent getArenaEvent() { return arenaEvent; }
    public void setArenaEvent(ArenaEvent arenaEvent) { this.arenaEvent = arenaEvent; }
    public String getCustomEventName() { return customEventName; }
    public void setCustomEventName(String customEventName) { this.customEventName = customEventName; }
    public int getDefaultTimerSeconds() { return defaultTimerSeconds; }
    public void setDefaultTimerSeconds(int defaultTimerSeconds) { this.defaultTimerSeconds = defaultTimerSeconds; }
    public boolean isTimerEnabled() { return timerEnabled; }
    public void setTimerEnabled(boolean timerEnabled) { this.timerEnabled = timerEnabled; }
    public List<ItemStack> getKitItems() { return kitItems; }
    public void setKitItems(List<ItemStack> kitItems) { this.kitItems = kitItems; }
    public ItemStack[] getKitArmor() { return kitArmor; }
    public void setKitArmor(ItemStack[] kitArmor) { this.kitArmor = kitArmor; }
}
