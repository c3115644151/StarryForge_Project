package com.starryforge.features.forging.gui;

import com.starryforge.StarryForge;
import com.starryforge.features.core.PDCManager;
import com.starryforge.features.forging.ForgingRecipeManager;
import com.starryforge.utils.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * 蓝图选择 GUI
 * <p>
 * 只有通过右键星魂台打开的 GUI 才能将蓝图写入为工程蓝图。
 */
public class BlueprintSelectionGUI implements Listener {

    private final StarryForge plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final NamespacedKey blueprintTargetKey;

    // 有效来源追踪
    private final Map<UUID, Location> validAltarSources = new HashMap<>();

    private String cachedTitle;

    public BlueprintSelectionGUI(StarryForge plugin) {
        this.plugin = plugin;
        this.blueprintTargetKey = new NamespacedKey(plugin, "blueprint_target");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private String getTitle() {
        if (cachedTitle == null) {
            cachedTitle = plugin.getConfigManager().getMessage("forging.gui.title");
        }
        return cachedTitle;
    }

    private Component getTitleComponent() {
        return mm.deserialize(getTitle());
    }

    /**
     * 从星魂祭坛打开 GUI（有效来源）
     */
    public void openFromAltar(Player player, Location altarLocation) {
        validAltarSources.put(player.getUniqueId(), altarLocation);
        openGUI(player);
    }

    private void openGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, getTitleComponent());

        ForgingRecipeManager recipeManager = plugin.getForgingRecipeManager();
        if (recipeManager == null) return;

        int slot = 0;
        for (ForgingRecipeManager.ForgingRecipe recipe : recipeManager.getRecipes().values()) {
            if (slot >= 54) break;
            inv.setItem(slot++, createRecipeIcon(recipe));
        }

        player.openInventory(inv);
    }

    private ItemStack createRecipeIcon(ForgingRecipeManager.ForgingRecipe recipe) {
        ItemStack icon = plugin.getItemManager().getItem(recipe.getResultItem());
        if (icon == null) {
            icon = new ItemStack(Material.PAPER);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(mm.deserialize("<red>Unknown: " + recipe.getResultItem()));
            icon.setItemMeta(meta);
        } else {
            icon = icon.clone();
        }

        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();

        lore.add(Component.empty());
        lore.add(mm.deserialize(getMessage("forging.gui.req").replace("{item}", recipe.getDisplayName())));
        lore.add(mm.deserialize(getMessage("forging.gui.difficulty").replace("{diff}", String.valueOf(recipe.getDifficulty()))));
        lore.add(mm.deserialize(getMessage("forging.gui.tier").replace("{tier}", String.valueOf(recipe.getTierRequired()))));
        lore.add(mm.deserialize(getMessage("forging.gui.click")));

        meta.lore(lore);
        icon.setItemMeta(meta);

        PDCManager.setString(icon, Keys.ITEM_ID_KEY, recipe.getResultItem());
        return icon;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().title().equals(getTitleComponent())) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !PDCManager.hasKey(clicked, Keys.ITEM_ID_KEY)) return;

        Player player = (Player) event.getWhoClicked();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);

        // 验证来源
        if (!validAltarSources.containsKey(player.getUniqueId())) {
            player.closeInventory();
            sendMessage(player, "forging.gui.invalid_source");
            return;
        }

        // 验证手持空白蓝图
        ItemStack hand = player.getInventory().getItemInMainHand();
        String handId = PDCManager.getString(hand, Keys.ITEM_ID_KEY);

        if (!"BLANK_BLUEPRINT".equals(handId)) {
            player.closeInventory();
            sendMessage(player, "forging.gui.no_blueprint_in_hand");
            return;
        }

        if (PDCManager.hasKey(hand, blueprintTargetKey)) {
            player.closeInventory();
            sendMessage(player, "forging.gui.already_written");
            return;
        }

        // 写入蓝图 (只转换一个)
        String selectedId = PDCManager.getString(clicked, Keys.ITEM_ID_KEY);
        String displayName = getItemDisplayName(clicked, selectedId);

        // 分离出一个空白蓝图进行转换
        ItemStack singleBlueprint = hand.clone();
        singleBlueprint.setAmount(1);
        writeBlueprint(singleBlueprint, selectedId, displayName);

        // 减少手中的空白蓝图数量
        hand.setAmount(hand.getAmount() - 1);

        // 将转换后的工程蓝图放入背包，满则掉落
        player.getInventory().addItem(singleBlueprint).values()
                .forEach(overflow -> player.getWorld().dropItemNaturally(player.getLocation(), overflow));

        String msg = getMessage("forging.gui.written").replace("{item}", displayName);
        player.sendMessage(mm.deserialize(msg));
        player.closeInventory();
        player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
    }

    private void writeBlueprint(ItemStack blueprint, String targetId, String displayName) {
        ItemMeta meta = blueprint.getItemMeta();

        // 更新名称
        String baseName = getMessage("items.written_blueprint.name");
        if (baseName.contains("Missing")) baseName = "<aqua>工程蓝图";
        meta.displayName(mm.deserialize(baseName + ": " + displayName));

        // 添加附魔光效
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        // 更新 Lore
        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize(getMessage("forging.gui.blueprint_lore_target").replace("{item}", displayName)));
        lore.add(mm.deserialize(getMessage("forging.gui.blueprint_lore_hint")));
        meta.lore(lore);

        blueprint.setItemMeta(meta);

        // 写入目标 ID
        PDCManager.setString(blueprint, blueprintTargetKey, targetId);
    }

    private String getItemDisplayName(ItemStack item, String fallback) {
        if (item.getItemMeta().hasDisplayName()) {
            return MiniMessage.miniMessage().serialize(item.getItemMeta().displayName());
        }
        return fallback;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().title().equals(getTitleComponent())) {
            validAltarSources.remove(event.getPlayer().getUniqueId());
        }
    }

    private String getMessage(String key) {
        return plugin.getConfigManager().getMessage(key);
    }

    private void sendMessage(Player player, String key) {
        player.sendMessage(mm.deserialize(getMessage(key)));
    }
}
