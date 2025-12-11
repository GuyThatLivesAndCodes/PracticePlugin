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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class SchematicManager {

    private final PracticeMaster plugin;
    private final File schematicsFolder;
    private final boolean worldEditAvailable;
    private final ClipboardFormat format;

    public SchematicManager(PracticeMaster plugin) {
        this.plugin = plugin;
        this.schematicsFolder = new File(plugin.getDataFolder(), "schematics");

        // Create schematics folder
        if (!schematicsFolder.exists()) {
            schematicsFolder.mkdirs();
        }

        // Check if WorldEdit/FAWE is available
        this.worldEditAvailable = plugin.getServer().getPluginManager().getPlugin("WorldEdit") != null ||
                plugin.getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") != null;

        if (worldEditAvailable) {
            // Use Sponge Schematic format (newer, better compression)
            this.format = BuiltInClipboardFormat.SPONGE_SCHEMATIC;
            plugin.getLogger().info("WorldEdit detected! Using schematic-based arena saving.");
        } else {
            this.format = null;
            plugin.getLogger().warning("WorldEdit not found! Arena saving will be slower.");
            plugin.getLogger().warning("Install WorldEdit or FastAsyncWorldEdit for better performance!");
        }
    }

    /**
     * Saves an arena region to a schematic file
     */
    public SaveResult saveSchematic(String arenaName, Location corner1, Location corner2) {
        if (!worldEditAvailable) {
            return new SaveResult(false, "WorldEdit not available", 0, null);
        }

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

            // Calculate block count
            int blockCount = (max.getX() - min.getX() + 1) *
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
            File schematicFile = new File(schematicsFolder, arenaName + ".schem");
            try (FileOutputStream fos = new FileOutputStream(schematicFile);
                 ClipboardWriter writer = format.getWriter(fos)) {
                writer.write(clipboard);
            }

            String dimensions = (max.getX() - min.getX() + 1) + "x" +
                    (max.getY() - min.getY() + 1) + "x" +
                    (max.getZ() - min.getZ() + 1);

            return new SaveResult(true, "Schematic", blockCount, schematicFile.getAbsolutePath(), dimensions);

        } catch (WorldEditException | IOException e) {
            plugin.getLogger().severe("Failed to save schematic: " + e.getMessage());
            e.printStackTrace();
            return new SaveResult(false, "Error: " + e.getMessage(), 0, null);
        }
    }

    /**
     * Loads a schematic at a specific location
     */
    public boolean loadSchematic(String arenaName, Location location) {
        if (!worldEditAvailable) {
            plugin.getLogger().warning("Cannot load schematic - WorldEdit not available!");
            return false;
        }

        File schematicFile = new File(schematicsFolder, arenaName + ".schem");
        if (!schematicFile.exists()) {
            plugin.getLogger().warning("Schematic file not found for arena: " + arenaName);
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
                // Set paste location
                BlockVector3 to = BlockVector3.at(
                        location.getBlockX(),
                        location.getBlockY(),
                        location.getBlockZ()
                );

                // Paste
                Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(to)
                        .ignoreAirBlocks(false)
                        .build();

                Operations.complete(operation);
            }

            return true;

        } catch (IOException | WorldEditException e) {
            plugin.getLogger().severe("Failed to load schematic for arena '" + arenaName + "': " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Checks if a schematic exists for an arena
     */
    public boolean schematicExists(String arenaName) {
        File schematicFile = new File(schematicsFolder, arenaName + ".schem");
        return schematicFile.exists();
    }

    /**
     * Deletes a schematic file
     */
    public boolean deleteSchematic(String arenaName) {
        File schematicFile = new File(schematicsFolder, arenaName + ".schem");
        if (schematicFile.exists()) {
            return schematicFile.delete();
        }
        return false;
    }

    public boolean isWorldEditAvailable() {
        return worldEditAvailable;
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
}