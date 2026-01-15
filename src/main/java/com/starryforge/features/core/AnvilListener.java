package com.starryforge.features.core;

import com.starryforge.features.mining.LithicInsightEnchantment;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;

public class AnvilListener implements Listener {

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getItem(0);
        ItemStack right = event.getInventory().getItem(1);

        if (left == null || right == null)
            return;

        int leftLevel = LithicInsightEnchantment.getLevel(left);
        int rightLevel = LithicInsightEnchantment.getLevel(right);

        // Case 1: Book + Book Merging (Fix: User Requested)
        if (left.getType() == Material.ENCHANTED_BOOK && right.getType() == Material.ENCHANTED_BOOK) {
            if (leftLevel > 0 && rightLevel > 0) {
                int newLevel = leftLevel;
                if (leftLevel == rightLevel) {
                    newLevel = Math.min(leftLevel + 1, LithicInsightEnchantment.MAX_LEVEL);
                } else {
                    newLevel = Math.max(leftLevel, rightLevel);
                }

                ItemStack result = event.getResult();
                // If vanilla result is null or air (no vanilla usage), clone left
                if (result == null || result.getType().isAir()) {
                    result = left.clone();
                }

                LithicInsightEnchantment.apply(result, newLevel);
                event.setResult(result);

                setRepairCost(event, newLevel * 2);
            }
            return;
        }

        // Case 2: Pickaxe + Book or Pickaxe + Pickaxe
        // User Requirement: "Only compatible with Pickaxe"
        if (isPickaxe(left.getType())) {
            // Check right item being valid source
            boolean validSource = (right.getType() == Material.ENCHANTED_BOOK || isPickaxe(right.getType()));
            if (!validSource)
                return;

            if (rightLevel > 0) {
                int currentLevel = leftLevel;
                int newLevel;

                if (currentLevel == rightLevel) {
                    newLevel = Math.min(currentLevel + 1, LithicInsightEnchantment.MAX_LEVEL);
                } else {
                    newLevel = Math.max(currentLevel, rightLevel);
                }

                if (newLevel < currentLevel)
                    newLevel = currentLevel; // Don't downgrade

                // Get vanilla result if possible (handles durability repair etc.)
                ItemStack result = event.getResult();
                if (result == null || result.getType().isAir()) {
                    result = left.clone();
                }

                LithicInsightEnchantment.apply(result, newLevel);
                event.setResult(result);

                // Update cost
                int cost = getRepairCostLegacy(event.getInventory());
                if (cost < newLevel * 2)
                    cost = newLevel * 2;

                setRepairCost(event, cost);
            }
        }
    }

    private void setRepairCost(PrepareAnvilEvent event, int cost) {
        // On Paper 1.21+, use AnvilView if available.
        if (event.getView() instanceof org.bukkit.inventory.view.AnvilView view) {
            view.setRepairCost(cost);
        } else {
            setRepairCostLegacy(event.getInventory(), cost);
        }
    }

    @SuppressWarnings("removal")
    private void setRepairCostLegacy(org.bukkit.inventory.AnvilInventory inv, int cost) {
        inv.setRepairCost(cost);
    }

    @SuppressWarnings("removal")
    private int getRepairCostLegacy(org.bukkit.inventory.AnvilInventory inv) {
        return inv.getRepairCost();
    }

    private boolean isPickaxe(Material mat) {
        return mat.name().endsWith("_PICKAXE");
    }

}
