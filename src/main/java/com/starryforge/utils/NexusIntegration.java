package com.starryforge.utils;

import com.nexuscore.items.NexusItemProvider;
import com.nexuscore.items.NexusRegistry;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

public class NexusIntegration {

    /**
     * Tries to fetch an item from NexusCore registry.
     * Supports standard IDs (e.g., "starryforge:great_sword")
     * and CustomModelData fallback (e.g., "hanaweapons:11458").
     *
     * @param targetId The ID to look up.
     * @return The ItemStack, or null if not found.
     */
    public static ItemStack getItem(String targetId) {
        if (!Bukkit.getPluginManager().isPluginEnabled("NexusCore") || targetId == null) {
            return null;
        }

        NexusRegistry registry = com.nexuscore.NexusCore.getInstance().getRegistry();

        // 1. Standard Lookup
        ItemStack item = registry.getItemById(targetId);
        if (item != null) {
            return item;
        }

        // 2. Fallback: CustomModelData (provider:modelData)
        if (targetId.contains(":")) {
            try {
                String[] parts = targetId.split(":");
                String providerId = parts[0];
                int modelData = Integer.parseInt(parts[1]);

                NexusItemProvider provider = registry.getProvider(providerId);
                if (provider != null) {
                    for (ItemStack candidate : provider.getItems()) {
                        if (candidate.hasItemMeta() && candidate.getItemMeta().hasCustomModelData()
                                && candidate.getItemMeta().getCustomModelData() == modelData) {
                            return candidate.clone();
                        }
                    }
                }
            } catch (NumberFormatException ignored) {
                // Not a model-data ID
            }
        }

        return null;
    }
}
