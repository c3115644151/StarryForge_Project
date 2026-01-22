package com.starryforge.integration;

import com.nexuscore.items.NexusItemProvider;
import com.starryforge.StarryForge;
import org.bukkit.inventory.ItemStack;
import java.util.List;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;

import com.starryforge.features.core.PDCManager;
import com.starryforge.utils.Keys;
import com.nexuscore.items.RecipeDisplay;
import com.nexuscore.items.RecipeType;
import org.bukkit.Material;
import java.util.Map;

public class StarryForgeProvider implements NexusItemProvider, Listener {

    private final StarryForge plugin;

    public StarryForgeProvider(StarryForge plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("StarryForgeProvider initialized and listener registered.");
    }

    @Override
    public String getModuleId() {
        return "starry-forge";
    }

    @Override
    public String getDisplayName() {
        return "Starry Forge";
    }

    @Override
    public ItemStack getIcon() {
        // Return a representative icon, e.g. Titan's Hammer or just an Anvil
        ItemStack icon = plugin.getItemManager().getItem("TITANS_HAMMER");
        if (icon == null)
            icon = new ItemStack(org.bukkit.Material.ANVIL);
        return icon;
    }

    @Override
    public List<ItemStack> getItems() {
        List<ItemStack> items = plugin.getItemManager().getAllItems();
        return items;
    }

    @Override
    public RecipeDisplay getRecipe(ItemStack item) {
        String id = PDCManager.getString(item, Keys.ITEM_ID_KEY);

        // 1. Check Alloy Recipes
        try {
            com.starryforge.features.alloy.AlloyManager alloyManager = plugin.getAlloyManager();
            if (alloyManager != null) {
                for (com.starryforge.features.alloy.AlloyManager.AlloyRecipe r : alloyManager.getRecipes()) {
                    if ((id != null && id.equalsIgnoreCase(r.resultId()))
                            || (id == null && item.getType().name().equalsIgnoreCase(r.resultId()))) {
                        RecipeDisplay nr = new RecipeDisplay(RecipeType.ALLOY);
                        int slot = 0;
                        for (Map.Entry<String, Integer> entry : r.inputs().entrySet()) {
                            ItemStack ing = plugin.getItemManager().getItem(entry.getKey());
                            if (ing == null) {
                                Material mat = Material.matchMaterial(entry.getKey());
                                if (mat != null)
                                    ing = new ItemStack(mat);
                            }
                            if (ing != null) {
                                ing.setAmount(entry.getValue());
                                nr.addIngredient(slot++, ing);
                            }
                        }
                        return nr;
                    }
                }
            }
        } catch (Exception e) {
        }

        // 2. Check Forging Recipes
        try {
            com.starryforge.features.forging.ForgingRecipeManager forgingManager = plugin.getForgingRecipeManager();
            if (forgingManager != null) {
                for (com.starryforge.features.forging.ForgingRecipeManager.ForgingRecipe r : forgingManager.getRecipes()
                        .values()) {
                    if ((id != null && id.equalsIgnoreCase(r.getResultItem()))
                            || (id == null && item.getType().name().equalsIgnoreCase(r.getResultItem()))) {
                        RecipeDisplay nr = new RecipeDisplay(RecipeType.ASTRAL);
                        ItemStack input = plugin.getItemManager().getItem(r.getInputItem());
                        if (input == null) {
                            Material mat = Material.matchMaterial(r.getInputItem());
                            if (mat != null)
                                input = new ItemStack(mat);
                        }
                        if (input != null) {
                            nr.addIngredient(0, input);
                        }
                        return nr;
                    }
                }
            }
        } catch (Exception e) {
        }

        return new RecipeDisplay(RecipeType.NONE);
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("NexusCore")) {
            try {
                plugin.getLogger().info("NexusCore re-enabled. Attempting re-registration...");
                com.nexuscore.NexusCore.getInstance().getRegistry().registerProvider(this);
                plugin.getLogger().info("Re-registered items with NexusCore Nexus after reload.");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to re-register with NexusCore: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
