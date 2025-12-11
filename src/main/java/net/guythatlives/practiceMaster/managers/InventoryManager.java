package net.guythatlives.practiceMaster.managers;

import net.guythatlives.practiceMaster.PracticeMaster;
import net.guythatlives.practiceMaster.arena.Arena;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InventoryManager {

    private final PracticeMaster plugin;
    private final Map<UUID, ItemStack[]> savedInventories;
    private final Map<UUID, ItemStack[]> savedArmor;

    public InventoryManager(PracticeMaster plugin) {
        this.plugin = plugin;
        this.savedInventories = new HashMap<>();
        this.savedArmor = new HashMap<>();
    }

    /**
     * Saves a player's inventory and clears it
     */
    public void saveAndClearInventory(Player player) {
        // Save current inventory
        savedInventories.put(player.getUniqueId(), player.getInventory().getContents().clone());
        savedArmor.put(player.getUniqueId(), player.getInventory().getArmorContents().clone());

        // Clear inventory
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
    }

    /**
     * Gives a player the arena's kit (if configured)
     */
    public void giveArenaKit(Player player, Arena arena) {
        if (arena.getKitItems() != null && !arena.getKitItems().isEmpty()) {
            for (ItemStack item : arena.getKitItems()) {
                if (item != null) {
                    player.getInventory().addItem(item.clone());
                }
            }
        }

        if (arena.getKitArmor() != null && arena.getKitArmor().length > 0) {
            // Clone armor items to avoid shared references between players
            ItemStack[] armorClone = new ItemStack[arena.getKitArmor().length];
            for (int i = 0; i < arena.getKitArmor().length; i++) {
                if (arena.getKitArmor()[i] != null) {
                    armorClone[i] = arena.getKitArmor()[i].clone();
                }
            }
            player.getInventory().setArmorContents(armorClone);
        }
    }

    /**
     * Restores a player's original inventory
     */
    public void restoreInventory(Player player) {
        UUID uuid = player.getUniqueId();

        if (savedInventories.containsKey(uuid)) {
            player.getInventory().setContents(savedInventories.get(uuid));
            savedInventories.remove(uuid);
        }

        if (savedArmor.containsKey(uuid)) {
            player.getInventory().setArmorContents(savedArmor.get(uuid));
            savedArmor.remove(uuid);
        }
    }

    /**
     * Checks if a player's inventory is saved
     */
    public boolean hasInventorySaved(Player player) {
        return savedInventories.containsKey(player.getUniqueId());
    }

    /**
     * Clears all saved inventories (for plugin disable)
     */
    public void clearAllSavedInventories() {
        savedInventories.clear();
        savedArmor.clear();
    }

    public void clearInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
    }
}