package com.starryforge.features.items;

import com.nexuscore.util.NexusKeys;
import com.nexuscore.rpg.provider.NexusStatProvider;
import com.nexuscore.rpg.stats.NexusStat;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StarryStatProvider implements NexusStatProvider {

    @Override
    public @Nullable String getNamespace() {
        return "starryforge";
    }

    @Override
    public double getStat(@NotNull ItemStack item, @NotNull NexusStat stat) {
        if (!item.hasItemMeta()) return 0;
        
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String itemId = pdc.get(NexusKeys.ITEM_ID, PersistentDataType.STRING);
        Integer star = pdc.get(NexusKeys.STAR_RATING, PersistentDataType.INTEGER);
        
        if (itemId == null || star == null) return 0;
        
        double val = StarryItemStats.getStat(itemId, star, stat);
        if (val > 0) {
            // Debug log to trace if stats are being read
            com.starryforge.StarryForge.getInstance().getLogger().info("[StatProvider] Providing " + stat + " for " + itemId + " (" + star + "*) = " + val);
        }
        return val;
    }
}
