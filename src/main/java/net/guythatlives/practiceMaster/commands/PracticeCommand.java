package net.guythatlives.practiceMaster.commands;

import net.guythatlives.practiceMaster.PracticeMaster;
import net.guythatlives.practiceMaster.arena.Arena;
import net.guythatlives.practiceMaster.arena.ArenaEvent;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PracticeCommand implements CommandExecutor, TabCompleter {

    private final PracticeMaster plugin;

    public PracticeCommand(PracticeMaster plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "play":
                return handlePlay(sender, args);
            case "leave":
                return handleLeave(sender);
            case "arena":
                return handleArena(sender, args);
            case "timer":
                return handleTimer(sender, args);
            case "reload":
                return handleReload(sender);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean handlePlay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            player.sendMessage("§cUsage: /practice play <arena_name> [timer:on/off]");
            return true;
        }

        String arenaName = args[1];
        Arena arena = plugin.getArenaManager().getArena(arenaName);

        if (arena == null) {
            player.sendMessage("§cArena '§e" + arenaName + "§c' does not exist!");
            return true;
        }

        if (!arena.isComplete()) {
            player.sendMessage("§cThis arena is not fully configured!");
            player.sendMessage("§7Missing: " + getMissingComponents(arena));
            return true;
        }

        if (arena.getPlayLocations().isEmpty()) {
            player.sendMessage("§cThis arena has no play locations!");
            player.sendMessage("§7Add locations with: §e/practice arena addplaylocation " + arenaName);
            return true;
        }

        // Check for timer argument
        boolean timerEnabled = arena.isTimerEnabled();
        if (args.length >= 3) {
            String timerArg = args[2].toLowerCase();
            if (timerArg.startsWith("timer:")) {
                String option = timerArg.substring(6);
                timerEnabled = option.equals("on") || option.equals("true");
            }
        }

        plugin.getSessionManager().startSession(player, arena, timerEnabled);
        return true;
    }

    private String getMissingComponents(Arena arena) {
        List<String> missing = new ArrayList<>();
        if (arena.getCorner1() == null) missing.add("corner1");
        if (arena.getCorner2() == null) missing.add("corner2");
        if (arena.getSpawnLocation() == null) missing.add("spawn");
        if (arena.getBaseLocation() == null) missing.add("base location");
        if (arena.getSavedBlocks().isEmpty()) missing.add("saved structure");
        return String.join(", ", missing);
    }

    private boolean handleLeave(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (!plugin.getSessionManager().isInSession(player)) {
            player.sendMessage("§cYou are not in a practice session!");
            return true;
        }

        plugin.getSessionManager().endSession(player);
        return true;
    }

    private boolean handleArena(CommandSender sender, String[] args) {
        if (!sender.hasPermission("practicemaster.arena")) {
            sender.sendMessage("§cYou don't have permission to manage arenas!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§e=== Arena Commands ===");
            sender.sendMessage("§7/practice arena create <n>");
            sender.sendMessage("§7/practice arena delete <n>");
            sender.sendMessage("§7/practice arena setcorner1 <n>");
            sender.sendMessage("§7/practice arena setcorner2 <n>");
            sender.sendMessage("§7/practice arena setspawn <n>");
            sender.sendMessage("§7/practice arena setbase <n>");
            sender.sendMessage("§7/practice arena save <n> §8- Save structure");
            sender.sendMessage("§7/practice arena hardsave <n> §8- Force YAML save");
            sender.sendMessage("§7/practice arena addplaylocation <n>");
            sender.sendMessage("§7/practice arena removeplaylocation <n>");
            sender.sendMessage("§7/practice arena loadall <n> §8- Load at all locations");
            sender.sendMessage("§7/practice arena setevent <n> <event>");
            sender.sendMessage("§7/practice arena settimer <n> <seconds>");
            sender.sendMessage("§7/practice arena setkit <n> §8- Save your inventory as kit");
            sender.sendMessage("§7/practice arena clearkit <n> §8- Clear arena kit");
            sender.sendMessage("§7/practice arena info <n>");
            sender.sendMessage("§7/practice arena list");
            return true;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "create":
                return handleArenaCreate(sender, args);
            case "delete":
                return handleArenaDelete(sender, args);
            case "setcorner1":
                return handleArenaSetCorner1(sender, args);
            case "setcorner2":
                return handleArenaSetCorner2(sender, args);
            case "setspawn":
                return handleArenaSetSpawn(sender, args);
            case "setbase":
                return handleArenaSetBase(sender, args);
            case "save":
                return handleArenaSave(sender, args);
            case "hardsave":
                return handleArenaHardSave(sender, args);
            case "addplaylocation":
                return handleArenaAddPlayLocation(sender, args);
            case "removeplaylocation":
                return handleArenaRemovePlayLocation(sender, args);
            case "loadall":
                return handleArenaLoadAll(sender, args);
            case "setevent":
                return handleArenaSetEvent(sender, args);
            case "settimer":
                return handleArenaSetTimer(sender, args);
            case "setkit":
                return handleArenaSetKit(sender, args);
            case "clearkit":
                return handleArenaClearKit(sender, args);
            case "info":
                return handleArenaInfo(sender, args);
            case "list":
                return handleArenaList(sender);
            default:
                sender.sendMessage("§cUnknown arena command!");
                return true;
        }
    }

    private boolean handleArenaCreate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /practice arena create <n>");
            return true;
        }

        String name = args[2].toLowerCase();

        if (plugin.getArenaManager().arenaExists(name)) {
            sender.sendMessage("§cArena '§e" + name + "§c' already exists!");
            return true;
        }

        Arena arena = new Arena(name);
        plugin.getArenaManager().createArena(arena);
        sender.sendMessage("§aArena '§e" + name + "§a' created!");
        sender.sendMessage("§7Next steps:");
        sender.sendMessage("§71. Set corners: §e/practice arena setcorner1/setcorner2 " + name);
        sender.sendMessage("§72. Set spawn: §e/practice arena setspawn " + name);
        sender.sendMessage("§73. Set base location: §e/practice arena setbase " + name);
        sender.sendMessage("§74. Save structure: §e/practice arena save " + name);
        sender.sendMessage("§75. Add play locations: §e/practice arena addplaylocation " + name);
        return true;
    }

    private boolean handleArenaDelete(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /practice arena delete <n>");
            return true;
        }

        String name = args[2].toLowerCase();

        if (!plugin.getArenaManager().arenaExists(name)) {
            sender.sendMessage("§cArena '§e" + name + "§c' does not exist!");
            return true;
        }

        plugin.getArenaManager().deleteArena(name);
        sender.sendMessage("§aArena '§e" + name + "§a' deleted!");
        return true;
    }

    private boolean handleArenaSetCorner1(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUsage: /practice arena setcorner1 <n>");
            return true;
        }

        Player player = (Player) sender;
        String name = args[2].toLowerCase();
        Arena arena = plugin.getArenaManager().getArena(name);

        if (arena == null) {
            player.sendMessage("§cArena '§e" + name + "§c' does not exist!");
            return true;
        }

        arena.setCorner1(player.getLocation());
        plugin.getArenaManager().saveArenas();
        player.sendMessage("§aCorner 1 set for arena '§e" + name + "§a'!");
        return true;
    }

    private boolean handleArenaSetCorner2(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUsage: /practice arena setcorner2 <n>");
            return true;
        }

        Player player = (Player) sender;
        String name = args[2].toLowerCase();
        Arena arena = plugin.getArenaManager().getArena(name);

        if (arena == null) {
            player.sendMessage("§cArena '§e" + name + "§c' does not exist!");
            return true;
        }

        arena.setCorner2(player.getLocation());
        plugin.getArenaManager().saveArenas();
        player.sendMessage("§aCorner 2 set for arena '§e" + name + "§a'!");
        return true;
    }

    private boolean handleArenaSetSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUsage: /practice arena setspawn <n>");
            return true;
        }

        Player player = (Player) sender;
        String name = args[2].toLowerCase();
        Arena arena = plugin.getArenaManager().getArena(name);

        if (arena == null) {
            player.sendMessage("§cArena '§e" + name + "§c' does not exist!");
            return true;
        }

        arena.setSpawnLocation(player.getLocation());
        plugin.getArenaManager().saveArenas();
        player.sendMessage("§aSpawn location set for arena '§e" + name + "§a'!");
        return true;
    }

    private boolean handleArenaSetBase(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUsage: /practice arena setbase <n>");
            return true;
        }

        Player player = (Player) sender;
        String name = args[2].toLowerCase();
        Arena arena = plugin.getArenaManager().getArena(name);

        if (arena == null) {
            player.sendMessage("§cArena '§e" + name + "§c' does not exist!");
            return true;
        }

        arena.setBaseLocation(player.getLocation());
        plugin.getArenaManager().saveArenas();
        player.sendMessage("§aBase location set for arena '§e" + name + "§a'!");
        player.sendMessage("§7This is the reference location (not for gameplay).");
        player.sendMessage("§7Now run: §e/practice arena save " + name);
        return true;
    }

    private boolean handleArenaSave(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /practice arena save <n>");
            return true;
        }

        String name = args[2].toLowerCase();
        Arena arena = plugin.getArenaManager().getArena(name);

        if (arena == null) {
            sender.sendMessage("§cArena '§e" + name + "§c' does not exist!");
            return true;
        }

        if (arena.getCorner1() == null || arena.getCorner2() == null || arena.getBaseLocation() == null) {
            sender.sendMessage("§cPlease set corners and base location first!");
            return true;
        }

        sender.sendMessage("§eSaving arena structure...");
        arena.saveArenaStructure(false);
        plugin.getArenaManager().saveArenas();
        sender.sendMessage("§aArena structure saved! §7(" + arena.getSavedBlocks().size() + " blocks)");
        sender.sendMessage("§7Now add play locations: §e/practice arena addplaylocation " + name);
        return true;
    }

    private boolean handleArenaHardSave(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /practice arena hardsave <n>");
            return true;
        }

        String name = args[2].toLowerCase();
        Arena arena = plugin.getArenaManager().getArena(name);

        if (arena == null) {
            sender.sendMessage("§cArena '§e" + name + "§c' does not exist!");
            return true;
        }

        if (arena.getCorner1() == null || arena.getCorner2() == null || arena.getBaseLocation() == null) {
            sender.sendMessage("§cPlease set corners and base location first!");
            return true;
        }

        sender.sendMessage("§eForce saving arena structure to YAML...");
        Arena.SaveResult result = arena.saveArenaStructure(true); // Force YAML save
        plugin.getArenaManager().saveArenas();

        if (result.success) {
            sender.sendMessage("§aArena structure saved to YAML! §7(" + result.blockCount + " blocks)");
            sender.sendMessage("§7Dimensions: §e" + result.dimensions);
        } else {
            sender.sendMessage("§cFailed to save arena: " + result.method);
        }
        return true;
    }

    private boolean handleArenaAddPlayLocation(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUsage: /practice arena addplaylocation <n>");
            return true;
        }

        Player player = (Player) sender;
        String name = args[2].toLowerCase();
        Arena arena = plugin.getArenaManager().getArena(name);

        if (arena == null) {
            player.sendMessage("§cArena '§e" + name + "§c' does not exist!");
            return true;
        }

        // Check if arena has saved structure (either schematic or legacy blocks)
        boolean hasStructure = plugin.getSchematicManager().schematicExists(arena.getName()) || !arena.getSavedBlocks().isEmpty();
        if (!hasStructure) {
            player.sendMessage("§cPlease save the arena structure first!");
            player.sendMessage("§7Use: §e/practice arena save " + name);
            return true;
        }

        Location loc = player.getLocation();
        arena.addPlayLocation(loc);
        plugin.getArenaManager().saveArenas();
        player.sendMessage("§aPlay location added for arena '§e" + name + "§a'!");
        player.sendMessage("§7Total play locations: §e" + arena.getPlayLocations().size());
        player.sendMessage("§7Load arena here: §e/practice arena loadall " + name);
        return true;
    }

    private boolean handleArenaRemovePlayLocation(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUsage: /practice arena removeplaylocation <n>");
            return true;
        }

        Player player = (Player) sender;
        String name = args[2].toLowerCase();
        Arena arena = plugin.getArenaManager().getArena(name);

        if (arena == null) {
            player.sendMessage("§cArena '§e" + name + "§c' does not exist!");
            return true;
        }

        arena.removePlayLocation(player.getLocation());
        plugin.getArenaManager().saveArenas();
        player.sendMessage("§aPlay location removed for arena '§e" + name + "§a'!");
        player.sendMessage("§7Remaining play locations: §e" + arena.getPlayLocations().size());
        return true;
    }

    private boolean handleArenaLoadAll(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /practice arena loadall <n>");
            return true;
        }

        String name = args[2].toLowerCase();
        Arena arena = plugin.getArenaManager().getArena(name);

        if (arena == null) {
            sender.sendMessage("§cArena '§e" + name + "§c' does not exist!");
            return true;
        }

        // Check if arena has saved structure (either schematic or legacy blocks)
        boolean hasStructure = plugin.getSchematicManager().schematicExists(arena.getName()) || !arena.getSavedBlocks().isEmpty();
        if (!hasStructure) {
            sender.sendMessage("§cNo saved structure for this arena!");
            return true;
        }

        if (arena.getPlayLocations().isEmpty()) {
            sender.sendMessage("§cNo play locations configured!");
            return true;
        }

        sender.sendMessage("§eLoading arena at all play locations...");
        arena.loadArenaAtAllLocations();
        sender.sendMessage("§aArena loaded at §e" + arena.getPlayLocations().size() + "§a location(s)!");
        return true;
    }

    private boolean handleArenaSetEvent(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /practice arena setevent <n> <event>");
            sender.sendMessage("§7Available events:");
            sender.sendMessage("§7  - NONE §8(no events)");
            sender.sendMessage("§7  - CUSTOM:<name> §8(custom event from config)");
            sender.sendMessage("§7Example: §e/practice arena setevent bridge CUSTOM:shooting");
            return true;
        }

        String name = args[2].toLowerCase();
        Arena arena = plugin.getArenaManager().getArena(name);

        if (arena == null) {
            sender.sendMessage("§cArena '§e" + name + "§c' does not exist!");
            return true;
        }

        String eventArg = args[3];

        // Check if it's a custom event
        if (eventArg.toUpperCase().startsWith("CUSTOM:")) {
            String customEventName = eventArg.substring(7); // Remove "CUSTOM:" prefix

            // Verify event exists in config
            if (plugin.getConfig().getConfigurationSection("events." + customEventName) == null) {
                sender.sendMessage("§cCustom event '§e" + customEventName + "§c' not found in config.yml!");
                sender.sendMessage("§7Add it to the 'events:' section in config.yml");
                return true;
            }

            arena.setArenaEvent(ArenaEvent.CUSTOM);
            arena.setCustomEventName(customEventName);
            plugin.getArenaManager().saveArenas();
            sender.sendMessage("§aCustom event '§e" + customEventName + "§a' set for arena '§e" + name + "§a'!");
            return true;
        }

        // Legacy hardcoded events
        ArenaEvent event = ArenaEvent.fromString(eventArg);
        arena.setArenaEvent(event);
        arena.setCustomEventName(null);
        plugin.getArenaManager().saveArenas();
        sender.sendMessage("§aEvent set to '§e" + event.name() + "§a' for arena '§e" + name + "§a'!");
        return true;
    }

    private boolean handleArenaSetTimer(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /practice arena settimer <n> <seconds|on|off>");
            return true;
        }

        String name = args[2].toLowerCase();
        Arena arena = plugin.getArenaManager().getArena(name);

        if (arena == null) {
            sender.sendMessage("§cArena '§e" + name + "§c' does not exist!");
            return true;
        }

        String timerArg = args[3].toLowerCase();

        if (timerArg.equals("on")) {
            arena.setTimerEnabled(true);
            sender.sendMessage("§aTimer enabled for arena '§e" + name + "§a'!");
        } else if (timerArg.equals("off")) {
            arena.setTimerEnabled(false);
            sender.sendMessage("§aTimer disabled for arena '§e" + name + "§a'!");
        } else {
            try {
                int seconds = Integer.parseInt(timerArg);
                arena.setDefaultTimerSeconds(seconds);
                sender.sendMessage("§aDefault timer set to §e" + seconds + "§a seconds for arena '§e" + name + "§a'!");
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid argument! Use 'on', 'off', or a number of seconds.");
                return true;
            }
        }

        plugin.getArenaManager().saveArenas();
        return true;
    }

    private boolean handleArenaSetKit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUsage: /practice arena setkit <n>");
            sender.sendMessage("§7This will save your current inventory as the arena kit.");
            return true;
        }

        Player player = (Player) sender;
        String name = args[2].toLowerCase();
        Arena arena = plugin.getArenaManager().getArena(name);

        if (arena == null) {
            player.sendMessage("§cArena '§e" + name + "§c' does not exist!");
            return true;
        }

        // Save player's current inventory as kit
        List<ItemStack> kitItems = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null) {
                kitItems.add(item.clone());
            }
        }
        arena.setKitItems(kitItems);

        // Save armor
        ItemStack[] armor = player.getInventory().getArmorContents();
        if (armor != null) {
            arena.setKitArmor(armor.clone());
        }

        plugin.getArenaManager().saveArenas();
        player.sendMessage("§aKit saved for arena '§e" + name + "§a'!");
        player.sendMessage("§7Items: §e" + kitItems.size() + " §7| Armor pieces: §e" +
                (armor != null ? (int)Arrays.stream(armor).filter(i -> i != null).count() : 0));
        return true;
    }

    private boolean handleArenaClearKit(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /practice arena clearkit <n>");
            return true;
        }

        String name = args[2].toLowerCase();
        Arena arena = plugin.getArenaManager().getArena(name);

        if (arena == null) {
            sender.sendMessage("§cArena '§e" + name + "§c' does not exist!");
            return true;
        }

        arena.setKitItems(new ArrayList<>());
        arena.setKitArmor(new ItemStack[4]);
        plugin.getArenaManager().saveArenas();
        sender.sendMessage("§aKit cleared for arena '§e" + name + "§a'!");
        return true;
    }

    private boolean handleArenaInfo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /practice arena info <n>");
            return true;
        }

        String name = args[2].toLowerCase();
        Arena arena = plugin.getArenaManager().getArena(name);

        if (arena == null) {
            sender.sendMessage("§cArena '§e" + name + "§c' does not exist!");
            return true;
        }

        sender.sendMessage("§e=== Arena Info: " + name + " ===");
        sender.sendMessage("§7Status: " + (arena.isComplete() ? "§a✓ Complete" : "§c✗ Incomplete"));
        sender.sendMessage("§7Corner 1: " + (arena.getCorner1() != null ? "§a✓" : "§c✗"));
        sender.sendMessage("§7Corner 2: " + (arena.getCorner2() != null ? "§a✓" : "§c✗"));
        sender.sendMessage("§7Spawn: " + (arena.getSpawnLocation() != null ? "§a✓" : "§c✗"));
        sender.sendMessage("§7Base Location: " + (arena.getBaseLocation() != null ? "§a✓" : "§c✗"));
        sender.sendMessage("§7Saved Blocks: §e" + arena.getSavedBlocks().size());
        sender.sendMessage("§7Play Locations: §e" + arena.getPlayLocations().size());
        sender.sendMessage("§7Event: §e" + arena.getArenaEvent().name());
        sender.sendMessage("§7Timer: " + (arena.isTimerEnabled() ? "§aEnabled" : "§cDisabled"));

        // Storage info
        if (arena.getSavedBlocks().isEmpty() && !plugin.getSchematicManager().schematicExists(arena.getName())) {
            sender.sendMessage("§7Storage: §c✗ Not saved");
        } else if (plugin.getSchematicManager().schematicExists(arena.getName())) {
            sender.sendMessage("§7Storage: §aSchematic file (.schem)");
        } else {
            sender.sendMessage("§7Storage: §eLegacy (YAML - " + arena.getSavedBlocks().size() + " blocks)");
        }

        // Kit info
        int kitItemCount = arena.getKitItems() != null ? arena.getKitItems().size() : 0;
        int armorCount = arena.getKitArmor() != null ?
                (int)Arrays.stream(arena.getKitArmor()).filter(i -> i != null).count() : 0;
        sender.sendMessage("§7Kit: §e" + kitItemCount + " items, " + armorCount + " armor pieces");
        return true;
    }

    private boolean handleArenaList(CommandSender sender) {
        if (plugin.getArenaManager().getArenas().isEmpty()) {
            sender.sendMessage("§cNo arenas exist!");
            return true;
        }

        sender.sendMessage("§e=== Arenas ===");
        for (Arena arena : plugin.getArenaManager().getArenas().values()) {
            String status = arena.isComplete() ? "§a✓" : "§c✗";
            String playLocs = arena.getPlayLocations().isEmpty() ? "§c(No play locations)" : "§7(" + arena.getPlayLocations().size() + " play locations)";
            sender.sendMessage(status + " §e" + arena.getName() + " §8[" + arena.getArenaEvent() + "] " + playLocs);
        }
        return true;
    }

    private boolean handleTimer(CommandSender sender, String[] args) {
        sender.sendMessage("§7Timer is managed per-session. Use:");
        sender.sendMessage("§e/practice play <arena> timer:on");
        sender.sendMessage("§e/practice play <arena> timer:off");
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("practicemaster.admin")) {
            sender.sendMessage("§cYou don't have permission!");
            return true;
        }

        sender.sendMessage("§eReloading PracticeMaster...");

        // Reload config
        plugin.reloadConfig();
        sender.sendMessage("§a✓ Config reloaded");

        // Reload arenas from file
        plugin.getArenaManager().loadArenas();
        sender.sendMessage("§a✓ Arenas reloaded from arenas.yml");

        int arenaCount = plugin.getArenaManager().getArenas().size();
        sender.sendMessage("§aReload complete! Loaded " + arenaCount + " arena(s)");
        sender.sendMessage("§7Check console for detailed arena status.");

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§e=== PracticeMaster Commands ===");
        sender.sendMessage("§7/practice play <arena> [timer:on/off] - Start practice");
        sender.sendMessage("§7/practice leave - Leave current practice");
        sender.sendMessage("§7/practice arena - Arena management commands");
        sender.sendMessage("§7/practice reload - Reload configuration");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("play", "leave", "arena", "timer", "reload"));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("play")) {
                completions.addAll(plugin.getArenaManager().getArenas().keySet());
            } else if (args[0].equalsIgnoreCase("arena")) {
                completions.addAll(Arrays.asList("create", "delete", "setcorner1", "setcorner2",
                        "setspawn", "setbase", "save", "hardsave", "addplaylocation", "removeplaylocation",
                        "loadall", "setevent", "settimer", "setkit", "clearkit", "info", "list"));
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("play")) {
                completions.addAll(Arrays.asList("timer:on", "timer:off"));
            } else if (args[0].equalsIgnoreCase("arena") &&
                    !args[1].equalsIgnoreCase("create") &&
                    !args[1].equalsIgnoreCase("list")) {
                completions.addAll(plugin.getArenaManager().getArenas().keySet());
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("arena") &&
                args[1].equalsIgnoreCase("setevent")) {
            for (ArenaEvent event : ArenaEvent.values()) {
                completions.add(event.name());
            }
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}