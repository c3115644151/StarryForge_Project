package com.starryforge.features.alloy;

import com.starryforge.StarryForge;
import com.starryforge.utils.Keys;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles the smelting of Mineral Slag in Vanilla Blast Furnaces
 * to recover a portion of the original special ore.
 */
public class SlagRecoveryListener implements Listener {
    private final MiniMessage mm = MiniMessage.miniMessage();

    public SlagRecoveryListener(StarryForge plugin) {
        // plugin not currently used, but kept in constructor for future needs
    }

    @EventHandler
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        if (event.getBlock().getType() != Material.BLAST_FURNACE) {
            return;
        }

        ItemStack source = event.getSource();
        if (source == null || !source.hasItemMeta()) {
            return;
        }

        ItemMeta meta = source.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Check if this is Mineral Slag
        String itemId = pdc.get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
        if (!"MINERAL_SLAG".equals(itemId)) {
            // This event matched our CHARCOAL -> CHARCOAL recipe, but the input is not
            // Slag.
            // We must cancel it to prevent regular Charcoal from being smelted into
            // Charcoal.
            if (event.getResult() != null && event.getResult().getType() == Material.CHARCOAL) {
                event.setCancelled(true);
            }
            return;
        }

        // Get stored data
        int stars = pdc.getOrDefault(Keys.SLAG_SPECIAL_ORE_STARS, PersistentDataType.INTEGER, 1);
        int amount = pdc.getOrDefault(Keys.SLAG_SPECIAL_ORE_AMOUNT, PersistentDataType.INTEGER, 1);

        ItemStack result = null;

        // 1. Try to recover full ItemStack (Best for custom items)
        String itemBase64 = pdc.get(Keys.SLAG_SPECIAL_ORE_ITEM, PersistentDataType.STRING);
        if (itemBase64 != null) {
            try {
                result = com.starryforge.utils.SerializationUtils.itemFromBase64(itemBase64);
            } catch (Exception e) {
                // Ignore, fallback to type
            }
        }

        // 2. Fallback to Type ID
        if (result == null) {
            String oreType = pdc.getOrDefault(Keys.SLAG_SPECIAL_ORE_TYPE, PersistentDataType.STRING, "IRON_ORE");
            Material oreMaterial = Material.matchMaterial(oreType.replace("BIOMEGIFTS:", ""));
            if (oreMaterial == null) {
                oreMaterial = Material.RAW_IRON;
            }
            result = new ItemStack(oreMaterial);
        }

        // Apply amount
        result.setAmount(amount);

        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta != null) {
            // Apply stars to the recovered ore
            // Note: If we recovered a full item, it might already have a name.
            // We should append stars if not present, or update the star rating.
            // But requirement implies the recovered ore SHOULD have the stars.

            // Check if name already has stars? Or just force overwrite like before?
            // "Recovered Mineral" name style seems specific to this plugin's feature.
            // However, user wants "High Energy Lignite" (Custom Name) back.
            // So if we have a custom item name, we should probably keep it and just ensure
            // stars are updated.

            // If it's a restored custom item, let's respect its name but maybe add star
            // indicator if missing
            // Actually, the PDC star rating is the one we want to enforce.

            resultMeta.getPersistentDataContainer().set(Keys.ORE_STAR_KEY, PersistentDataType.INTEGER, stars);

            // Update name with stars
            if (resultMeta.hasDisplayName()) {
                // Simple append for now
                // If name doesn't end with stars, append them?
                // It's hard to check Component content easily without serializing.
                // Let's just trust the item definition.
                // BUT: The user complaint was "Name is wrong, called Recovered Mineral".
                // So if we deserialized, we HAVE the correct name.
                // We just need to update the star visualization.

                // If we created from Material (Fallback), we set the name.
                if (itemBase64 == null) {
                    resultMeta.displayName(mm.deserialize("<gray>回收矿物 <yellow>" + "⭐".repeat(stars)));
                } else {
                    // It's a custom item. It might already have stars in name.
                    // We update the PDC, which is the important part for logic.
                    // Updating the visual name might duplicate stars if not careful.
                    // For now, let's leave the name alone if it's recovered, assuming the original
                    // item had stars in name.
                    // Or we can rebuild the name if we know the format.
                }
            } else {
                if (itemBase64 == null) {
                    resultMeta.displayName(mm.deserialize("<gray>回收矿物 <yellow>" + "⭐".repeat(stars)));
                }
            }

            result.setItemMeta(resultMeta);
        }

        // Replace the default result
        event.setResult(result);
    }
}
