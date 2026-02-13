package com.starryforge.features.ironheart.config;

import com.starryforge.StarryForge;
import com.starryforge.features.ironheart.data.model.WeaponComponent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class BlueprintConfig {

    private final StarryForge plugin;
    private final Map<String, Blueprint> blueprints = new LinkedHashMap<>();

    public BlueprintConfig(StarryForge plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        blueprints.clear();
        File file = new File(plugin.getDataFolder(), "blueprints.yml");
        if (!file.exists()) {
            plugin.saveResource("blueprints.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("blueprints");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) continue;

            String targetItem = section.getString("target_item");
            String displayName = section.getString("display_name");
            List<String> description = section.getStringList("description");

            Map<WeaponComponent.ComponentType, Integer> requirements = new EnumMap<>(WeaponComponent.ComponentType.class);
            ConfigurationSection compSec = section.getConfigurationSection("components");
            if (compSec != null) {
                for (String typeKey : compSec.getKeys(false)) {
                    // Map "COMPONENT_EDGE" -> "HEAD" if needed, or use direct enum names
                    // Assuming YAML uses "HEAD", "GRIP" etc. or we map legacy keys
                    // Let's assume YAML uses standard enum names for Phase 1 simplicity, or map legacy "COMPONENT_EDGE"
                    WeaponComponent.ComponentType type = parseType(typeKey);
                    if (type != null) {
                        requirements.put(type, compSec.getInt(typeKey));
                    }
                }
            }

            blueprints.put(key, new Blueprint(key, targetItem, displayName, description, requirements));
        }
        plugin.getLogger().info("Loaded " + blueprints.size() + " IronHeart blueprints.");
    }

    private WeaponComponent.ComponentType parseType(String key) {
        // Legacy mapping support
        return switch (key) {
            case "COMPONENT_EDGE", "HEAD" -> WeaponComponent.ComponentType.HEAD;
            case "COMPONENT_SPINE", "SPINE" -> WeaponComponent.ComponentType.SPINE;
            case "COMPONENT_GRIP", "GRIP" -> WeaponComponent.ComponentType.GRIP;
            case "COMPONENT_WEIGHT", "WEIGHT" -> WeaponComponent.ComponentType.WEIGHT;
            case "COMPONENT_GUARD", "GUARD" -> WeaponComponent.ComponentType.GUARD;
            case "COMPONENT_SHAFT", "SHAFT" -> WeaponComponent.ComponentType.SHAFT;
            default -> null;
        };
    }

    public Blueprint getBlueprint(String id) {
        return blueprints.get(id);
    }

    public Collection<Blueprint> getAllBlueprints() {
        return Collections.unmodifiableCollection(blueprints.values());
    }

    public record Blueprint(
            String id,
            String targetItem,
            String displayName,
            List<String> description,
            Map<WeaponComponent.ComponentType, Integer> requiredComponents
    ) {}
}
