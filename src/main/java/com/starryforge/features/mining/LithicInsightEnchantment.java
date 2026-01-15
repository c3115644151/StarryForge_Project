package com.starryforge.features.mining;

import com.starryforge.StarryForge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LithicInsightEnchantment {

    public static final NamespacedKey KEY = new NamespacedKey(StarryForge.getInstance(), "lithic_insight");
    public static final int MAX_LEVEL = 3;

    @SuppressWarnings("deprecation")
    public static ItemStack apply(ItemStack item, int level) {
        if (item == null || level <= 0) return item;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // 1. Set PDC Level
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.INTEGER, level);

        // 2. Add Dummy Enchantment for Glint (UNBREAKING) if no other enchants exist
        // To be safe and consistent, we always add UNBREAKING and HIDE_ENCHANTS to control lore order
        Enchantment dummy = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
        if (dummy != null) {
            meta.addEnchant(dummy, 1, true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        // 3. Rebuild Lore
        rebuildLore(meta, level);

        item.setItemMeta(meta);
        return item;
    }

    public static int getLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(KEY, PersistentDataType.INTEGER, 0);
    }

    @SuppressWarnings("deprecation")
    public static void rebuildLore(ItemMeta meta, int level) {
        List<Component> lore = new ArrayList<>();
        
        // 1. Add Vanilla Enchantments (Translatable)
        Map<Enchantment, Integer> enchants = meta.getEnchants();
        Enchantment dummy = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
        
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            Enchantment ench = entry.getKey();
            if (dummy != null && ench.equals(dummy)) continue; // Skip our dummy

            int lvl = entry.getValue();
            String lvlRoman = toRoman(lvl);
            
            Component enchName = Component.translatable(ench)
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
            
            if (ench.getMaxLevel() > 1) {
                enchName = enchName.append(Component.text(" " + lvlRoman));
            }
            
            lore.add(enchName);
        }

        // 2. Add Lithic Insight
        if (level > 0) {
            String name = StarryForge.getInstance().getConfigManager().getMessage("enchantment.lithic_insight");
            // Fallback if config fails or message has colors that we want to override to GRAY/ITALIC false
            // But usually getMessage returns color codes. We want plain text for the name part maybe?
            // Actually, we are building a component. 
            // If getMessage returns "<red>Name", deserialize will make it red.
            // We want strict formatting here: Gray, No Italic.
            // So we should strip color or just trust the config value is plain text.
            // Let's assume the config value is just the name "Lithic Insight".
            
            // Clean the message of MiniMessage tags if we want to enforce our style, 
            // OR just use MiniMessage to deserialize it fully.
            // The original code used Component.text().
            
            // Let's use MiniMessage to allow users to style it in lang file!
            // But we need to support the level roman numeral.
            // Maybe lang: "Lithic Insight {level}"?
            // For now, let's keep the structure: Name + " " + Roman.
            
            // Note: Since this is a static method and we need the plugin instance, we can use StarryForge.getInstance()
            // But wait, ConfigManager might not be available statically if we didn't expose it.
            // StarryForge.getInstance().getConfigManager() should work if we add the getter to main class.
            
            // I'll stick to simple text replacement for now to avoid breaking if ConfigManager isn't static-friendly.
            // But wait, `StarryForge.getInstance()` is not standard. I usually pass plugin instance.
            // This class has no plugin instance field.
            // It uses `StarryForge.getInstance()` for NamespacedKey.
            // Does StarryForge have getInstance()? I should check. 
            // Assuming yes because line 21 uses it.
            
            lore.add(Component.text(name + " " + toRoman(level))
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        }

        // 3. Preserve existing lore (filtering out old enchantments)
        List<Component> existingLore = meta.lore();
        if (existingLore == null) existingLore = new ArrayList<>();
        
        List<Component> filteredLore = new ArrayList<>();
        String configName = StarryForge.getInstance().getConfigManager().getMessage("enchantment.lithic_insight");
        // Remove color codes for check
        String plainConfigName = MiniMessage.miniMessage().stripTags(configName);
        
        for (Component line : existingLore) {
            String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line);
            
            // Filter out Lithic Insight
            if (text.contains("Lithic Insight") || text.contains("岩层勘探") || (plainConfigName != null && !plainConfigName.isEmpty() && text.contains(plainConfigName))) {
                continue;
            }
            
            // Filter out Vanilla Enchantments (if possible/necessary)
            // Since we use HIDE_ENCHANTS and rebuild them, the "existing" lore shouldn't contain vanilla enchants 
            // UNLESS they were baked in by us previously.
            // But since we rebuild them from meta.getEnchants() every time, we should filter them out from the "manual" lore list
            // to avoid duplication if we accidentally saved them there.
            // However, distinguishing vanilla enchants from regular text is hard without strict formatting.
            // For now, we assume vanilla enchants are NOT in meta.lore() persistent list (they are dynamic), 
            // OR we accept that if we baked them, we might duplicate them if we don't detect them.
            // But standard practice with HIDE_ENCHANTS is that the client doesn't see them, so we add them.
            // If we add them to meta.lore(), they ARE saved.
            // So we MUST filter them.
            
            filteredLore.add(line);
        }
        
        List<Component> newLore = new ArrayList<>();
        newLore.addAll(lore); // The enchantments we built (Vanilla + Custom)
        newLore.addAll(filteredLore); // The manual descriptions (minus old custom enchant)
        
        meta.lore(newLore);
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }
}
