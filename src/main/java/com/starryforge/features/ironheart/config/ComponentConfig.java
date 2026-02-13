package com.starryforge.features.ironheart.config;

import com.starryforge.StarryForge;
import com.starryforge.features.ironheart.data.model.WeaponComponent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

public class ComponentConfig {

    private final StarryForge plugin;
    private final Map<String, WeaponComponent> components = new HashMap<>();

    public ComponentConfig(StarryForge plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        components.clear();
        File file = new File(plugin.getDataFolder(), "components.yml");
        if (!file.exists()) {
            plugin.saveResource("components.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            try {
                ConfigurationSection section = config.getConfigurationSection(key);
                if (section == null) continue;

                WeaponComponent component = parseComponent(key, section);
                components.put(key, component);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load component: " + key, e);
            }
        }
        plugin.getLogger().info("Loaded " + components.size() + " IronHeart components.");
    }

    private WeaponComponent parseComponent(String id, ConfigurationSection section) {
        WeaponComponent.ComponentType type = WeaponComponent.ComponentType.valueOf(section.getString("type"));
        String name = section.getString("name");
        String itemId = section.getString("item_id");
        int cmd = section.getInt("cmd");

        ConfigurationSection statsSec = section.getConfigurationSection("stats");
        WeaponComponent.ComponentStats stats = new WeaponComponent.ComponentStats(
                statsSec != null ? statsSec.getDouble("damage", 0) : 0,
                statsSec != null ? statsSec.getDouble("speed", 0) : 0,
                statsSec != null ? statsSec.getDouble("reach", 0) : 0,
                statsSec != null ? statsSec.getInt("integrity_provider", 0) : 0,
                statsSec != null ? statsSec.getInt("integrity_cost", 0) : 0
        );

        ConfigurationSection reqSec = section.getConfigurationSection("requirements");
        WeaponComponent.ComponentRequirements reqs = new WeaponComponent.ComponentRequirements(
                reqSec != null ? reqSec.getInt("forge_level", 0) : 0
        );

        List<String> abilities = section.getStringList("abilities");

        return new WeaponComponent(id, type, name, itemId, cmd, stats, reqs, abilities);
    }

    public WeaponComponent getComponent(String id) {
        if (id == null) return null;
        // Normalize ID: remove namespace if present
        if (id.contains(":")) {
            String[] parts = id.split(":");
            id = parts[parts.length - 1];
        }
        
        // Try direct match first
        WeaponComponent c = components.get(id);
        if (c != null) return c;
        
        // Try uppercase match (components.yml keys are uppercase)
        c = components.get(id.toUpperCase());
        if (c != null) return c;

        // Try lowercase match
        return components.get(id.toLowerCase());
    }

    public Map<String, WeaponComponent> getAllComponents() {
        return Collections.unmodifiableMap(components);
    }
}
