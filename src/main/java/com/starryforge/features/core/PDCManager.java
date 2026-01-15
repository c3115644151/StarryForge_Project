package com.starryforge.features.core;

import com.starryforge.utils.Keys;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class PDCManager {

    private PDCManager() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 通用 Getters/Setters
    public static void setString(ItemStack item, NamespacedKey key, String value) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
    }

    public static String getString(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public static void setDouble(ItemStack item, NamespacedKey key, double value) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, value);
        item.setItemMeta(meta);
    }

    public static double getDouble(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return 0.0;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.getOrDefault(key, PersistentDataType.DOUBLE, 0.0);
    }
    
    public static void setInt(ItemStack item, NamespacedKey key, int value) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
        item.setItemMeta(meta);
    }

    public static int getInt(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return 0;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.getOrDefault(key, PersistentDataType.INTEGER, 0);
    }
    
    public static boolean hasKey(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(key, PersistentDataType.STRING) ||
               pdc.has(key, PersistentDataType.DOUBLE) ||
               pdc.has(key, PersistentDataType.INTEGER);
    }

    // 特定温度方法
    public static double getTemperature(ItemStack item) {
        return getDouble(item, Keys.TEMPERATURE_KEY);
    }

    public static void setTemperature(ItemStack item, double temp) {
        setDouble(item, Keys.TEMPERATURE_KEY, temp);
    }
    
    public static boolean hasTemperature(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(Keys.TEMPERATURE_KEY, PersistentDataType.DOUBLE);
    }
}
