package com.starryforge.features.resonator;

import com.starryforge.StarryForge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class ResonatorListener implements Listener {

    private final ResonatorManager manager;

    public ResonatorListener(ResonatorManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!event.getAction().name().contains("RIGHT")) return;
        
        ItemStack item = event.getItem();
        if (StarryForge.getInstance().getItemManager().isCustomItem(item, "SEISMIC_RESONATOR")) {
            manager.toggleResonator(event.getPlayer());
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.stopResonator(event.getPlayer().getUniqueId());
    }
}
