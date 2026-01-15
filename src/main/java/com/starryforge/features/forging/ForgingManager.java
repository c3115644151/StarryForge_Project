package com.starryforge.features.forging;

import com.starryforge.StarryForge;
import com.starryforge.utils.SerializationUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 锻造会话管理器
 * <p>
 * 使用文件持久化存储会话数据，因为 SMITHING_TABLE 不是 TileEntity，无法使用 PDC
 */
public class ForgingManager implements Listener {

    private final StarryForge plugin;
    private final Map<Location, ForgingSession> sessions = new HashMap<>();
    private final File dataFile;

    public ForgingManager(StarryForge plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "altar_sessions.yml");
        loadSessions();
        startTickTask();
    }

    /**
     * 从文件加载所有会话
     */
    private void loadSessions() {
        sessions.clear();

        if (!dataFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection sessionsSection = config.getConfigurationSection("sessions");

        if (sessionsSection == null) {
            return;
        }

        int restoredCount = 0;
        for (String key : sessionsSection.getKeys(false)) {
            try {
                ConfigurationSection s = sessionsSection.getConfigurationSection(key);
                if (s == null) continue;

                // 解析位置
                String worldName = s.getString("world");
                World world = worldName != null ? Bukkit.getWorld(worldName) : null;
                if (world == null) {
                    plugin.getLogger().warning("[Forging] World not found: " + worldName + ", skipping session.");
                    continue;
                }

                int x = s.getInt("x");
                int y = s.getInt("y");
                int z = s.getInt("z");
                Location loc = new Location(world, x, y, z);

                // 解析玩家 ID
                String playerIdStr = s.getString("player_id");
                UUID playerId = playerIdStr != null ? UUID.fromString(playerIdStr) : null;

                // 创建会话
                ForgingSession session = new ForgingSession(loc, playerId);

                // 加载蓝图
                String blueprintData = s.getString("blueprint");
                if (blueprintData != null) {
                    ItemStack blueprint = SerializationUtils.itemFromBase64(blueprintData);
                    session.restoreBlueprint(blueprint);
                }

                // 加载锻造进度
                session.restoreProgress(
                    s.getInt("current_phase", 0),
                    s.getInt("max_phases", 0),
                    s.getDouble("quality_score", 0.0)
                );
                session.restoreLists(
                    s.getIntegerList("material_stars"),
                    s.getDoubleList("qte_scores")
                );

                // 加载当前材料和配方
                String ingotData = s.getString("current_ingot");
                ItemStack ingot = null;
                if (ingotData != null) {
                    ingot = SerializationUtils.itemFromBase64(ingotData);
                }
                
                String recipeId = s.getString("recipe_id");
                // 即使 ingot 为空，只要有 recipeId 也需要恢复状态（处于阶段之间）
                if (recipeId != null || ingot != null) {
                    session.restoreForging(ingot, recipeId);
                }

                // 初始化视觉效果
                session.initializeVisualsIfNeeded();

                sessions.put(loc, session);
                restoredCount++;
            } catch (Exception e) {
                plugin.getLogger().warning("[Forging] Failed to restore session: " + key);
                e.printStackTrace();
            }
        }

        if (restoredCount > 0) {
            plugin.getLogger().info("[Forging] Restored " + restoredCount + " altar session(s) from file.");
        }
    }

    public ForgingSession getSession(UUID playerId) {
        for (ForgingSession session : sessions.values()) {
            if (playerId.equals(session.getPlayerId())) {
                return session;
            }
        }
        return null;
    }

    /**
     * 保存所有会话到文件
     */
    public void saveSessions() {
        YamlConfiguration config = new YamlConfiguration();

        int index = 0;
        for (Map.Entry<Location, ForgingSession> entry : sessions.entrySet()) {
            Location loc = entry.getKey();
            ForgingSession session = entry.getValue();

            if (!session.hasBlueprint()) {
                continue; // 跳过没有蓝图的会话
            }

            String key = "sessions.session_" + index;

            // 保存位置
            config.set(key + ".world", loc.getWorld().getName());
            config.set(key + ".x", loc.getBlockX());
            config.set(key + ".y", loc.getBlockY());
            config.set(key + ".z", loc.getBlockZ());

            // 保存玩家 ID
            if (session.getPlayerId() != null) {
                config.set(key + ".player_id", session.getPlayerId().toString());
            }

            // 保存蓝图
            ItemStack blueprint = session.getTargetBlueprint();
            if (blueprint != null) {
                try {
                    config.set(key + ".blueprint", SerializationUtils.itemToBase64(blueprint));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // 保存锻造进度
            config.set(key + ".current_phase", session.getCurrentPhase());
            config.set(key + ".max_phases", session.getMaxPhases());
            config.set(key + ".quality_score", session.getQualityScore());
            config.set(key + ".material_stars", session.getMaterialStars());
            config.set(key + ".qte_scores", session.getQteScores());

            // 保存当前材料和配方
            ItemStack ingot = session.getCurrentIngot();
            if (ingot != null) {
                try {
                    config.set(key + ".current_ingot", SerializationUtils.itemToBase64(ingot));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            String recipeId = session.getActiveRecipeId();
            if (recipeId != null) {
                config.set(key + ".recipe_id", recipeId);
            }

            index++;
        }

        try {
            config.save(dataFile);
            if (index > 0) {
                plugin.getLogger().info("[Forging] Saved " + index + " altar session(s) to file.");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[Forging] Failed to save sessions!");
            e.printStackTrace();
        }
    }

    private void startTickTask() {
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.isEnabled()) {
                    cancel();
                    return;
                }
                for (ForgingSession session : sessions.values()) {
                    session.tick();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public void shutdown() {
        // 保存所有会话到文件
        saveSessions();

        // 清理视觉效果
        for (ForgingSession session : sessions.values()) {
            session.cleanup();
        }
        sessions.clear();
    }

    public ForgingSession getSession(Location loc) {
        return sessions.get(loc);
    }

    /**
     * 获取会话（文件持久化模式下直接从内存获取）
     */
    public ForgingSession getOrRestoreSession(Location loc) {
        return sessions.get(loc);
    }

    public void startSession(Location loc, ForgingSession session) {
        if (sessions.containsKey(loc)) {
            sessions.get(loc).cleanup();
        }
        sessions.put(loc, session);
        // 立即保存以防崩溃
        saveSessions();
    }

    public void endSession(Location loc) {
        ForgingSession session = sessions.remove(loc);
        if (session != null) {
            session.cleanup();
        }
        // 更新持久化文件
        saveSessions();
    }

    /**
     * 检查指定位置是否有会话
     */
    public boolean hasSession(Location loc) {
        return sessions.containsKey(loc);
    }

    /**
     * 通知管理器会话状态已变化，需要保存
     */
    public void markDirty() {
        // 延迟保存，避免频繁写入
        plugin.getServer().getScheduler().runTaskLater(plugin, this::saveSessions, 20L);
    }
}
