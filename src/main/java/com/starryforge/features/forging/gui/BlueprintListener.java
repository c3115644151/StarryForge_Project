package com.starryforge.features.forging.gui;

import com.starryforge.StarryForge;
import com.starryforge.features.core.PDCManager;
import com.starryforge.utils.Keys;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class BlueprintListener implements Listener {

    private final StarryForge plugin;
    private final NamespacedKey blueprintTargetKey;

    public BlueprintListener(StarryForge plugin) {
        this.plugin = plugin;
        this.blueprintTargetKey = new NamespacedKey(plugin, "blueprint_target");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Only handle Right Click (Air or Block)
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Only Main Hand? Usually best.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null)
            return;

        // Check if item is BLANK_BLUEPRINT
        String id = PDCManager.getString(item, Keys.ITEM_ID_KEY);
        if (!"BLANK_BLUEPRINT".equals(id)) {
            return;
        }

        // Check if it's already written (should receive "WRITTEN_BLUEPRINT" ID usually,
        // but check PDC too)
        if (PDCManager.hasKey(item, blueprintTargetKey)) {
            return; // It's a written blueprint, do nothing (or let block interaction happen)
        }

        // It is a Blank Blueprint -> Open GUI
        event.setCancelled(true); // Prevent placing it or other interactions
        plugin.getBlueprintGUI().openGUI(event.getPlayer());
    }
}
