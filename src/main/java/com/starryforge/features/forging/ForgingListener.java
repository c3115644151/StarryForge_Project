package com.starryforge.features.forging;

import com.starryforge.StarryForge;
import com.starryforge.features.core.PDCManager;
import com.starryforge.utils.Keys;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 星魂共振锻造监听器
 * <p>
 * 交互流程：
 * 1. 手持空白蓝图右键星魂台 → 打开 GUI 选择目标
 * 2. 选择完成后 → 手中空白蓝图变成工程蓝图
 * 3. 手持工程蓝图右键星魂台 → 蓝图消耗，铺展在台上
 * 4. 再次右键可取出蓝图（有进度时需 Shift+右键 确认）
 */
public class ForgingListener implements Listener {

    private final StarryForge plugin;
    private final ForgingManager manager;
    private final ForgingRecipeManager recipeManager;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final NamespacedKey blueprintTargetKey;

    // 蓝图取出确认机制
    private final Map<UUID, Long> retrieveConfirmTimestamps = new HashMap<>();

    public ForgingListener(StarryForge plugin) {
        this.plugin = plugin;
        this.manager = plugin.getForgingManager();
        this.recipeManager = plugin.getForgingRecipeManager();
        this.blueprintTargetKey = new NamespacedKey(plugin, "blueprint_target");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND)
            return;

        Player player = event.getPlayer();
        Action action = event.getAction();

        // 1. Left Click Logic (Hit Nodes)
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            ForgingSession session = manager.getSession(player.getUniqueId());
            if (session != null && session.isForging()) {
                session.handleHit(player);
                event.setCancelled(true);
                return;
            }
        }

        // 2. Right Click Logic (Anvil Interaction)
        if (action == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block == null || block.getType() != Material.SMITHING_TABLE)
                return;

            // Check MultiBlock
            if (plugin.getMultiBlockManager().checkStructure(block, "astral_altar") == null)
                return;

            event.setCancelled(true);
            Location loc = block.getLocation();
            ItemStack item = player.getInventory().getItemInMainHand();
            ForgingSession session = manager.getOrRestoreSession(loc);

            handleRightClick(player, loc, item, session);
        }
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND)
            return;

        if (event.getRightClicked() instanceof ItemDisplay || event.getRightClicked() instanceof TextDisplay) {
            // Find the Smithing Table below the display
            Location entityLoc = event.getRightClicked().getLocation();
            Block candidate = entityLoc.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);

            // Check 1 block down (ItemDisplay)
            if (candidate.getType() != Material.SMITHING_TABLE) {
                // Check 2 blocks down (TextDisplay)
                candidate = candidate.getRelative(org.bukkit.block.BlockFace.DOWN);
            }

            if (candidate.getType() != Material.SMITHING_TABLE) {
                return;
            }

            Location blockLoc = candidate.getLocation();
            ForgingSession session = manager.getOrRestoreSession(blockLoc);

            event.setCancelled(true);
            handleRightClick(event.getPlayer(), blockLoc, event.getPlayer().getInventory().getItemInMainHand(),
                    session);
        }
    }

    private void handleRightClick(Player player, Location loc, ItemStack item, ForgingSession session) {
        String itemId = PDCManager.getString(item, Keys.ITEM_ID_KEY);

        // 0. Handle Completed Session (Retrieve Result)
        if (session != null && session.isCompleted()) {
            retrieveResult(player, loc, session);
            return;
        }

        // 手持空白蓝图
        if ("BLANK_BLUEPRINT".equals(itemId)) {
            handleBlueprintInteraction(player, loc, item, session);
            return;
        }

        // 空手交互
        if (item.getType() == Material.AIR) {
            // 如果没有内存会话，再次尝试恢复（确保获取最新状态）
            if (session == null) {
                session = manager.getOrRestoreSession(loc);
            }
            if (player.isSneaking()) {
                handleBlueprintRetrieval(player, loc, session);
            } else {
                showSessionInfo(player, session);
            }
            return;
        }

        // 放置材料
        if (session != null && session.hasBlueprint()) {
            if (session.isForging()) {
                sendMessage(player, "forging.process.busy"); // Need to add this key or use raw message
                return;
            }
            ForgingRecipeManager.ForgingRecipe recipe = recipeManager.getRecipeByInput(item);
            if (recipe != null) {
                handleMaterialPlacement(player, session, item, recipe);
            }
        }
    }

    private void handleBlueprintInteraction(Player player, Location loc, ItemStack blueprint, ForgingSession session) {
        boolean isWritten = PDCManager.hasKey(blueprint, blueprintTargetKey);

        if (isWritten) {
            // 工程蓝图 → 铺展
            placeWrittenBlueprint(player, loc, session, blueprint);
        } else {
            // 空白蓝图 → 提示在空气中打开
            sendMessage(player, "forging.blueprint.use_in_air_hint");
        }
    }

    private void placeWrittenBlueprint(Player player, Location loc, ForgingSession session, ItemStack blueprint) {
        if (session != null && session.hasBlueprint()) {
            sendMessage(player, "forging.blueprint.already_placed");
            return;
        }

        ForgingSession newSession = new ForgingSession(loc, player.getUniqueId());
        newSession.setBlueprint(blueprint.clone());
        manager.startSession(loc, newSession);

        blueprint.setAmount(blueprint.getAmount() - 1);

        sendMessage(player, "forging.blueprint.set");
        sendMessage(player, "forging.blueprint.hint");
        player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 0.8f);
    }

    private void retrieveResult(Player player, Location loc, ForgingSession session) {
        ItemStack result = session.getResultItem();
        if (result != null) {
            player.getInventory().addItem(result).values()
                    .forEach(overflow -> player.getWorld().dropItemNaturally(player.getLocation(), overflow));

            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
            sendMessage(player, "forging.process.retrieved"); // Need to ensure lang key exists or use hardcoded for now
        }

        manager.endSession(loc);
    }

    private void handleBlueprintRetrieval(Player player, Location loc, ForgingSession session) {
        if (session == null || !session.hasBlueprint()) {
            sendMessage(player, "forging.anvil.no_blueprint");
            return;
        }

        // 检查是否有进度 (roundsCompleted > 0) 而不仅是 isForging (currentIngot != null)
        boolean hasProgress = session.getCurrentPhase() > 0;

        if (!hasProgress) {
            // 无进度，直接取出
            performBlueprintRetrieval(player, loc, session);
            return;
        }

        // 有进度，需要二次确认
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastAttempt = retrieveConfirmTimestamps.get(playerId);

        long RETRIEVE_CONFIRM_TIMEOUT_MS = plugin.getConfigManager()
                .getInt("machines.astral_altar.settings.confirm_timeout_ms", 5000);

        if (lastAttempt != null && (now - lastAttempt) < RETRIEVE_CONFIRM_TIMEOUT_MS) {
            // 确认窗口内，执行取出
            performBlueprintRetrieval(player, loc, session);
            retrieveConfirmTimestamps.remove(playerId);
        } else {
            // 首次尝试，发出警告
            sendMessage(player, "forging.blueprint.retrieve_warning");
            sendMessage(player, "forging.blueprint.retrieve_confirm");
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            retrieveConfirmTimestamps.put(playerId, now);

            // 定时清理并通知
            long RETRIEVE_CONFIRM_TIMEOUT_TICKS = RETRIEVE_CONFIRM_TIMEOUT_MS / 50;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Long stored = retrieveConfirmTimestamps.get(playerId);
                if (stored != null && stored == now) {
                    retrieveConfirmTimestamps.remove(playerId);
                    if (player.isOnline()) {
                        sendMessage(player, "forging.blueprint.retrieve_cancelled");
                    }
                }
            }, RETRIEVE_CONFIRM_TIMEOUT_TICKS);
        }
    }

    private void performBlueprintRetrieval(Player player, Location loc, ForgingSession session) {
        ItemStack blueprint = session.getTargetBlueprint();
        if (blueprint != null) {
            ItemStack returnItem = blueprint.clone();
            returnItem.setAmount(1);
            player.getInventory().addItem(returnItem).values()
                    .forEach(overflow -> player.getWorld().dropItemNaturally(player.getLocation(), overflow));
        }

        manager.endSession(loc);
        sendMessage(player, "forging.blueprint.retrieve_empty");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
    }

    private void showSessionInfo(Player player, ForgingSession session) {
        String key;
        if (session == null || !session.hasBlueprint()) {
            key = "forging.anvil.no_blueprint";
        } else if (session.isForging()) {
            String msg = plugin.getConfigManager().getMessage("forging.anvil.in_progress")
                    .replace("{phase}", String.valueOf(session.getCurrentPhase()))
                    .replace("{max_phase}", String.valueOf(session.getMaxPhases()))
                    .replace("{score}", String.format("%.1f", session.getQualityScore()));
            player.sendActionBar(mm.deserialize(msg));
            return;
        } else {
            key = "forging.anvil.empty_hand_hint";
        }
        player.sendActionBar(mm.deserialize(plugin.getConfigManager().getMessage(key)));
    }

    private void handleMaterialPlacement(Player player, ForgingSession session, ItemStack item,
            ForgingRecipeManager.ForgingRecipe recipe) {
        double temp = PDCManager.getTemperature(item);
        if (temp < recipe.getMinTemperature()) {
            String msg = plugin.getConfigManager().getMessage("forging.material.temp_low")
                    .replace("{required}", String.valueOf(recipe.getMinTemperature()))
                    .replace("{current}", String.format("%.0f", temp));
            player.sendMessage(mm.deserialize(msg));
            return;
        }

        String blueprintTarget = session.getTargetId();
        if (blueprintTarget != null && !blueprintTarget.equals(recipe.getResultItem())) {
            String msg = plugin.getConfigManager().getMessage("forging.material.invalid_target")
                    .replace("{item}", blueprintTarget);
            player.sendMessage(mm.deserialize(msg));
            return;
        }

        ItemStack forgingItem = item.clone();
        forgingItem.setAmount(1);
        item.setAmount(item.getAmount() - 1);

        sendMessage(player, "forging.material.placed");
        session.processMaterialInput(forgingItem, recipe);
    }

    private void sendMessage(Player player, String key) {
        player.sendMessage(mm.deserialize(plugin.getConfigManager().getMessage(key)));
    }
}
