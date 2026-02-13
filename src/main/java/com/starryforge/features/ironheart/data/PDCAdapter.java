package com.starryforge.features.ironheart.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.starryforge.features.ironheart.data.model.IronHeartWeapon;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class PDCAdapter {

    private static final Gson GSON = new GsonBuilder().create();
    private static final NamespacedKey FORGE_DATA_KEY = new NamespacedKey("starfield", "forge_data");

    /**
     * Write IronHeartWeapon data to ItemStack PDC
     */
    public static void writeWeaponData(ItemStack item, IronHeartWeapon weapon) {
        if (item == null || weapon == null) return;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        String json = GSON.toJson(weapon);
        meta.getPersistentDataContainer().set(FORGE_DATA_KEY, PersistentDataType.STRING, json);
        item.setItemMeta(meta);
    }

    /**
     * Read IronHeartWeapon data from ItemStack PDC
     */
    public static IronHeartWeapon readWeaponData(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(FORGE_DATA_KEY, PersistentDataType.STRING)) return null;

        String json = pdc.get(FORGE_DATA_KEY, PersistentDataType.STRING);
        try {
            return GSON.fromJson(json, IronHeartWeapon.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Check if item is an IronHeart weapon
     */
    public static boolean isIronHeartWeapon(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(FORGE_DATA_KEY, PersistentDataType.STRING);
    }
}
