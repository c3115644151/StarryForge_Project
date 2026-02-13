package com.starryforge.features.items;

import com.nexuscore.rpg.stats.NexusStat;
import com.starryforge.StarryForge;
import org.bukkit.configuration.file.FileConfiguration;

public enum StarryItemStats {
    // Enum entries are kept for reference or legacy iteration if needed, 
    // but values are now fetched dynamically from config.
    SHADOW_DAGGER("SHADOW_DAGGER"),
    FROST_SIGH_BLADE("FROST_SIGH_BLADE");

    StarryItemStats(String itemId) {
    }

    public static double getStat(String itemId, int star, NexusStat stat) {
        // Normalize IDs
        String configSection = null;
        if ("SHADOW_DAGGER".equals(itemId)) {
            configSection = "shadow_dagger";
        } else if ("FROST_SIGH_BLADE".equals(itemId) || "FROSTSIGH_OBLIVION".equals(itemId)) {
            configSection = "frostsigh";
        }

        if (configSection == null) return 0.0;

        FileConfiguration config = StarryForge.getInstance().getConfigManager().getLegendaryConfig();
        if (config == null) return 0.0;

        String statKey = switch (stat) {
            case ATTACK_DAMAGE -> "attack_damage";
            case ATTACK_SPEED -> "attack_speed";
            case CRITICAL_CHANCE -> "critical_chance";
            case CRITICAL_DAMAGE -> "critical_damage";
            case ATTACK_RANGE -> "attack_range";
            default -> null;
        };

        if (statKey == null) return 0.0;

        String path = configSection + ".stats.star_" + star + "." + statKey;
        
        // Return 0.0 if not found, allowing NexusCore defaults to take over if necessary
        return config.getDouble(path, 0.0);
    }
}
