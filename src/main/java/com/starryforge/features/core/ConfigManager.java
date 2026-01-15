package com.starryforge.features.core;

import com.starryforge.StarryForge;
import com.starryforge.utils.LogUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {

    private final StarryForge plugin;
    private FileConfiguration langConfig;
    private boolean debug;
    private FileConfiguration recipesConfig;

    public ConfigManager(StarryForge plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.debug = config.getBoolean("debug", false);

        loadLang();
        loadRecipes();
    }

    private void loadRecipes() {
        File recipesFile = new File(plugin.getDataFolder(), "recipes.yml");
        if (!recipesFile.exists()) {
            plugin.saveResource("recipes.yml", false);
        }
        recipesConfig = YamlConfiguration.loadConfiguration(recipesFile);
        LogUtil.debug("Loaded recipes configuration.");
    }

    public FileConfiguration getRecipesConfig() {
        if (recipesConfig == null)
            loadRecipes();
        return recipesConfig;
    }

    private void loadLang() {
        String locale = plugin.getConfig().getString("settings.locale", "zh_CN");
        File langFile = new File(plugin.getDataFolder(), "lang/" + locale + ".yml");

        // 如果语言文件不存在，尝试从资源中保存
        if (!langFile.exists()) {
            try {
                plugin.saveResource("lang/" + locale + ".yml", false);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Language file " + locale + ".yml not found in jar resources!");
            }
        }

        if (langFile.exists()) {
            langConfig = YamlConfiguration.loadConfiguration(langFile);
            LogUtil.debug("Loaded language file: " + locale);
        } else {
            plugin.getLogger().severe("Could not load language file: " + langFile.getPath());
            langConfig = new YamlConfiguration();
        }

        // Load defaults from JAR
        try {
            java.io.InputStream defLangStream = plugin.getResource("lang/" + locale + ".yml");
            if (defLangStream != null) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(defLangStream, java.nio.charset.StandardCharsets.UTF_8));
                langConfig.setDefaults(defConfig);

                // Manual Merge to ensure keys are present and savable
                boolean changed = false;
                for (String key : defConfig.getKeys(true)) {
                    if (!langConfig.contains(key)) {
                        langConfig.set(key, defConfig.get(key));
                        changed = true;
                    }
                }

                if (changed) {
                    try {
                        langConfig.save(langFile);
                        LogUtil.debug("Updated language file with new keys from JAR.");
                    } catch (Exception e) {
                        plugin.getLogger().warning("Could not save updated language file: " + e.getMessage());
                    }
                }
            } else {
                plugin.getLogger().warning("Default language resource not found: lang/" + locale + ".yml");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load default language file from JAR: " + e.getMessage());
        }
    }

    public boolean isDebug() {
        return debug;
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public String getMessage(String path) {
        if (langConfig == null)
            return "<red>Lang not loaded";
        return langConfig.getString("messages." + path, "<red>Missing message: " + path);
    }

    public List<String> getMessageList(String path) {
        if (langConfig == null)
            return new ArrayList<>();
        return langConfig.getStringList("messages." + path);
    }

    public double getDouble(String path, double def) {
        return plugin.getConfig().getDouble(path, def);
    }

    public int getInt(String path, int def) {
        return plugin.getConfig().getInt(path, def);
    }
}
