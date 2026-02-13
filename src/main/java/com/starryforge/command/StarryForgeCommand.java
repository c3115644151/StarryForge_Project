package com.starryforge.command;

import com.starryforge.StarryForge;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.starryforge.features.ironheart.config.BlueprintConfig;

public class StarryForgeCommand implements CommandExecutor, TabCompleter {

    private final StarryForge plugin;

    public StarryForgeCommand(StarryForge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("starryforge"))
            return false;
        if (args.length == 0)
            return false;

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "reload":
                if (sender.hasPermission("starryforge.admin")) {
                    plugin.reloadPlugin();
                    sender.sendMessage(
                            MiniMessage.miniMessage().deserialize("<green>StarryForge configuration reloaded!"));
                    return true;
                }
                break;
            case "debug":
                return handleDebug(sender, args);
            case "cedebug":
                return handleCEDebug(sender, args);
            case "give":
                return handleGive(sender, args);
            case "blueprint":
                return handleBlueprint(sender, args);
            case "list":
                if (sender.hasPermission("starryforge.admin")) {
                    sender.sendMessage(MiniMessage.miniMessage()
                            .deserialize(plugin.getConfigManager().getMessage("commands.list_header")));
                    String tooltip = plugin.getConfigManager().getMessage("commands.list_item_tooltip");
                    for (String id : plugin.getItemManager().getItemNames()) {
                        sender.sendMessage(MiniMessage.miniMessage().deserialize("<gray>- <click:run_command:'/sf give "
                                + id + "'><hover:show_text:'" + tooltip + "'>" + id + "</hover></click>"));
                    }
                    return true;
                }
                break;
            case "visualizenoise":
                return handleVisualizeNoise(sender);
        }
        return true;
    }

    private boolean handleBlueprint(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(
                    MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("commands.no_console")));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Usage: /sf blueprint <blueprint_id>"));
            return true;
        }

        String id = args[1].toLowerCase();
        // Update to IronHeartManager
        // For Phase 1 migration, createBlueprintItem is not available directly on
        // BlueprintConfig.
        // We will just verify it exists for now and give a dummy item or reimplement
        // logic.
        // Re-implementing basic logic here for Phase 1.
        BlueprintConfig.Blueprint bp = plugin.getIronHeartManager().getBlueprintConfig().getBlueprint(id);

        if (bp != null) {
            ItemStack item = new ItemStack(Material.PAPER); // Placeholder
            ItemMeta meta = item.getItemMeta();
            meta.displayName(MiniMessage.miniMessage().deserialize("<aqua>" + bp.displayName()));
            // Set PDC for identification
            com.starryforge.features.core.PDCManager.setString(item, com.starryforge.utils.Keys.BLUEPRINT_TARGET, id);
            item.setItemMeta(meta);

            player.getInventory().addItem(item).values()
                    .forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
            player.sendMessage(MiniMessage.miniMessage().deserialize("<green>Given blueprint: " + id));
        } else {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Unknown blueprint ID: " + id));
        }
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("item")) {
            if (sender instanceof Player player) {
                if (player.hasPermission("starryforge.debug")) {
                    ItemStack item = player.getInventory().getItemInMainHand();
                    if (item.getType() == Material.AIR) {
                        player.sendMessage(
                                MiniMessage.miniMessage().deserialize("<red>You are not holding an item.</red>"));
                        return true;
                    }
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        player.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Item PDC Keys:</yellow>"));
                        for (NamespacedKey pdcKey : meta.getPersistentDataContainer().getKeys()) {
                            Object value = null;
                            try {
                                value = meta.getPersistentDataContainer().get(pdcKey, PersistentDataType.STRING);
                            } catch (Exception ignored) {
                            }
                            if (value == null) {
                                try {
                                    value = meta.getPersistentDataContainer().get(pdcKey, PersistentDataType.INTEGER);
                                } catch (Exception ignored) {
                                }
                            }
                            String valStr = value != null ? value.toString() : "[unknown type]";
                            player.sendMessage(MiniMessage.miniMessage()
                                    .deserialize("<gray> - " + pdcKey + " = " + valStr + "</gray>"));
                        }
                    }
                    return true;
                }
            } else {
                sender.sendMessage(MiniMessage.miniMessage()
                        .deserialize(plugin.getConfigManager().getMessage("commands.no_console")));
                return true;
            }
        }

        if (sender instanceof Player player) {
            if (player.hasPermission("starryforge.debug")) {
                plugin.getDebugStickManager().giveDebugStick(player);
                return true;
            }
        } else {
            sender.sendMessage(
                    MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("commands.no_console")));
            return true;
        }
        return true;
    }

    private boolean handleCEDebug(CommandSender sender, String[] args) {
        if (sender.hasPermission("starryforge.admin")) {
            if (args.length < 2) {
                sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Usage: /sf cedebug <ce_item_id></red>"));
                return true;
            }
            String ceItemId = args[1];
            try {
                org.bukkit.plugin.Plugin cePlugin = plugin.getServer().getPluginManager().getPlugin("CraftEngine");
                if (cePlugin == null) {
                    sender.sendMessage(
                            MiniMessage.miniMessage().deserialize("<red>CraftEngine plugin not found!</red>"));
                    return true;
                }

                Object api = null;
                try {
                    api = cePlugin.getClass().getMethod("getAPI").invoke(cePlugin);
                } catch (Exception ignored) {
                }

                if (api == null)
                    api = cePlugin;

                Object itemManager = null;
                for (java.lang.reflect.Method m : api.getClass().getMethods()) {
                    if (m.getName().equalsIgnoreCase("getItemManager") || m.getName().equalsIgnoreCase("item")) {
                        itemManager = m.invoke(api);
                        break;
                    }
                }

                if (itemManager == null) {
                    sender.sendMessage(MiniMessage.miniMessage()
                            .deserialize("<red>Could not find ItemManager in CraftEngine.</red>"));
                    return true;
                }

                Class<?> keyClass = Class.forName("net.kyori.adventure.key.Key");
                Object keyObj = keyClass.getMethod("key", String.class).invoke(null, ceItemId);

                Object result = null;
                try {
                    result = itemManager.getClass().getMethod("build", keyClass).invoke(itemManager, keyObj);
                } catch (NoSuchMethodException e) {
                    try {
                        result = itemManager.getClass().getMethod("getItem", String.class).invoke(itemManager,
                                ceItemId);
                    } catch (NoSuchMethodException e2) {
                        result = itemManager.getClass().getMethod("get", keyClass).invoke(itemManager, keyObj);
                    }
                }

                if (result instanceof ItemStack ceItem) {
                    sender.sendMessage(
                            MiniMessage.miniMessage().deserialize("<green>Got CE item: " + ceItemId + "</green>"));
                    ItemMeta meta = ceItem.getItemMeta();
                    if (meta != null) {
                        sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>PDC Keys:</yellow>"));
                        for (NamespacedKey pdcKey : meta.getPersistentDataContainer().getKeys()) {
                            String value = meta.getPersistentDataContainer().get(pdcKey, PersistentDataType.STRING);
                            if (value == null)
                                value = "[non-string]";
                            sender.sendMessage(MiniMessage.miniMessage()
                                    .deserialize("<gray> - " + pdcKey + " = " + value + "</gray>"));
                        }
                        if (meta.hasCustomModelData()) {
                            sender.sendMessage(MiniMessage.miniMessage()
                                    .deserialize("<yellow>CMD: " + meta.getCustomModelData() + "</yellow>"));
                        } else {
                            sender.sendMessage(
                                    MiniMessage.miniMessage().deserialize("<gray>No CMD (client-bound mode)</gray>"));
                        }
                    }
                } else {
                    sender.sendMessage(MiniMessage.miniMessage()
                            .deserialize("<red>CE returned null (or not ItemStack) for: " + ceItemId + "</red>"));
                }
            } catch (Exception e) {
                sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Error: " + e.getMessage() + "</red>"));
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(
                    MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("commands.usage_give")));
            return true;
        }
        if (sender instanceof Player player) {
            if (player.hasPermission("starryforge.admin")) {
                String id = args[1].toUpperCase();
                ItemStack item;

                if ("UNIDENTIFIED_CLUSTER".equals(id)) {
                    int stars = 1;
                    int amount = 1;
                    if (args.length > 2)
                        try {
                            stars = Integer.parseInt(args[2]);
                        } catch (NumberFormatException ignored) {
                        }
                    if (args.length > 3)
                        try {
                            amount = Integer.parseInt(args[3]);
                        } catch (NumberFormatException ignored) {
                        }

                    item = plugin.getItemManager().createUnidentifiedCluster(stars);
                    if (item != null)
                        item.setAmount(amount);
                } else {
                    item = plugin.getItemManager().getItem(id);
                    if (item != null) {
                        int amount = 1;
                        int stars = -1;
                        if (args.length > 2)
                            try {
                                stars = Integer.parseInt(args[2]);
                            } catch (NumberFormatException ignored) {
                            }
                        if (args.length > 3)
                            try {
                                amount = Integer.parseInt(args[3]);
                            } catch (NumberFormatException ignored) {
                            }

                        if (stars != -1) {
                            ItemMeta meta = item.getItemMeta();
                            int finalStars = Math.max(1, Math.min(5, stars));
                            meta.getPersistentDataContainer().set(com.nexuscore.util.NexusKeys.STAR_RATING,
                                    PersistentDataType.INTEGER, finalStars);
                            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "nexus_has_star"),
                                    PersistentDataType.INTEGER, 1);
                            item.setItemMeta(meta);
                            com.nexuscore.NexusCore.getInstance().getTierVisuals().applyVisuals(item, finalStars, true);
                            try {
                                com.nexuscore.NexusCore.getInstance().getRpgManager().updateItemAttributes(item);
                            } catch (Exception e) {
                                plugin.getLogger()
                                        .warning("Failed to update item attributes in /sf give: " + e.getMessage());
                            }
                        }
                        item.setAmount(amount);
                    }
                }

                if (item != null) {
                    player.getInventory().addItem(item);
                    String msg = plugin.getConfigManager().getMessage("commands.given").replace("{item}", id);
                    player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
                } else {
                    String msg = plugin.getConfigManager().getMessage("commands.unknown_item").replace("{id}", id);
                    player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
                }
                return true;
            }
        } else {
            sender.sendMessage(
                    MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("commands.no_console")));
            return true;
        }
        return false;
    }

    private boolean handleVisualizeNoise(CommandSender sender) {
        if (sender instanceof Player player && player.hasPermission("starryforge.debug")) {
            player.sendMessage(MiniMessage.miniMessage()
                    .deserialize(plugin.getConfigManager().getMessage("commands.visualize_noise_start")));
            Location origin = player.getLocation();
            int radius = 5;
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        Location loc = origin.clone().add(x, y, z);
                        double potency = plugin.getNoiseManager().getRawPotency(loc.getBlockX(), loc.getBlockY(),
                                loc.getBlockZ());

                        org.bukkit.Particle.DustOptions dust;
                        if (potency > 0.8)
                            dust = new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.0f);
                        else if (potency > 0.5)
                            dust = new org.bukkit.Particle.DustOptions(org.bukkit.Color.YELLOW, 0.8f);
                        else
                            dust = new org.bukkit.Particle.DustOptions(org.bukkit.Color.GRAY, 0.5f);

                        if (potency > 0.3) {
                            player.spawnParticle(org.bukkit.Particle.DUST, loc.add(0.5, 0.5, 0.5), 1, 0, 0, 0, 0, dust);
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("starryforge"))
            return null;
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("starryforge.debug")) {
                completions.add("debug");
                completions.add("visualizenoise");
            }
            if (sender.hasPermission("starryforge.admin")) {
                completions.add("give");
                completions.add("blueprint");
                completions.add("list");
                completions.add("reload");
            }
            return completions.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give") && sender.hasPermission("starryforge.admin")) {
                return plugin.getItemManager().getItemNames().stream().filter(s -> s.startsWith(args[1].toUpperCase()))
                        .collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("blueprint") && sender.hasPermission("starryforge.admin")) {
                return plugin.getIronHeartManager().getBlueprintConfig().getAllBlueprints().stream()
                        .map(BlueprintConfig.Blueprint::id)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }
}
