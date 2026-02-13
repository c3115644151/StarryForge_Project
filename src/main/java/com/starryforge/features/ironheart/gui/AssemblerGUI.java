package com.starryforge.features.ironheart.gui;

import com.starryforge.StarryForge;
import com.starryforge.features.ironheart.config.BlueprintConfig;
import com.starryforge.features.ironheart.data.model.WeaponComponent;
import com.nexuscore.util.NexusKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * 组装台主界面 (Assembler)
 * 54格箱子界面，支持幻影物品渲染
 */
public class AssemblerGUI implements Listener {

    private final StarryForge plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    // Custom Block Identification
    private final NamespacedKey BLOCK_TYPE_KEY;
    private final NamespacedKey ORIGINAL_LORE_SIZE_KEY;
    private final String ASSEMBLER_BLOCK_ID = "assembler";

    // Slot Constants
    private final int SLOT_INPUT = 22; // Center

    // Component slots layout (Tetra style radiating)
    private final Map<WeaponComponent.ComponentType, Integer> TYPE_SLOTS = Map.of(
            WeaponComponent.ComponentType.HEAD, 13,
            WeaponComponent.ComponentType.SPINE, 4,
            WeaponComponent.ComponentType.GRIP, 31,
            WeaponComponent.ComponentType.GUARD, 21,
            WeaponComponent.ComponentType.WEIGHT, 23,
            WeaponComponent.ComponentType.SHAFT, 49);

    // Active sessions
    private final Map<UUID, GUIStateSession> sessions = new HashMap<>();

    public AssemblerGUI(StarryForge plugin) {
        this.plugin = plugin;
        this.BLOCK_TYPE_KEY = new NamespacedKey(plugin, "block_type");
        this.ORIGINAL_LORE_SIZE_KEY = new NamespacedKey(plugin, "gui_lore_size");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private String getTitle() {
        return plugin.getConfigManager().getMessage("assembler.gui.title");
    }

    public void open(Player player) {
        GUIStateSession session = sessions.computeIfAbsent(player.getUniqueId(), k -> new GUIStateSession(player));
        Inventory inv = Bukkit.createInventory(player, 54, mm.deserialize(getTitle()));

        render(inv, session);
        player.openInventory(inv);
    }

    /**
     * Renders the GUI based on current state.
     * Does NOT touch Slot 22 (Input) as it contains physical items.
     */
    private void render(Inventory inv, GUIStateSession session) {
        // 1. Fill Background
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.displayName(Component.empty());
        bg.setItemMeta(bgMeta);

        for (int i = 0; i < 54; i++) {
            // Skip Input Slot and Component Slots (initially)
            if (i == SLOT_INPUT)
                continue;

            // If IDLE, fill everything with BG
            if (session.getCurrentState() == GUIStateSession.State.IDLE) {
                inv.setItem(i, bg);
            } else {
                // If not IDLE, only fill slots that are NOT component slots
                if (!TYPE_SLOTS.containsValue(i)) {
                    inv.setItem(i, bg);
                }
            }
        }

        // 2. Render State-Specific Elements
        if (session.getCurrentState() == GUIStateSession.State.FABRICATION) {
            renderGhostSlots(inv, session);
        } else if (session.getCurrentState() == GUIStateSession.State.MODIFICATION) {
            // TODO: Render actual components retrieved from weapon NBT
            // For now, render placeholders or nothing (since components should be physical
            // items?)
            // If modification allows taking components out, they should be physical items.
            // But we can't "generate" physical items from NBT without risk of duplication
            // if not careful.
            // For Phase 1, strictly visual or ghost slots.
            // Design doc says: "Materialize components... Swap Protocol"
            // So we need to put physical items in slots.
            // For this refactor task, let's just support the State Switch first.
            plugin.getLogger().info("Rendering Modification Mode for " + session.getPlayer().getName());
        }
    }

    private void renderGhostSlots(Inventory inv, GUIStateSession session) {
        String bpId = session.getActiveBlueprintId();
        if (bpId == null)
            return;

        BlueprintConfig.Blueprint bp = plugin.getIronHeartManager().getBlueprintConfig().getBlueprint(bpId);
        if (bp == null)
            return;

        bp.requiredComponents().forEach((type, count) -> {
            Integer slot = TYPE_SLOTS.get(type);
            if (slot != null) {
                // If slot is empty or BG, show ghost item
                ItemStack current = inv.getItem(slot);
                if (current == null || current.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                    ItemStack ghost = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                    ItemMeta meta = ghost.getItemMeta();

                    String typeName = plugin.getConfigManager()
                            .getMessage("component_types." + type.name().toLowerCase());
                    if (typeName == null || typeName.contains("Missing"))
                        typeName = type.name();

                    // Display format: 1x Component Name
                    // Or use config message if available, for now hardcode or use simple format
                    String reqMsg = "<gray>" + count + "x " + typeName;

                    // Allow using the config format if it exists, reusing the blueprint lore format
                    // for consistency?
                    // "forging.gui.blueprint_lore_component_format": "<dark_gray>- <white>{amount}x
                    // {component}"
                    // We probably want a cleaner one for the GUI item name, e.g. "<red>需要:
                    // <white>{amount}x {component}"
                    String configFormat = plugin.getConfigManager().getMessage("assembler.gui.requirement");
                    if (configFormat != null && !configFormat.contains("Missing")) {
                        reqMsg = configFormat.replace("{component}", typeName)
                                .replace("{amount}", String.valueOf(count));
                    }

                    meta.displayName(mm.deserialize(reqMsg));
                    ghost.setItemMeta(meta);
                    inv.setItem(slot, ghost);
                }
            }
        });
    }

    private void updateSlotVisuals(ItemStack item, int current, int required) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();

        // Store original size if not present
        Integer originalSize = meta.getPersistentDataContainer().get(ORIGINAL_LORE_SIZE_KEY, PersistentDataType.INTEGER);
        if (originalSize == null) {
            originalSize = lore.size();
            meta.getPersistentDataContainer().set(ORIGINAL_LORE_SIZE_KEY, PersistentDataType.INTEGER, originalSize);
        }

        // Trim to original size
        while (lore.size() > originalSize) {
            lore.remove(lore.size() - 1);
        }

        // Add status line
        String color = current >= required ? "<green>" : "<red>";
        lore.add(mm.deserialize(color + "进度: " + current + "/" + required));

        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private ItemStack restoreItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return item;
        ItemMeta meta = item.getItemMeta();
        Integer originalSize = meta.getPersistentDataContainer().get(ORIGINAL_LORE_SIZE_KEY, PersistentDataType.INTEGER);
        if (originalSize != null) {
            List<Component> lore = meta.lore();
            if (lore != null) {
                while (lore.size() > originalSize) {
                    lore.remove(lore.size() - 1);
                }
                meta.lore(lore);
            }
            meta.getPersistentDataContainer().remove(ORIGINAL_LORE_SIZE_KEY);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;
        if (!event.getView().title().equals(mm.deserialize(getTitle())))
            return;

        int slot = event.getRawSlot();

        GUIStateSession session = sessions.get(player.getUniqueId());
        if (session == null)
            return;
        
        // Handle Shift-Clicking into the GUI (Must be checked BEFORE returning for player inventory slots)
        if (event.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (slot >= 54) {
                // plugin.getLogger().info("[Assembler] Shift-Click detected from slot " + slot);
                
                // Shift-click from Player Inventory -> Try to move to Input Slot
                
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem == null || clickedItem.getType().isAir()) return;

                // Check Input Slot
                ItemStack inputSlotItem = event.getInventory().getItem(SLOT_INPUT);
                
                // Manual Move Logic
                // If input slot is empty
                if (inputSlotItem == null || inputSlotItem.getType().isAir()) {
                    // plugin.getLogger().info("[Assembler] Input slot empty, moving item...");
                    event.getInventory().setItem(SLOT_INPUT, clickedItem.clone());
                    event.setCurrentItem(null); // Clear from player inventory
                    
                    // Force update inventory for client sync
                    if (event.getWhoClicked() instanceof Player p) {
                         p.updateInventory();
                    }

                    // Trigger State Update IMMEDIATELY
                    updateState(event.getInventory(), session);
                } 
                // If input slot has same item, try to stack
                else if (inputSlotItem.isSimilar(clickedItem)) {
                    int space = inputSlotItem.getMaxStackSize() - inputSlotItem.getAmount();
                    if (space > 0) {
                        int toMove = Math.min(space, clickedItem.getAmount());
                        inputSlotItem.setAmount(inputSlotItem.getAmount() + toMove);
                        clickedItem.setAmount(clickedItem.getAmount() - toMove);
                        
                        event.getInventory().setItem(SLOT_INPUT, inputSlotItem);
                        event.setCurrentItem(clickedItem.getAmount() > 0 ? clickedItem : null);
                        
                        if (event.getWhoClicked() instanceof Player p) {
                             p.updateInventory();
                        }
                        
                        // Trigger State Update
                        updateState(event.getInventory(), session);
                    }
                }
                
                // Always cancel default behavior because we handled it manually
                event.setCancelled(true);
                return; 
            }
        }
        
        // Return if clicking in player inventory (and not handled above)
        if (slot >= 54)
            return; 

        // Allow interaction with Input Slot
        if (slot == SLOT_INPUT) {
            // Let the event happen, then check result
            Bukkit.getScheduler().runTask(plugin, () -> updateState(event.getInventory(), session));
            return;
        }

    // Component slots logic
        if (TYPE_SLOTS.containsValue(slot)) {
            // Allow interaction only if in Fabrication/Modification mode
            if (session.getCurrentState() == GUIStateSession.State.IDLE) {
                event.setCancelled(true);
                return;
            }

            // Find which component type this slot corresponds to
            WeaponComponent.ComponentType targetType = TYPE_SLOTS.entrySet().stream()
                    .filter(e -> e.getValue() == slot)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);

            if (targetType == null) return; // Should not happen

            // Get Blueprint Requirement
            String bpId = session.getActiveBlueprintId();
            if (bpId == null) {
                event.setCancelled(true);
                return;
            }
            BlueprintConfig.Blueprint bp = plugin.getIronHeartManager().getBlueprintConfig().getBlueprint(bpId);
            if (bp == null) {
                event.setCancelled(true);
                return;
            }

            int requiredCount = bp.requiredComponents().getOrDefault(targetType, 0);
            if (requiredCount == 0) {
                // This component is not required for this blueprint
                event.setCancelled(true);
                return;
            }

            ItemStack currentSlotItem = event.getCurrentItem();
            ItemStack cursorItem = event.getCursor();
            
            boolean isGhost = currentSlotItem != null && currentSlotItem.getType() == Material.RED_STAINED_GLASS_PANE;
            boolean isSlotEmpty = currentSlotItem == null || currentSlotItem.getType().isAir() || isGhost;
            
            // Interaction Logic:
            // 1. Place Item (Cursor -> Slot)
            if (cursorItem != null && !cursorItem.getType().isAir()) {
                // Validate Item Type
                String itemId = null;
                PersistentDataContainer pdc = cursorItem.getItemMeta().getPersistentDataContainer();
                
                // 1. Try Standard Key
                itemId = pdc.get(NexusKeys.ITEM_ID, PersistentDataType.STRING);
                
                // 2. Try Legacy Key
                if (itemId == null) {
                    itemId = pdc.get(com.starryforge.utils.Keys.ITEM_ID_KEY, PersistentDataType.STRING);
                }

                // 3. Robust Search: Iterate ALL string keys if specific keys failed
                // This handles cases where the key might be slightly different or namespaced differently
                if (itemId == null) {
                    for (NamespacedKey key : pdc.getKeys()) {
                        if (pdc.has(key, PersistentDataType.STRING)) {
                            String value = pdc.get(key, PersistentDataType.STRING);
                            if (value != null && plugin.getIronHeartManager().getComponentConfig().getComponent(value) != null) {
                                itemId = value;
                                // plugin.getLogger().info("Found valid component ID via scan: " + key + " -> " + value);
                                break;
                            }
                        }
                    }
                }
                
                // Debug logging for troubleshooting
                if (itemId == null) {
                    /*
                    plugin.getLogger().info("DEBUG: Item rejected. Keys found:");
                    for (NamespacedKey key : pdc.getKeys()) {
                        plugin.getLogger().info(" - " + key.toString());
                    }
                    */
                    event.setCancelled(true);
                    return;
                }
                
                WeaponComponent component = plugin.getIronHeartManager().getComponentConfig().getComponent(itemId);
                if (component == null || component.type() != targetType) {
                    // Wrong component type
                    // plugin.getLogger().info("Invalid component type: " + (component == null ? "null" : component.type()) + " vs " + targetType);
                    event.setCancelled(true);
                    return;
                }

                // Calculate Amount
                // Safe check: if isSlotEmpty is false, currentSlotItem IS NOT null.
                int currentAmount = 0;
                if (!isSlotEmpty && currentSlotItem != null) {
                    currentAmount = currentSlotItem.getAmount();
                }
                
                int needed = requiredCount - currentAmount;
                
                if (needed <= 0) {
                    // Already full
                    event.setCancelled(true);
                    return;
                }
                
                // Perform Move
                int toAdd = Math.min(needed, cursorItem.getAmount());
                
                ItemStack newSlotItem;
                if (isSlotEmpty) {
                    newSlotItem = cursorItem.clone();
                    newSlotItem.setAmount(toAdd);
                } else {
                    // Must match existing item (Bukkit checks this usually, but we are manual)
                    // Wait! If slot is NOT empty, we check similarity.
                    // But if it IS empty (ghost), we just replace.
                    // HOWEVER, if isGhost is TRUE, isSlotEmpty is TRUE.
                    // So we enter the IF block above.
                    // So we don't need to check similarity here if it's a ghost.
                    
                    if (currentSlotItem == null || !currentSlotItem.isSimilar(cursorItem)) {
                         event.setCancelled(true);
                         return;
                    }
                    newSlotItem = currentSlotItem.clone();
                    newSlotItem.setAmount(currentAmount + toAdd);
                }
                
                // Update Visuals (Lore)
                updateSlotVisuals(newSlotItem, newSlotItem.getAmount(), requiredCount);
                
                event.setCurrentItem(newSlotItem);
                
                // Update Cursor
                ItemStack newCursor = cursorItem.clone();
                newCursor.setAmount(cursorItem.getAmount() - toAdd);
                event.getWhoClicked().setItemOnCursor(newCursor.getAmount() > 0 ? newCursor : null);
                
                event.setCancelled(true); // Handled manually
                
                if (event.getWhoClicked() instanceof Player p) {
                    p.updateInventory();
                }
                return;
            }
            
            // 2. Pickup Item (Slot -> Cursor)
            // Only if slot has real item (not ghost)
            if (!isSlotEmpty && currentSlotItem != null) {
                // Restore item (remove GUI lore)
                ItemStack restored = restoreItem(currentSlotItem.clone());
                
                // Handle split/pickup (Vanilla logic is complex, let's simplify: pickup all or half?)
                // For simplicity: Pickup All (Left Click) or Half (Right Click)
                // Let's implement Pickup All for now to avoid bugs
                
                event.getWhoClicked().setItemOnCursor(restored);
                event.setCurrentItem(null); // Will be replaced by Ghost on next render/update?
                // Actually we should trigger updateState or renderGhostSlots if empty
                // But renderGhostSlots is called in render().
                // If we set null here, next tick render might fix it?
                // Let's set it to null, and let renderGhostSlots handle it if we call it.
                // But updateState calls render only if state changes? No.
                
                // We should manually restore ghost slot if empty?
                // Actually, renderGhostSlots checks: if (current == null || current.getType() == Material.GRAY_STAINED_GLASS_PANE)
                // So if we set null, renderGhostSlots will fill it.
                // But we need to trigger render.
                
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player p) {
                    p.updateInventory();
                    // Schedule a render update to show ghost item immediately
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (event.getInventory().getItem(slot) == null) {
                             renderGhostSlots(event.getInventory(), session);
                        }
                    });
                }
                return;
            }
            
            // If clicking empty/ghost slot with empty cursor -> Cancel
            event.setCancelled(true);
            return;
        }

        // Cancel all other slots (Background)
        // But allow Shift-Click from player inventory to Input Slot?
        // IF player clicks in their own inventory (slot >= 54)
        // AND action is MOVE_TO_OTHER_INVENTORY
        // We should allow it IF it goes to input slot.
        // But the event.getRawSlot() is the clicked slot.

        // Handle Shift-Clicking into the GUI
        // REMOVED: Moved to top of method to avoid early return
        
        // Handle Hotbar Swap (Number Keys) into Input Slot
        if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
            // If hovering over SLOT_INPUT
            if (slot == SLOT_INPUT) {
                // Let the swap happen naturally, but we must update state afterwards
                event.setCancelled(false);
                
                // We must use runTask here because the swap hasn't happened yet in the event
                Bukkit.getScheduler().runTask(plugin, () -> {
                     updateState(event.getInventory(), session);
                });
                return;
            }
        }

        event.setCancelled(true);
    }

    private void updateState(Inventory inv, GUIStateSession session) {
        ItemStack input = inv.getItem(SLOT_INPUT);
        // plugin.getLogger().info("[Assembler] updateState check. Input: " + (input == null ? "null" : input.getType()));

        // 1. Check for Blueprint
        String blueprintId = com.starryforge.features.core.PDCManager.getString(input,
                com.starryforge.utils.Keys.BLUEPRINT_TARGET);
        
        // plugin.getLogger().info("[Assembler] Blueprint ID found: " + blueprintId);
        
        if (blueprintId != null) {
            if (session.getCurrentState() != GUIStateSession.State.FABRICATION
                    || !blueprintId.equals(session.getActiveBlueprintId())) {
                session.transitionTo(GUIStateSession.State.FABRICATION);
                session.setActiveBlueprintId(blueprintId);
                render(inv, session);
                
                String msg = plugin.getConfigManager().getMessage("assembler.current_blueprint");
                
                String displayName = blueprintId;
                // Try to resolve display name
                BlueprintConfig.Blueprint bp = plugin.getIronHeartManager().getBlueprintConfig().getBlueprint(blueprintId);
                if (bp != null) {
                    displayName = bp.displayName();
                    // Remove "蓝图" suffix if present to look cleaner in sentence
                    if (displayName.endsWith("蓝图")) {
                         displayName = displayName.substring(0, displayName.length() - 2);
                    }
                }
                
                if (msg != null && !msg.contains("Missing")) {
                     session.getPlayer().sendMessage(mm.deserialize(msg.replace("{name}", displayName)));
                } else {
                     session.getPlayer().sendMessage(mm.deserialize("<green>已识别蓝图: " + displayName));
                }
            }
            return;
        }

        // 2. Check for Weapon (Modification Data)
        // FIXME: Use correct key "starfield:forge_data" or similar when weapon handling
        // is fully implemented.
        // Currently we just check if the item is not empty and reset if necessary.

        // We can just check if session needs reset
        if (input == null || input.getType().isAir()) {
            if (session.getCurrentState() != GUIStateSession.State.IDLE) {
                session.reset();
                render(inv, session); // Re-render triggers background fill
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().title().equals(mm.deserialize(getTitle()))) {
            // Drop input item if needed?
            // It's a chest inventory, items stay in it?
            // "54格箱子界面" usually implies a virtual inventory if we create it via
            // Bukkit.createInventory.
            // If it's a TileEntity (Barrel), we are opening the Barrel's inventory?
            // In open(), we did Bukkit.createInventory, so it is VIRTUAL.
            // So items will be lost if we don't return them!
            // We MUST return items in Input Slot and Component Slots.

            Inventory inv = event.getInventory();
            Player player = (Player) event.getPlayer();

            // Return Input
            returnItem(player, inv.getItem(SLOT_INPUT));

            // Return Components
            TYPE_SLOTS.values().forEach(slot -> {
                ItemStack item = inv.getItem(slot);
                if (item != null && item.getType() != Material.GRAY_STAINED_GLASS_PANE
                        && item.getType() != Material.RED_STAINED_GLASS_PANE) {
                    // Restore item before returning
                    returnItem(player, restoreItem(item));
                }
            });

            sessions.remove(player.getUniqueId());
        }
    }

    private void returnItem(Player player, ItemStack item) {
        if (item != null && !item.getType().isAir()) {
            player.getInventory().addItem(item).values()
                    .forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
        }
    }

    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        if (event.getClickedBlock() == null)
            return;

        if (event.getClickedBlock().getType() == Material.BARREL) {
            if (event.getClickedBlock().getState() instanceof TileState state) {
                String type = state.getPersistentDataContainer().get(BLOCK_TYPE_KEY, PersistentDataType.STRING);
                if (ASSEMBLER_BLOCK_ID.equals(type)) {
                    event.setCancelled(true);
                    if (!event.getPlayer().isSneaking() || event.getItem() == null) {
                        open(event.getPlayer());
                    }
                }
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getType() != Material.BARREL)
            return;

        String itemId = null;
        if (item.hasItemMeta()) {
            itemId = item.getItemMeta().getPersistentDataContainer().get(NexusKeys.ITEM_ID, PersistentDataType.STRING);
        }

        if ("assembly_table".equals(itemId)) {
            if (event.getBlockPlaced().getState() instanceof TileState state) {
                state.getPersistentDataContainer().set(BLOCK_TYPE_KEY, PersistentDataType.STRING, ASSEMBLER_BLOCK_ID);
                state.update();
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.BARREL)
            return;

        if (event.getBlock().getState() instanceof TileState state) {
            String type = state.getPersistentDataContainer().get(BLOCK_TYPE_KEY, PersistentDataType.STRING);
            if (ASSEMBLER_BLOCK_ID.equals(type)) {
                event.setDropItems(false);
                ItemStack assemblerItem = plugin.getItemManager().getItem("assembly_table");
                if (assemblerItem != null) {
                    event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), assemblerItem);
                } else {
                    event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(),
                            new ItemStack(Material.BARREL));
                }
            }
        }
    }
}
