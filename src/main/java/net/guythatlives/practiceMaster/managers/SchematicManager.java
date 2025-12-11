package net.guythatlives.practiceMaster.managers;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.*;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import net.guythatlives.practiceMaster.PracticeMaster;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class SchematicManager {

    private final PracticeMaster plugin;
    private final File arenasFolder;
    private final boolean worldEditAvailable;
    private final ClipboardFormat format;

    public SchematicManager(PracticeMaster plugin) {
        this.plugin = plugin;
        this.arenasFolder = new File(plugin.getDataFolder(), "arenas");

        // Create arenas folder
        if (!arenasFolder.exists()) {
            arenasFolder.mkdirs();
        }

        // Check if WorldEdit/FAWE is available
        this.worldEditAvailable = plugin.getServer().getPluginManager().getPlugin("WorldEdit") != null ||
                plugin.getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") != null;

        if (worldEditAvailable) {
            this.format = BuiltInClipboardFormat.SPONGE_SCHEMATIC;
            plugin.getLogger().info("WorldEdit detected! Using schematic-based arena saving.");
        } else {
            this.format = null;
            plugin.getLogger().warning("WorldEdit not found! Using legacy YAML block storage.");
        }
    }

    /**
     * Gets the folder for a specific arena
     */
    public File getArenaFolder(String arenaName) {
        File folder = new File(arenasFolder, arenaName.toLowerCase());
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    /**
     * Gets the next available save index for an arena
     */
    public int getNextSaveIndex(String arenaName) {
        File arenaFolder = getArenaFolder(arenaName);
        int maxIndex = 0;

        File[] files = arenaFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.startsWith("saved-build-")) {
                    try {
                        String indexStr;
                        if (file.isDirectory()) {
                            indexStr = name.substring("saved-build-".length());
                        } else if (name.endsWith(".schem")) {
                            indexStr = name.substring("saved-build-".length(), name.length() - ".schem".length());
                        } else {
                            continue;
                        }
                        int index = Integer.parseInt(indexStr);
                        maxIndex = Math.max(maxIndex, index);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        return maxIndex + 1;
    }

    /**
     * Gets all available save versions for an arena
     */
    public List<SaveVersion> getSaveVersions(String arenaName) {
        List<SaveVersion> versions = new ArrayList<>();
        File arenaFolder = getArenaFolder(arenaName);

        File[] files = arenaFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.startsWith("saved-build-")) {
                    try {
                        int index;
                        String type;
                        long timestamp;

                        if (file.isDirectory()) {
                            index = Integer.parseInt(name.substring("saved-build-".length()));
                            type = "YAML";
                            // Check for metadata file
                            File metaFile = new File(file, "metadata.yml");
                            timestamp = metaFile.exists() ? metaFile.lastModified() : file.lastModified();
                        } else if (name.endsWith(".schem")) {
                            index = Integer.parseInt(name.substring("saved-build-".length(), name.length() - ".schem".length()));
                            type = "Schematic";
                            timestamp = file.lastModified();
                        } else {
                            continue;
                        }

                        versions.add(new SaveVersion(index, type, timestamp, file));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // Sort by index
        versions.sort(Comparator.comparingInt(v -> v.index));
        return versions;
    }

    /**
     * Gets the active (latest) save version for an arena
     */
    public SaveVersion getActiveSaveVersion(String arenaName) {
        List<SaveVersion> versions = getSaveVersions(arenaName);
        return versions.isEmpty() ? null : versions.get(versions.size() - 1);
    }

    /**
     * Saves an arena region with versioning - tries WorldEdit first, falls back to YAML
     */
    public SaveResult saveArenaStructure(String arenaName, Location corner1, Location corner2, Location baseLocation, boolean forceYAML) {
        int saveIndex = getNextSaveIndex(arenaName);

        // Try WorldEdit first (unless forced to YAML)
        if (!forceYAML && worldEditAvailable) {
            SaveResult result = saveSchematic(arenaName, corner1, corner2, saveIndex);

            // Verify the save worked
            if (result.success && result.verified) {
                plugin.getLogger().info("Arena '" + arenaName + "' saved successfully as schematic (version " + saveIndex + ")");
                return result;
            } else if (result.success && !result.verified) {
                plugin.getLogger().warning("Schematic save verification failed for '" + arenaName + "', falling back to YAML");
                // Delete the potentially corrupt schematic
                deleteSpecificVersion(arenaName, saveIndex);
            }
        }

        // Fallback to YAML block-by-block saving
        return saveYAML(arenaName, corner1, corner2, baseLocation, saveIndex);
    }

    /**
     * Saves an arena region to a schematic file with verification
     */
    private SaveResult saveSchematic(String arenaName, Location corner1, Location corner2, int saveIndex) {
        if (!worldEditAvailable) {
            return new SaveResult(false, "WorldEdit not available", 0, null, null, false);
        }

        File arenaFolder = getArenaFolder(arenaName);
        File schematicFile = new File(arenaFolder, "saved-build-" + saveIndex + ".schem");

        try {
            // Convert Bukkit locations to WorldEdit vectors
            BlockVector3 min = BlockVector3.at(
                    Math.min(corner1.getBlockX(), corner2.getBlockX()),
                    Math.min(corner1.getBlockY(), corner2.getBlockY()),
                    Math.min(corner1.getBlockZ(), corner2.getBlockZ())
            );

            BlockVector3 max = BlockVector3.at(
                    Math.max(corner1.getBlockX(), corner2.getBlockX()),
                    Math.max(corner1.getBlockY(), corner2.getBlockY()),
                    Math.max(corner1.getBlockZ(), corner2.getBlockZ())
            );

            // Calculate expected block count
            int expectedBlockCount = (max.getX() - min.getX() + 1) *
                    (max.getY() - min.getY() + 1) *
                    (max.getZ() - min.getZ() + 1);

            // Create region
            com.sk89q.worldedit.world.World world = BukkitAdapter.adapt(corner1.getWorld());
            CuboidRegion region = new CuboidRegion(world, min, max);

            // Create clipboard
            BlockArrayClipboard clipboard = new BlockArrayClipboard(region);

            // Copy blocks to clipboard
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(world)) {
                ForwardExtentCopy copy = new ForwardExtentCopy(
                        editSession,
                        region,
                        clipboard,
                        region.getMinimumPoint()
                );
                copy.setCopyingEntities(false);
                Operations.complete(copy);
            }

            // Save to file
            try (FileOutputStream fos = new FileOutputStream(schematicFile);
                 ClipboardWriter writer = format.getWriter(fos)) {
                writer.write(clipboard);
            }

            // Verify the save by reading it back
            boolean verified = verifySchematic(schematicFile, expectedBlockCount);

            String dimensions = (max.getX() - min.getX() + 1) + "x" +
                    (max.getY() - min.getY() + 1) + "x" +
                    (max.getZ() - min.getZ() + 1);

            return new SaveResult(true, "Schematic", expectedBlockCount, schematicFile.getAbsolutePath(), dimensions, verified, saveIndex);

        } catch (WorldEditException | IOException e) {
            plugin.getLogger().severe("Failed to save schematic: " + e.getMessage());
            e.printStackTrace();
            return new SaveResult(false, "Error: " + e.getMessage(), 0, null, null, false);
        }
    }

    /**
     * Verifies a schematic file can be read correctly
     */
    private boolean verifySchematic(File schematicFile, int expectedBlockCount) {
        if (!schematicFile.exists() || schematicFile.length() == 0) {
            return false;
        }

        try (FileInputStream fis = new FileInputStream(schematicFile);
             ClipboardReader reader = format.getReader(fis)) {
            Clipboard clipboard = reader.read();

            // Verify block count is reasonable (within 10% tolerance for air blocks etc)
            BlockVector3 dimensions = clipboard.getDimensions();
            int actualVolume = dimensions.getX() * dimensions.getY() * dimensions.getZ();

            return actualVolume > 0 && Math.abs(actualVolume - expectedBlockCount) <= expectedBlockCount * 0.1;

        } catch (IOException e) {
            plugin.getLogger().warning("Schematic verification failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Saves an arena using YAML block-by-block storage
     */
    private SaveResult saveYAML(String arenaName, Location corner1, Location corner2, Location baseLocation, int saveIndex) {
        File arenaFolder = getArenaFolder(arenaName);
        File saveFolder = new File(arenaFolder, "saved-build-" + saveIndex);
        saveFolder.mkdirs();

        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
        int maxY = Math.max(corner1.getBlockY(), corner2.getBlockY());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());

        YamlConfiguration blocksConfig = new YamlConfiguration();
        List<String> blockData = new ArrayList<>();

        int blockCount = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Location loc = new Location(corner1.getWorld(), x, y, z);
                    Block block = loc.getBlock();

                    int relX = x - baseLocation.getBlockX();
                    int relY = y - baseLocation.getBlockY();
                    int relZ = z - baseLocation.getBlockZ();

                    // Format: relX,relY,relZ,material,blockData
                    String entry = relX + "," + relY + "," + relZ + "," +
                            block.getType().name() + "," +
                            block.getBlockData().getAsString();
                    blockData.add(entry);
                    blockCount++;
                }
            }
        }

        blocksConfig.set("blocks", blockData);
        blocksConfig.set("block_count", blockCount);

        // Save blocks file
        File blocksFile = new File(saveFolder, "blocks.yml");
        try {
            blocksConfig.save(blocksFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save YAML blocks: " + e.getMessage());
            return new SaveResult(false, "YAML save error: " + e.getMessage(), 0, null, null, false);
        }

        // Save metadata
        YamlConfiguration metaConfig = new YamlConfiguration();
        metaConfig.set("arena_name", arenaName);
        metaConfig.set("save_index", saveIndex);
        metaConfig.set("block_count", blockCount);
        metaConfig.set("timestamp", System.currentTimeMillis());
        metaConfig.set("world", corner1.getWorld().getName());

        String dimensions = (maxX - minX + 1) + "x" + (maxY - minY + 1) + "x" + (maxZ - minZ + 1);
        metaConfig.set("dimensions", dimensions);

        File metaFile = new File(saveFolder, "metadata.yml");
        try {
            metaConfig.save(metaFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save metadata: " + e.getMessage());
        }

        // Verify the save
        boolean verified = blocksFile.exists() && blocksFile.length() > 0;

        return new SaveResult(true, "YAML", blockCount, saveFolder.getAbsolutePath(), dimensions, verified, saveIndex);
    }

    /**
     * Loads a schematic or YAML save at a specific location
     */
    public boolean loadArenaAt(String arenaName, Location location, int version) {
        SaveVersion saveVersion = null;

        if (version <= 0) {
            // Load latest
            saveVersion = getActiveSaveVersion(arenaName);
        } else {
            // Load specific version
            List<SaveVersion> versions = getSaveVersions(arenaName);
            for (SaveVersion v : versions) {
                if (v.index == version) {
                    saveVersion = v;
                    break;
                }
            }
        }

        if (saveVersion == null) {
            plugin.getLogger().warning("No save version found for arena: " + arenaName);
            return false;
        }

        if (saveVersion.type.equals("Schematic")) {
            return loadSchematic(saveVersion.file, location);
        } else {
            return loadYAML(saveVersion.file, location);
        }
    }

    /**
     * Loads a schematic at a specific location
     */
    private boolean loadSchematic(File schematicFile, Location location) {
        if (!worldEditAvailable) {
            plugin.getLogger().warning("Cannot load schematic - WorldEdit not available!");
            return false;
        }

        if (!schematicFile.exists()) {
            plugin.getLogger().warning("Schematic file not found: " + schematicFile.getPath());
            return false;
        }

        try {
            // Load clipboard from file
            Clipboard clipboard;
            try (FileInputStream fis = new FileInputStream(schematicFile);
                 ClipboardReader reader = format.getReader(fis)) {
                clipboard = reader.read();
            }

            // Paste at location
            com.sk89q.worldedit.world.World world = BukkitAdapter.adapt(location.getWorld());
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(world)) {
                BlockVector3 to = BlockVector3.at(
                        location.getBlockX(),
                        location.getBlockY(),
                        location.getBlockZ()
                );

                Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(to)
                        .ignoreAirBlocks(false)
                        .build();

                Operations.complete(operation);
            }

            return true;

        } catch (IOException | WorldEditException e) {
            plugin.getLogger().severe("Failed to load schematic: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Loads YAML block data at a specific location
     */
    private boolean loadYAML(File saveFolder, Location location) {
        File blocksFile = new File(saveFolder, "blocks.yml");
        if (!blocksFile.exists()) {
            plugin.getLogger().warning("Blocks file not found: " + blocksFile.getPath());
            return false;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(blocksFile);
        List<String> blockData = config.getStringList("blocks");

        for (String entry : blockData) {
            String[] parts = entry.split(",", 5);
            if (parts.length < 4) continue;

            try {
                int relX = Integer.parseInt(parts[0]);
                int relY = Integer.parseInt(parts[1]);
                int relZ = Integer.parseInt(parts[2]);
                Material material = Material.valueOf(parts[3]);

                Location targetLoc = location.clone().add(relX, relY, relZ);
                Block block = targetLoc.getBlock();
                block.setType(material);

                if (parts.length >= 5 && !parts[4].isEmpty()) {
                    try {
                        block.setBlockData(org.bukkit.Bukkit.createBlockData(parts[4]));
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load block: " + entry);
            }
        }

        return true;
    }

    /**
     * Checks if any save exists for an arena
     */
    public boolean schematicExists(String arenaName) {
        return getActiveSaveVersion(arenaName) != null;
    }

    /**
     * Deletes a specific save version
     */
    public boolean deleteSpecificVersion(String arenaName, int version) {
        List<SaveVersion> versions = getSaveVersions(arenaName);
        for (SaveVersion v : versions) {
            if (v.index == version) {
                return deleteFile(v.file);
            }
        }
        return false;
    }

    /**
     * Deletes all saves for an arena
     */
    public boolean deleteAllSaves(String arenaName) {
        File arenaFolder = getArenaFolder(arenaName);
        return deleteFile(arenaFolder);
    }

    private boolean deleteFile(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteFile(child);
                }
            }
        }
        return file.delete();
    }

    public boolean isWorldEditAvailable() {
        return worldEditAvailable;
    }

    /**
     * Represents a saved version of an arena
     */
    public static class SaveVersion {
        public final int index;
        public final String type;
        public final long timestamp;
        public final File file;

        public SaveVersion(int index, String type, long timestamp, File file) {
            this.index = index;
            this.type = type;
            this.timestamp = timestamp;
            this.file = file;
        }
    }

    /**
     * Result class for save operations
     */
    public static class SaveResult {
        public final boolean success;
        public final String method;
        public final int blockCount;
        public final String filePath;
        public final String dimensions;
        public final boolean verified;
        public final int saveIndex;

        public SaveResult(boolean success, String method, int blockCount, String filePath, String dimensions, boolean verified) {
            this(success, method, blockCount, filePath, dimensions, verified, -1);
        }

        public SaveResult(boolean success, String method, int blockCount, String filePath, String dimensions, boolean verified, int saveIndex) {
            this.success = success;
            this.method = method;
            this.blockCount = blockCount;
            this.filePath = filePath;
            this.dimensions = dimensions;
            this.verified = verified;
            this.saveIndex = saveIndex;
        }
    }
}
