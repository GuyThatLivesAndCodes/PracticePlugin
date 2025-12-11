package net.guythatlives.practiceMaster.arena;

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
    private boolean usesSchematic; // Whether this arena uses schematic or legacy block storage
    private List<ArenaBlock> savedBlocks; // Legacy block storage (fallback)
    private ArenaEvent arenaEvent;
    private String customEventName; // For custom events from config
    private int defaultTimerSeconds;
    private boolean timerEnabled;
    private List<ItemStack> kitItems;
    private ItemStack[] kitArmor;

    public Arena(String name) {
        this.name = name;
        this.playLocations = new ArrayList<>();
        this.usesSchematic = false;
        this.savedBlocks = new ArrayList<>();
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
        this.usesSchematic = (Boolean) map.getOrDefault("usesSchematic", false);

        // Load saved blocks (legacy fallback)
        List<Map<String, Object>> blockMaps = (List<Map<String, Object>>) map.getOrDefault("savedBlocks", new ArrayList<>());
        this.savedBlocks = new ArrayList<>();
        for (Map<String, Object> blockMap : blockMaps) {
            this.savedBlocks.add(new ArenaBlock(blockMap));
        }

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
        map.put("usesSchematic", usesSchematic);

        // Only save blocks if not using schematic
        if (!usesSchematic) {
            List<Map<String, Object>> blockMaps = new ArrayList<>();
            for (ArenaBlock block : savedBlocks) {
                blockMaps.add(block.serialize());
            }
            map.put("savedBlocks", blockMaps);
        }

        map.put("arenaEvent", arenaEvent.name());
        map.put("customEventName", customEventName);
        map.put("defaultTimerSeconds", defaultTimerSeconds);
        map.put("timerEnabled", timerEnabled);
        map.put("kitItems", kitItems);
        map.put("kitArmor", kitArmor != null ? Arrays.asList(kitArmor) : new ArrayList<>());
        return map;
    }

    public SaveResult saveArenaStructure(boolean forceYAML) {
        if (corner1 == null || corner2 == null || baseLocation == null) {
            return new SaveResult(false, "Missing corners or base location", 0, null);
        }

        // Try to use schematic system first (unless forced to YAML)
        if (!forceYAML && net.guythatlives.practiceMaster.PracticeMaster.getInstance().getSchematicManager().isWorldEditAvailable()) {
            net.guythatlives.practiceMaster.managers.SchematicManager.SaveResult result =
                    net.guythatlives.practiceMaster.PracticeMaster.getInstance()
                            .getSchematicManager()
                            .saveSchematic(name, corner1, corner2);

            if (result.success) {
                this.usesSchematic = true;
                this.savedBlocks.clear();
                return new SaveResult(true, "Schematic", result.blockCount, result.filePath, result.dimensions);
            }
        }

        // Fallback to legacy block-by-block saving (or forced)
        this.usesSchematic = false;
        savedBlocks.clear();

        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
        int maxY = Math.max(corner1.getBlockY(), corner2.getBlockY());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());

        int blockCount = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Location loc = new Location(corner1.getWorld(), x, y, z);
                    Block block = loc.getBlock();

                    int relX = x - baseLocation.getBlockX();
                    int relY = y - baseLocation.getBlockY();
                    int relZ = z - baseLocation.getBlockZ();

                    savedBlocks.add(new ArenaBlock(relX, relY, relZ, block.getType(), block.getBlockData()));
                    blockCount++;
                }
            }
        }

        String dimensions = (maxX - minX + 1) + "x" + (maxY - minY + 1) + "x" + (maxZ - minZ + 1);
        String yamlPath = net.guythatlives.practiceMaster.PracticeMaster.getInstance()
                .getDataFolder().getAbsolutePath() + "/arenas.yml";

        return new SaveResult(true, forceYAML ? "YAML (Forced)" : "YAML (Fallback)", blockCount, yamlPath, dimensions);
    }

    public void loadArenaAt(Location playLocation) {
        // Use schematic if available
        if (usesSchematic) {
            net.guythatlives.practiceMaster.PracticeMaster.getInstance()
                    .getSchematicManager()
                    .loadSchematic(name, playLocation);
            return;
        }

        // Fallback to legacy block loading
        if (savedBlocks.isEmpty()) {
            return;
        }

        for (ArenaBlock arenaBlock : savedBlocks) {
            Location targetLoc = playLocation.clone().add(
                    arenaBlock.getRelX(),
                    arenaBlock.getRelY(),
                    arenaBlock.getRelZ()
            );
            Block block = targetLoc.getBlock();
            block.setType(arenaBlock.getMaterial());

            if (arenaBlock.getBlockData() != null) {
                try {
                    block.setBlockData(arenaBlock.getBlockData());
                } catch (Exception e) {
                    // Silently ignore
                }
            }
        }
    }

    public void loadArenaAtAllLocations() {
        for (Location playLoc : playLocations) {
            loadArenaAt(playLoc);
        }
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
        boolean hasStructure = usesSchematic ?
                net.guythatlives.practiceMaster.PracticeMaster.getInstance().getSchematicManager().schematicExists(name) :
                !savedBlocks.isEmpty();

        return corner1 != null && corner2 != null && spawnLocation != null &&
                baseLocation != null && hasStructure;
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
    public List<ArenaBlock> getSavedBlocks() { return savedBlocks; }
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

    /**
     * Result class for save operations
     */
    public static class SaveResult {
        public final boolean success;
        public final String method;
        public final int blockCount;
        public final String filePath;
        public final String dimensions;

        public SaveResult(boolean success, String method, int blockCount, String filePath) {
            this.success = success;
            this.method = method;
            this.blockCount = blockCount;
            this.filePath = filePath;
            this.dimensions = null;
        }

        public SaveResult(boolean success, String method, int blockCount, String filePath, String dimensions) {
            this.success = success;
            this.method = method;
            this.blockCount = blockCount;
            this.filePath = filePath;
            this.dimensions = dimensions;
        }
    }

    public static class ArenaBlock implements ConfigurationSerializable {
        private final int relX, relY, relZ;
        private final Material material;
        private final String blockDataString;

        public ArenaBlock(int relX, int relY, int relZ, Material material, BlockData blockData) {
            this.relX = relX;
            this.relY = relY;
            this.relZ = relZ;
            this.material = material;
            this.blockDataString = blockData.getAsString();
        }

        @SuppressWarnings("unchecked")
        public ArenaBlock(Map<String, Object> map) {
            this.relX = (Integer) map.get("relX");
            this.relY = (Integer) map.get("relY");
            this.relZ = (Integer) map.get("relZ");
            this.material = Material.valueOf((String) map.get("material"));
            this.blockDataString = (String) map.getOrDefault("blockData", "");
        }

        @Override
        public Map<String, Object> serialize() {
            Map<String, Object> map = new HashMap<>();
            map.put("relX", relX);
            map.put("relY", relY);
            map.put("relZ", relZ);
            map.put("material", material.name());
            map.put("blockData", blockDataString);
            return map;
        }

        public int getRelX() { return relX; }
        public int getRelY() { return relY; }
        public int getRelZ() { return relZ; }
        public Material getMaterial() { return material; }
        public BlockData getBlockData() {
            try {
                return Bukkit.createBlockData(blockDataString);
            } catch (Exception e) {
                return material.createBlockData();
            }
        }
    }
}