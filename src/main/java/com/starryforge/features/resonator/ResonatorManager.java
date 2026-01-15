package com.starryforge.features.resonator;

import com.starryforge.StarryForge;
import com.starryforge.features.core.NoiseManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ResonatorManager {

    private final StarryForge plugin;
    private final NoiseManager noiseManager;
    private final Map<UUID, BukkitRunnable> activeTasks = new HashMap<>();
    private final MiniMessage mm = MiniMessage.miniMessage();

    public ResonatorManager(StarryForge plugin, NoiseManager noiseManager) {
        this.plugin = plugin;
        this.noiseManager = noiseManager;
    }

    public boolean isPlayerResonating(Player player) {
        return activeTasks.containsKey(player.getUniqueId());
    }

    public void toggleResonator(Player player) {
        UUID uuid = player.getUniqueId();
        if (activeTasks.containsKey(uuid)) {
            stopResonator(uuid);
            player.sendMessage(mm.deserialize(plugin.getConfigManager().getMessage("resonator.toggle_off")));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.5f);
        } else {
            startResonator(player);
            player.sendMessage(mm.deserialize(plugin.getConfigManager().getMessage("resonator.toggle_on")));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
        }
    }

    public void stopResonator(UUID uuid) {
        BukkitRunnable task = activeTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    private void startResonator(Player player) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    this.cancel();
                    activeTasks.remove(player.getUniqueId());
                    return;
                }
                
                // 检查手中是否持有仪器 (Check Item ID via PDC) - 支持主手和副手
                ItemStack main = player.getInventory().getItemInMainHand();
                ItemStack off = player.getInventory().getItemInOffHand();
                boolean hasResonator = (main != null && plugin.getItemManager().isCustomItem(main, "SEISMIC_RESONATOR")) ||
                                       (off != null && plugin.getItemManager().isCustomItem(off, "SEISMIC_RESONATOR"));

                if (!hasResonator) {
                     this.cancel();
                     activeTasks.remove(player.getUniqueId());
                     player.sendMessage(mm.deserialize(plugin.getConfigManager().getMessage("resonator.toggle_off")));
                     return;
                }

                Location loc = player.getLocation();
                double potency = noiseManager.getRawPotency(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                
                playRadarSound(player, potency);
                sendTechnicalFeedback(player, potency);
            }
        };
        
        long interval = plugin.getConfigManager().getInt("resonator.update_interval_ticks", 40);
        task.runTaskTimer(plugin, 0L, interval);
        activeTasks.put(player.getUniqueId(), task);
    }

    private void playRadarSound(Player player, double potency) {
        double soundHigh = plugin.getConfigManager().getDouble("resonator.thresholds.sound_high", 0.8);
        double weak = plugin.getConfigManager().getDouble("resonator.thresholds.weak", 0.3);

        if (potency > soundHigh) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 2.0f, 2.0f);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 2.0f); 
        } else if (potency > weak) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1.0f, 0.5f);
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_OFF, 0.5f, 0.5f);
        }
    }

    private void sendTechnicalFeedback(Player player, double potency) {
        String signalStatus;
        String colorTag;
        
        double critical = plugin.getConfigManager().getDouble("resonator.thresholds.critical", 0.9);
        double high = plugin.getConfigManager().getDouble("resonator.thresholds.high", 0.7);
        double weak = plugin.getConfigManager().getDouble("resonator.thresholds.weak", 0.3);

        if (potency > critical) {
            signalStatus = plugin.getConfigManager().getMessage("resonator.status.critical");
            colorTag = "<dark_red><b>";
        } else if (potency > high) {
            signalStatus = plugin.getConfigManager().getMessage("resonator.status.high");
            colorTag = "<red>";
        } else if (potency > weak) {
            signalStatus = plugin.getConfigManager().getMessage("resonator.status.weak");
            colorTag = "<yellow>";
        } else {
            signalStatus = plugin.getConfigManager().getMessage("resonator.status.none");
            colorTag = "<gray>";
        }

        double flux = (System.currentTimeMillis() % 1000) / 100.0;
        String format = plugin.getConfigManager().getMessage("resonator.tech_data_format");

        String techData = String.format(format, 
            (potency * 100) + flux, 
            (Math.random() * 360)
        );

        String msgTemplate = plugin.getConfigManager().getMessage("resonator.actionbar");
        String message = msgTemplate
            .replace("{color}", colorTag)
            .replace("{status}", signalStatus)
            .replace("{tech_data}", techData);
            
        player.sendActionBar(mm.deserialize(message));
    }
}
