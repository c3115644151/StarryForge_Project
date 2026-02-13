package com.starryforge.features.ironheart.config;

import com.starryforge.StarryForge;
import com.starryforge.features.ironheart.data.model.WeaponComponent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class ResonanceConfig {

    private final StarryForge plugin;
    private final Map<String, Resonance> resonances = new HashMap<>();

    public ResonanceConfig(StarryForge plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        resonances.clear();
        File file = new File(plugin.getDataFolder(), "resonance.yml");
        if (!file.exists()) {
            plugin.saveResource("resonance.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("resonance");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) continue;

            String displayName = section.getString("display_name", key);
            List<String> requiredComponents = section.getStringList("required_components");
            String color = section.getString("color", "<white>");
            String hiddenLore = section.getString("hidden_lore");

            ConfigurationSection bonusSec = section.getConfigurationSection("bonus_stats");
            WeaponComponent.ComponentStats bonusStats = new WeaponComponent.ComponentStats(
                    bonusSec != null ? bonusSec.getDouble("damage", 0) : 0,
                    bonusSec != null ? bonusSec.getDouble("speed", 0) : 0,
                    bonusSec != null ? bonusSec.getDouble("reach", 0) : 0,
                    0, 0 // Resonance usually doesn't affect integrity
            );

            resonances.put(key, new Resonance(key, displayName, requiredComponents, bonusStats, color, hiddenLore));
        }
        plugin.getLogger().info("Loaded " + resonances.size() + " resonances.");
    }

    public Collection<Resonance> getAllResonances() {
        return Collections.unmodifiableCollection(resonances.values());
    }

    public record Resonance(
            String id,
            String displayName,
            List<String> requiredComponents,
            WeaponComponent.ComponentStats bonusStats,
            String color,
            String hiddenLore
    ) {}
}
