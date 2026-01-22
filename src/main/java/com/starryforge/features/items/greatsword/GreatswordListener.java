package com.starryforge.features.items.greatsword;

import com.starryforge.StarryForge;
import com.starryforge.utils.Keys;
import com.starryforge.features.core.PDCManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GreatswordListener implements Listener {

    private final StarryForge plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    // Blocking Start Time: UUID -> Start Time (ms)
    private final Map<UUID, Long> blockStartTimes = new HashMap<>();

    // Config Constants
    private static final long PARRY_WINDOW_MS = 500;
    private static final double FACING_THRESHOLD = 0.5; // ~60 degrees

    public GreatswordListener(StarryForge plugin) {
        this.plugin = plugin;
    }

    // --- Core Logic: Guard & Parry ---

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Only care about Right Click with Greatsword
        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            if (isGreatsword(item)) {
                
                // Record start time for Parry calculation
                blockStartTimes.put(player.getUniqueId(), System.currentTimeMillis());

                // Continuous Visual Feedback (Action Bar only)
                // We rely on the native "consumable" component for the blocking animation
                new BukkitRunnable() {
                @Override
                public void run() {
                    // Validation checks
                    if (!player.isOnline()) {
                        this.cancel();
                        return;
                    }
                    
                    // Check if player is still blocking (using native hand raised check)
                    // The "consumable" component keeps the hand raised while holding right click
                    if (!player.isHandRaised() || !isGreatsword(player.getInventory().getItemInMainHand())) {
                        this.cancel();
                        return;
                    }

                    // Calculate Parry Window status
                    long elapsedMs = System.currentTimeMillis() - blockStartTimes.getOrDefault(player.getUniqueId(), 0L);
                    boolean inParryWindow = elapsedMs <= PARRY_WINDOW_MS;

                    // Action Bar
                    if (inParryWindow) {
                        player.sendActionBar(mm.deserialize("<yellow><bold>[ ✦ 弹反判定 ✦ ]"));
                    } else {
                        player.sendActionBar(mm.deserialize("<gray>[ 🛡 格挡姿态 ]"));
                    }
                }
            }.runTaskTimer(plugin, 0L, 2L);
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Must be holding Greatsword
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isGreatsword(item)) {
            return;
        }
        
        // Must be actively blocking (Hand Raised)
        // Thanks to "consumable" component, isHandRaised() is true when holding right click
        if (!player.isHandRaised()) {
            return;
        }

        if (!(event.getDamager() instanceof LivingEntity attacker)) {
            return;
        }
        
        // 1. Directional Check (Dark Souls style)
        Vector playerLook = player.getEyeLocation().getDirection().normalize();
        Vector toAttacker = attacker.getEyeLocation().toVector().subtract(player.getEyeLocation().toVector()).normalize();
        double dot = playerLook.dot(toAttacker);

        if (dot < FACING_THRESHOLD) {
            return; // Not facing attacker
        }

        // 2. Parry Logic
        // Native 'blocks_attacks' component handles the base damage reduction (50%)
        // We only need to handle the Parry (Perfect Block) scenario
        
        long elapsed = System.currentTimeMillis() - blockStartTimes.getOrDefault(player.getUniqueId(), 0L);
        
        if (elapsed <= PARRY_WINDOW_MS) {
            // --- PARRY SUCCESS ---
            event.setCancelled(true); // Negate all damage
            
            // Visual/Audio Effects
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_REPAIR, 1.0f, 1.5f);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_HIT_GROUND, 1.0f, 0.5f);
            player.sendActionBar(mm.deserialize("<green><bold>✦ PARRY ✦"));
            
            // Counter Effect: Knockback Attacker
            Vector knockback = player.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize().multiply(-1.5).setY(0.5);
            attacker.setVelocity(knockback);
        }
        // If not in parry window, let the native component handle the damage reduction
    }

    private boolean isGreatsword(ItemStack item) {
        if (item == null) return false;
        String id = PDCManager.getString(item, Keys.ITEM_ID_KEY);
        return "greatsword".equals(id);
    }
}
