package com.starryforge.features.forging;

import com.starryforge.StarryForge;
import com.starryforge.features.core.PDCManager;
import com.starryforge.utils.Keys;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ForgingRecipeManager {

    private final StarryForge plugin;
    private final Map<String, ForgingRecipe> recipes = new HashMap<>();

    public ForgingRecipeManager(StarryForge plugin) {
        this.plugin = plugin;
        loadrecipes();
    }

    public void loadrecipes() {
        File file = new File(plugin.getDataFolder(), "forging_recipes.yml");
        if (!file.exists()) {
            plugin.saveResource("forging_recipes.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("recipes");

        recipes.clear();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(key);
                if (s == null)
                    continue;

                String inputId = s.getString("input_item");
                String resultId = s.getString("result_item");
                double minTemperature = s.getDouble("min_temperature", 300.0);
                int strikes = s.getInt("strikes_required", 10);
                double difficulty = s.getDouble("difficulty", 1.0);
                int tier = s.getInt("tier_required", 1);
                String displayName = s.getString("display_name", key);

                ForgingRecipe recipe = new ForgingRecipe(key, inputId, resultId, minTemperature, strikes, difficulty,
                        tier, displayName);
                recipes.put(key, recipe);
            }
        }
        plugin.getLogger().info("[Forging] Loaded " + recipes.size() + " recipes.");
    }

    public void reload() {
        loadrecipes();
    }

    public ForgingRecipe getRecipeByInput(ItemStack item) {
        if (item == null)
            return null;
        String id = PDCManager.getString(item, Keys.ITEM_ID_KEY);
        if (id == null)
            return null; // Or verify vanilla material if id is null?

        for (ForgingRecipe r : recipes.values()) {
            if (r.getInputItem().equals(id)) {
                return r;
            }
        }
        return null;
    }

    public ForgingRecipe getRecipeById(String id) {
        return recipes.get(id);
    }

    public Map<String, ForgingRecipe> getRecipes() {
        return recipes;
    }

    public static class ForgingRecipe {
        private final String id;
        private final String inputItem;
        private final String resultItem;
        private final double minTemperature;
        private final int strikesRequired;
        private final double difficulty;
        private final int tierRequired;
        private final String displayName;

        public ForgingRecipe(String id, String inputItem, String resultItem, double minTemp, int strikes, double diff,
                int tier, String name) {
            this.id = id;
            this.inputItem = inputItem;
            this.resultItem = resultItem;
            this.minTemperature = minTemp;
            this.strikesRequired = strikes;
            this.difficulty = diff;
            this.tierRequired = tier;
            this.displayName = name;
        }

        public String getId() {
            return id;
        }

        public String getInputItem() {
            return inputItem;
        }

        public String getResultItem() {
            return resultItem;
        }

        public double getMinTemperature() {
            return minTemperature;
        }

        public int getStrikesRequired() {
            return strikesRequired;
        }

        public double getDifficulty() {
            return difficulty;
        }

        public int getTierRequired() {
            return tierRequired;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
