package com.starryforge.features.items.shadowdagger;

import com.starryforge.StarryForge;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Visual effects for Shadow Dagger's Void Step.
 */
public class ShadowVisuals {

    public ShadowVisuals(StarryForge plugin) {
    }

    /**
     * Plays the heartbeat sound effect.
     * Should be called periodically (e.g., every 10-15 ticks) while Void Step is active.
     */
    public void playHeartbeat(Player player) {
        // Deep, heavy heartbeat sound
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_AMBIENT, 1.0f, 0.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.7f);
    }

    /**
     * Spawns a ghost trail behind the player.
     * Should be called every tick or every few ticks.
     */
    public void spawnGhostTrail(Player player) {
        Location loc = player.getLocation();
        
        // Use DustTransition for mixed Red/Purple effect
        Particle.DustTransition dustTransition = new Particle.DustTransition(
            Color.fromRGB(139, 0, 0), // Dark Red
            Color.fromRGB(75, 0, 130), // Indigo/Purple
            1.2f
        );
        
        // Spawn particles distributed around the player's body volume
        // We use a bounding box approximation (width 0.6, height 1.8)
        for (int i = 0; i < 5; i++) {
            // Random offset within body volume
            double offsetX = (Math.random() - 0.5) * 0.6;
            double offsetY = (Math.random() * 1.6) + 0.2; // 0.2 to 1.8 height
            double offsetZ = (Math.random() - 0.5) * 0.6;
            
            // Calculate spawn position relative to player's current position
            // To create a trail, we can slightly push it backwards based on movement
            // But simply spawning at current location is fine if called frequently.
            // To ensure it doesn't block vision, we apply a strict "behind" offset relative to look direction
            
            org.bukkit.util.Vector lookDir = player.getLocation().getDirection().setY(0).normalize();
            org.bukkit.util.Vector behindOffset = lookDir.clone().multiply(-0.5); // 0.5 blocks behind center
            
            Location spawnLoc = loc.clone().add(behindOffset).add(offsetX, offsetY, offsetZ);

            player.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, 
                    spawnLoc, 
                    1, 0, 0, 0, 0, dustTransition);
                    
            // Add subtle smoke for atmosphere
            if (Math.random() > 0.7) {
                 player.getWorld().spawnParticle(Particle.SMOKE, 
                    spawnLoc, 
                    0, 0, 0.05, 0, 0.02);
            }
        }
    }

    /**
     * Plays the activation effect (Entering Void Step).
     */
    public void playActivationEffect(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
        
        // Burst of particles
        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation().add(0, 1, 0), 50, 0.5, 1, 0.5, 0.1);
    }
}
