package com.starryforge.utils;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Standard logging utility for StarryForge.
 * Follows the debug-system workflow.
 */
public class LogUtil {
    private static JavaPlugin plugin;

    public static void init(JavaPlugin instance) {
        plugin = instance;
    }

    /**
     * Log a debug message if debug mode is enabled in config.yml.
     * 
     * @param message The message to log.
     */
    public static void debug(String message) {
        if (plugin != null && plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }

    /**
     * Log an error message and the stack trace if debug mode is enabled.
     * 
     * @param message The error message.
     * @param e       The exception.
     */
    public static void error(String message, Throwable e) {
        if (plugin != null) {
            plugin.getLogger().severe(message);
            if (plugin.getConfig().getBoolean("debug", false)) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Log a standard info message.
     * 
     * @param message The message to log.
     */
    public static void info(String message) {
        if (plugin != null) {
            plugin.getLogger().info(message);
        }
    }
}
