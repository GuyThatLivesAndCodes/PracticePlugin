package net.guythatlives.practiceMaster.arena;

import net.guythatlives.practiceMaster.PracticeMaster;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Random;

public enum ArenaEvent {
    NONE {
        @Override
        public void trigger(Player player) {
            // No event
        }
    },
    CUSTOM {
        @Override
        public void trigger(Player player) {
            // This is handled by the custom event system
        }
    };

    public abstract void trigger(Player player);

    /**
     * Triggers a custom event from config
     */
    public static void triggerCustomEvent(Player player, String eventName) {
        ConfigurationSection config = getEventConfig(eventName);
        if (config == null) {
            return;
        }

        String type = config.getString("type", "velocity");

        switch (type.toLowerCase()) {
            case "velocity":
            case "shooting":
                triggerVelocityEvent(player, config);
                break;
            case "jump":
                triggerJumpEvent(player, config);
                break;
            case "gravity":
                triggerGravityEvent(player, config);
                break;
            case "teleport":
                triggerTeleportEvent(player, config);
                break;
            case "speed":
                triggerSpeedEvent(player, config);
                break;
            default:
                triggerVelocityEvent(player, config);
                break;
        }

        // Send message
        String message = config.getString("message");
        if (message != null && !message.isEmpty()) {
            player.sendMessage(message);
        }

        // Play sound
        String soundName = config.getString("sound");
        if (soundName != null && !soundName.isEmpty()) {
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase().replace("MINECRAFT:", ""));
                float volume = (float) config.getDouble("sound_volume", 1.0);
                float pitch = (float) config.getDouble("sound_pitch", 1.0);
                player.playSound(player.getLocation(), sound, SoundCategory.AMBIENT, volume, pitch);
            } catch (IllegalArgumentException e) {
                // Invalid sound, ignore
            }
        }
    }

    private static void triggerVelocityEvent(Player player, ConfigurationSection config) {
        Random random = new Random();

        double minX = config.getDouble("velocity.x.min", -1.0);
        double maxX = config.getDouble("velocity.x.max", 1.0);
        double minY = config.getDouble("velocity.y.min", 0.5);
        double maxY = config.getDouble("velocity.y.max", 1.3);
        double minZ = config.getDouble("velocity.z.min", -1.0);
        double maxZ = config.getDouble("velocity.z.max", 1.0);
        double multiplier = config.getDouble("velocity.multiplier", 1.5);

        double x = minX + (maxX - minX) * random.nextDouble();
        double y = minY + (maxY - minY) * random.nextDouble();
        double z = minZ + (maxZ - minZ) * random.nextDouble();

        Vector velocity = new Vector(x, y, z).normalize().multiply(multiplier);
        player.setVelocity(velocity);
    }

    private static void triggerJumpEvent(Player player, ConfigurationSection config) {
        Random random = new Random();

        double minY = config.getDouble("jump.min", 0.8);
        double maxY = config.getDouble("jump.max", 1.3);
        boolean preserveHorizontal = config.getBoolean("jump.preserve_horizontal", true);

        double y = minY + (maxY - minY) * random.nextDouble();

        Vector velocity = player.getVelocity();
        if (!preserveHorizontal) {
            velocity.setX(0);
            velocity.setZ(0);
        }
        velocity.setY(y);
        player.setVelocity(velocity);
    }

    private static void triggerGravityEvent(Player player, ConfigurationSection config) {
        Random random = new Random();
        Vector current = player.getVelocity();

        double increaseMultiplier = config.getDouble("multipliers.increase", 1.3);
        double decreaseMultiplier = config.getDouble("multipliers.decrease", 0.7);
        double chance = config.getDouble("chance.increase", 0.5);

        if (random.nextDouble() < chance) {
            current.multiply(increaseMultiplier);
        } else {
            current.multiply(decreaseMultiplier);
        }

        player.setVelocity(current);
    }

    private static void triggerTeleportEvent(Player player, ConfigurationSection config) {
        Random random = new Random();

        double radius = config.getDouble("radius", 5.0);
        double minY = config.getDouble("y_offset.min", -2.0);
        double maxY = config.getDouble("y_offset.max", 2.0);

        double x = (random.nextDouble() - 0.5) * 2 * radius;
        double y = minY + (maxY - minY) * random.nextDouble();
        double z = (random.nextDouble() - 0.5) * 2 * radius;

        player.teleport(player.getLocation().add(x, y, z));
    }

    private static void triggerSpeedEvent(Player player, ConfigurationSection config) {
        double forwardMultiplier = config.getDouble("forward_multiplier", 2.0);
        double upwardBoost = config.getDouble("upward_boost", 0.3);

        Vector direction = player.getLocation().getDirection();
        direction.setY(0).normalize();
        direction.multiply(forwardMultiplier);
        direction.setY(upwardBoost);

        player.setVelocity(direction);
    }

    protected static ConfigurationSection getEventConfig(String eventName) {
        ConfigurationSection events = PracticeMaster.getInstance().getConfig().getConfigurationSection("events");
        if (events == null || !events.contains(eventName)) {
            return null;
        }
        return events.getConfigurationSection(eventName);
    }

    public static ArenaEvent fromString(String name) {
        try {
            return ArenaEvent.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CUSTOM;
        }
    }
}