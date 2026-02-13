package com.starryforge.features.alloy;

import com.starryforge.StarryForge;
import com.starryforge.features.multiblock.MultiBlockManager;
import com.starryforge.features.scrap.ScrapManager;
import com.starryforge.utils.Keys;
import com.starryforge.utils.LogUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.NamespacedKey;

import com.nexuscore.util.NexusKeys;
import com.starryforge.utils.SerializationUtils;
import com.starryforge.features.core.PDCManager;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.Random;

public class AlloyManager implements Listener {

    // Recipe Inner Class
    public record AlloyRecipe(
            String resultId,
            Map<String, Integer> inputs,
            int maxStrikes,
            double outputTemperature,
            double sweetSpotMult,
            double heatRateMult,
            double coolingMult,
            boolean hasQuality) {
    }

    private final StarryForge plugin;
    private final MultiBlockManager multiBlockManager;
    private final MiniMessage mm = MiniMessage.miniMessage();

    // 活跃熔炼会话：玩家 UUID -> 会话
    private final Map<UUID, SmeltingSession> sessions = new HashMap<>();
    private final Random random = new Random();

    // 配置参数
    private List<Integer> slotsInput;
    private int slotFlux;
    private int slotStart;
    private int slotOutput;

    private final List<AlloyRecipe> recipes = new ArrayList<>();

    private double physicsGravity;
    private double physicsBoostForce;
    private double physicsMaxTemp;
    private double physicsSweetSpotBaseWidth;

    private int gameMaxStrikes;
    private int gameStrikeCooldown;
    private int gameOverheatTicks;
    private double gameQualityReward;
    private double gameQualityPenalty;

    private final Map<FluxType, FluxModifier> fluxModifiers = new HashMap<>();

    public record FluxModifier(double heatRate, double sweetSpotSize, double sweetSpotMoveSpeed, double inertia) {
    }

    public AlloyManager(StarryForge plugin, MultiBlockManager multiBlockManager) {
        this.plugin = plugin;
        this.multiBlockManager = multiBlockManager;
        loadConfig();
    }

    public List<AlloyRecipe> getRecipes() {
        return recipes;
    }

    public void loadConfig() {
        plugin.reloadConfig();
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("machines.alloy_forge");

        // 确保配置部分存在，否则给默认值
        if (cfg == null) {
            plugin.getLogger().warning("Missing machines.alloy_forge config section! Using defaults.");
            slotsInput = List.of(10, 11, 12);
            slotFlux = 4;
            slotStart = 13;
            slotOutput = 15;
            physicsGravity = -0.15;
            physicsBoostForce = 2.5;
            physicsMaxTemp = 100.0;
            physicsSweetSpotBaseWidth = 10.0;
            gameMaxStrikes = 5;
            gameStrikeCooldown = 500;
            gameOverheatTicks = 60;
            gameQualityReward = 1.0;
            gameQualityPenalty = 0.5;
        } else {
            slotsInput = cfg.getIntegerList("gui.slots_input");
            if (slotsInput.isEmpty()) {
                // 兼容旧配置
                int oldSlot = cfg.getInt("gui.slot_input", 11);
                slotsInput = List.of(oldSlot);
            }
            slotFlux = cfg.getInt("gui.slot_flux", 4);
            slotStart = cfg.getInt("gui.slot_start", 13);
            slotOutput = cfg.getInt("gui.slot_output", 15);

            physicsGravity = cfg.getDouble("physics.gravity", -0.15);
            physicsBoostForce = cfg.getDouble("physics.boost_force", 2.5);
            physicsMaxTemp = cfg.getDouble("physics.max_temp", 100.0);
            physicsSweetSpotBaseWidth = cfg.getDouble("physics.sweet_spot_base_width", 10.0);

            gameMaxStrikes = cfg.getInt("gameplay.max_strikes", 5);
            gameStrikeCooldown = cfg.getInt("gameplay.strike_cooldown_ms", 500);
            gameOverheatTicks = cfg.getInt("gameplay.overheat_ticks", 60);
            gameQualityReward = cfg.getDouble("gameplay.quality_reward", 1.0);
            gameQualityPenalty = cfg.getDouble("gameplay.quality_penalty", 0.5);

            fluxModifiers.clear();
            ConfigurationSection fluxCfg = cfg.getConfigurationSection("flux_modifiers");
            if (fluxCfg != null) {
                for (String key : fluxCfg.getKeys(false)) {
                    try {
                        FluxType type = FluxType.valueOf(key.toUpperCase());
                        ConfigurationSection s = fluxCfg.getConfigurationSection(key);
                        FluxModifier mod = new FluxModifier(
                                s.getDouble("heat_rate", 1.0),
                                s.getDouble("sweet_spot_size", 1.0),
                                s.getDouble("sweet_spot_move_speed", 1.0),
                                s.getDouble("inertia", 0.5));
                        fluxModifiers.put(type, mod);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid FluxType in config: " + key);
                    }
                }
            }

            recipes.clear();
            ConfigurationSection recipeConfig = plugin.getConfigManager().getRecipesConfig()
                    .getConfigurationSection("alloy_forge");
            if (recipeConfig != null) {
                for (String key : recipeConfig.getKeys(false)) {
                    ConfigurationSection section = recipeConfig.getConfigurationSection(key);
                    if (section == null)
                        continue;

                    List<String> inputList = section.getStringList("inputs");
                    Map<String, Integer> inputs = new HashMap<>();

                    for (String inputStr : inputList) {
                        String[] parts = inputStr.split(":");
                        String mat;
                        int amt = 1;

                        if (parts.length >= 3) {
                            // Namespace:ID:Amount
                            mat = parts[0] + ":" + parts[1];
                            try {
                                amt = Integer.parseInt(parts[2]);
                            } catch (NumberFormatException ignored) {
                            }
                        } else if (parts.length == 2) {
                            // Mat:Amount or Namespace:ID
                            if (parts[1].matches("\\d+")) {
                                mat = parts[0];
                                amt = Integer.parseInt(parts[1]);
                            } else {
                                mat = parts[0] + ":" + parts[1];
                            }
                        } else {
                            mat = inputStr;
                        }
                        // Normalize: ignore MINECRAFT namespace, keep custom ones
                        inputs.put(mat.toUpperCase().replace("MINECRAFT:", ""), amt);
                    }

                    int strikes = section.getInt("max_strikes", gameMaxStrikes);
                    double outputTemp = section.getDouble("output_temperature", 1000.0);
                    ConfigurationSection diff = section.getConfigurationSection("difficulty");
                    double ssMult = 1.0, hrMult = 1.0, cMult = 1.0;
                    if (diff != null) {
                        ssMult = diff.getDouble("sweet_spot_mult", 1.0);
                        hrMult = diff.getDouble("heat_rate_mult", 1.0);
                        cMult = diff.getDouble("cooling_mult", 1.0);
                    }
                    
                    boolean hasQuality = section.getBoolean("has_quality", true);

                    if (!inputs.isEmpty()) {
                        recipes.add(new AlloyRecipe(key, inputs, strikes, outputTemp, ssMult, hrMult, cMult, hasQuality));
                    }
                }
                LogUtil.debug("Loaded " + recipes.size() + " alloy recipes.");
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null)
            return;

        // 处理会话交互 (锻打 / 鼓风)
        if (sessions.containsKey(player.getUniqueId())) {
            SmeltingSession session = sessions.get(player.getUniqueId());
            // 仅当视线对准结构时交互
            if (session.isPartOfStructure(block)) {
                event.setCancelled(true);
                if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                    session.handleStrike();
                } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    session.handleBoost();
                }
                return;
            }
        }

        // 处理新会话开始 (仅右键)
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        Block coreBlock = findCoreBlock(block);
        if (coreBlock == null)
            return;

        // 检查结构有效性
        BlockFace facing = multiBlockManager.checkStructure(coreBlock, "alloy_blast_furnace");
        if (facing == null) {
            // 结构不完整时，阻止原版 GUI 并显示调试信息
            event.setCancelled(true);
            multiBlockManager.debugStructure(player, coreBlock, "alloy_blast_furnace");
            return;
        }

        // 取消原版 GUI
        event.setCancelled(true);

        if (sessions.containsKey(player.getUniqueId())) {
            player.sendMessage(mm.deserialize(plugin.getConfigManager().getMessage("alloy.already_smelting")));
            return;
        }

        // 打开 GUI
        openGui(player, coreBlock, facing);
    }

    private void openGui(Player player, Block coreBlock, BlockFace facing) {
        AlloyForgeHolder holder = new AlloyForgeHolder(coreBlock, facing);
        Inventory inv = Bukkit.createInventory(holder, 27,
                mm.deserialize(plugin.getConfigManager().getMessage("alloy.gui.title")));

        // 填充背景
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.empty());
        filler.setItemMeta(fillerMeta);

        for (int i = 0; i < 27; i++) {
            if (slotsInput.contains(i) || i == slotFlux || i == slotStart || i == slotOutput)
                continue;
            inv.setItem(i, filler);
        }
        
        // Tutorial Book (Slot 26 - Top Right Corner)
        ItemStack tutorialBook = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta bookMeta = tutorialBook.getItemMeta();
        bookMeta.displayName(mm.deserialize("<!i><gradient:aqua:blue>合金锻造指南 (点击阅读)"));
        bookMeta.lore(java.util.List.of(
            mm.deserialize("<gray>了解如何操作热力冲程"),
            mm.deserialize("<gray>以及如何避免炸炉事故。")
        ));
        tutorialBook.setItemMeta(bookMeta);
        inv.setItem(26, tutorialBook);

        // Load Output
        ItemStack outputInfo = getOutputFromBlock(coreBlock);
        if (outputInfo != null) {
            inv.setItem(slotOutput, outputInfo);
        }

        // Load Input & Flux
        loadInventoryFromBlock(coreBlock, inv);

        // 开始按钮
        ItemStack startBtn = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta startMeta = startBtn.getItemMeta();
        startMeta.displayName(mm.deserialize(plugin.getConfigManager().getMessage("alloy.gui.start_btn_name")));

        List<String> lore = plugin.getConfigManager().getMessageList("alloy.gui.start_btn_lore");
        List<Component> loreComponents = lore.stream().map(mm::deserialize).toList();
        startMeta.lore(loreComponents);

        startBtn.setItemMeta(startMeta);
        inv.setItem(slotStart, startBtn);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AlloyForgeHolder))
            return;
        AlloyForgeHolder holder = (AlloyForgeHolder) event.getInventory().getHolder();

        event.setCancelled(true); // 默认取消，只允许特定槽位交互

        int slot = event.getRawSlot();
        if (slot >= event.getInventory().getSize()) {
            // 点击的是玩家背包，允许
            event.setCancelled(false);
            // shift click logic is handled by bukkit mostly, but dragging INTO gui needs
            // care
            // for simplicity, allow interaction in player inv
            return;
        }

        if (slot == 26) {
            // Tutorial Book Click
            event.setCancelled(true);
            Player p = (Player) event.getWhoClicked();
            p.closeInventory();
            
            // Send Tutorial Content
            p.sendMessage(mm.deserialize("<gradient:aqua:blue>========== [ 合金锻造指南 ] =========="));
            p.sendMessage(mm.deserialize("<gray>合金高炉采用独特的 <white>热力冲程</white> 机制。"));
            p.sendMessage(mm.deserialize(""));
            p.sendMessage(mm.deserialize("<yellow>1. 投料阶段 (Setup)"));
            p.sendMessage(mm.deserialize("<gray>  放入 <white>矿物原料</white> 与 <white>助熔剂(Flux)</white>。"));
            p.sendMessage(mm.deserialize("<gray>  点击 <green>[开始熔炼]</green> 按钮点火。"));
            p.sendMessage(mm.deserialize(""));
            p.sendMessage(mm.deserialize("<yellow>2. 动态博弈 (The Game)"));
            p.sendMessage(mm.deserialize("<gray>  关注 Actionbar 上的温度计: <aqua>[❄ ...|==★==|... 🔥]"));
            p.sendMessage(mm.deserialize("<gray>  <bold>目标:</bold> 当白色指针 <white>█</white> 位于金色甜蜜点 <gold>|==★==|</gold> 内时..."));
            p.sendMessage(mm.deserialize("<gray>  <bold>动作:</bold> 对高炉核心进行 <gold>左键打击(Left Click)</gold>！"));
            p.sendMessage(mm.deserialize("<gray>  <green>成功:</green> 积累评分，进度 +1。"));
            p.sendMessage(mm.deserialize("<gray>  <red>失败:</red> 扣除评分，进度不变。"));
            p.sendMessage(mm.deserialize(""));
            p.sendMessage(mm.deserialize("<yellow>3. 控温技巧 (Temperature Control)"));
            p.sendMessage(mm.deserialize("<gray>  <white>右键(Right Click)</white>: <red>鼓风升温</red> (指针向右加速)。"));
            p.sendMessage(mm.deserialize("<gray>  <white>潜行(Sneak)</white>: <aqua>阻尼降温</aqua> (增大阻力，稳定指针)。"));
            p.sendMessage(mm.deserialize(""));
            p.sendMessage(mm.deserialize("<yellow>4. 助熔剂策略 (Flux Strategy)"));
            p.sendMessage(mm.deserialize("<gray>  <dark_gray>煤炭:</dark_gray> 标准手感，适合新手。"));
            p.sendMessage(mm.deserialize("<gray>  <gold>烈焰粉:</gold> 升温极快，甜蜜点宽，适合快节奏高手。"));
            p.sendMessage(mm.deserialize("<gray>  <green>粘液球:</green> 惯性大，极难改变方向，但极其稳定。"));
            p.sendMessage(mm.deserialize(""));
            p.sendMessage(mm.deserialize("<red><bold>⚠ 警示 (WARNING)</bold>"));
            p.sendMessage(mm.deserialize("<gray>  若温度进入红色危险区超过 3秒，将触发 <dark_red>炸炉(Meltdown)</dark_red>！"));
            p.sendMessage(mm.deserialize("<gray>  炸炉会销毁所有原料并产生废渣，同时对您造成伤害。"));
            p.sendMessage(mm.deserialize("<gradient:blue:aqua>=================================="));
            
            p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
            return;
        }

        if (slotsInput.contains(slot) || slot == slotFlux) {
            event.setCancelled(false); // 允许放入取出
            return;
        }

        if (slot == slotOutput) {
            // Only allow taking out
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
                // Check if placing (holding item)
                if (event.getCursor() != null && event.getCursor().getType() != Material.AIR) {
                    // Try to swap? No. Prevent putting in.
                    return;
                }

                // Taking item
                event.setCancelled(false);
                // Clear persistence
                clearOutputFromBlock(holder.coreBlock);
            }
            return;
        }

        if (slot == slotStart) {
            Player player = (Player) event.getWhoClicked();
            Inventory inv = event.getInventory();

            // Check if output occupied
            if (inv.getItem(slotOutput) != null && inv.getItem(slotOutput).getType() != Material.AIR) {
                player.sendMessage(mm.deserialize(plugin.getConfigManager().getMessage("alloy.output_full")));
                return;
            }

            // 收集所有槽位的物品
            java.util.List<ItemStack> inputs = new java.util.ArrayList<>();
            for (int inputSlot : slotsInput) {
                ItemStack item = inv.getItem(inputSlot);
                if (item != null && item.getType() != Material.AIR) {
                    inputs.add(item);
                }
            }

            ItemStack fluxItem = inv.getItem(slotFlux);

            // 检查配方
            AlloyRecipe matchedRecipe = matchRecipe(inputs);

            if (matchedRecipe == null) {
                player.sendMessage(mm.deserialize(plugin.getConfigManager().getMessage("alloy.missing_material")));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                return;
            }

            // 获取 Flux
            FluxType flux = FluxType.NONE;
            if (fluxItem != null) {
                flux = FluxType.fromMaterial(fluxItem.getType());
            }

            // 开始熔炼

            // 消耗物品 logic
            // Copy matched inputs requirements to mutable map
            Map<String, Integer> remainingReqs = new HashMap<>(matchedRecipe.inputs());

            for (int inputSlot : slotsInput) {
                ItemStack item = inv.getItem(inputSlot);
                if (item == null || item.getType() == Material.AIR)
                    continue;

                String matName = getItemId(item);
                if (remainingReqs.containsKey(matName)) {
                    int reqAmt = remainingReqs.get(matName);
                    if (reqAmt > 0) {
                        int take = Math.min(item.getAmount(), reqAmt);
                        item.setAmount(item.getAmount() - take);
                        remainingReqs.put(matName, reqAmt - take);
                    }
                }
            }

            if (fluxItem != null) {
                fluxItem.setAmount(fluxItem.getAmount() - 1);
            }

            player.closeInventory();
            startSmelting(player, holder.coreBlock, holder.facing, matchedRecipe, flux, inputs);
        }
    }

    private String getItemId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR)
            return "AIR";

        // 1. Check SF PDC
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String sfId = meta.getPersistentDataContainer().get(com.starryforge.utils.Keys.ITEM_ID_KEY,
                    org.bukkit.persistence.PersistentDataType.STRING);
            if (sfId != null) {
                plugin.getLogger().info("DEBUG: Item has SF ID: " + sfId);
                // Special handling for legacy/Nexus items that might not have BIOMEGIFTS prefix
                if (sfId.equals("LIGNITE") || sfId.equals("RICH_SLAG") || sfId.equals("GOLD_DUST") || sfId.equals("CHARGED_DUST") || sfId.equals("ICE_SHARD") || sfId.equals("TIDE_ESSENCE") || sfId.equals("COPPER_CRYSTAL") || sfId.equals("JADE_SHARD")) {
                     return "BIOMEGIFTS:" + sfId;
                }
                return sfId.toUpperCase();
            }

            // 2. Check BiomeGifts PDC
            org.bukkit.NamespacedKey bgKey = org.bukkit.NamespacedKey.fromString("biomegifts:id");
            if (bgKey != null) {
                String bgId = meta.getPersistentDataContainer().get(bgKey,
                        org.bukkit.persistence.PersistentDataType.STRING);
                if (bgId != null) {
                    plugin.getLogger().info("DEBUG: Item has BG ID: " + bgId);
                    // Check if ID already contains namespace to avoid double prefix
                    if (bgId.toUpperCase().startsWith("BIOMEGIFTS:")) {
                         return bgId.toUpperCase();
                    }
                    return "BIOMEGIFTS:" + bgId.toUpperCase();
                }
            }
            
            // 3. Check NexusCore Item ID (Standard)
            org.bukkit.NamespacedKey nexusKey = org.bukkit.NamespacedKey.fromString("nexuscore:item_id");
            if (nexusKey != null) {
                String nexusId = meta.getPersistentDataContainer().get(nexusKey,
                        org.bukkit.persistence.PersistentDataType.STRING);
                if (nexusId != null) {
                    plugin.getLogger().info("DEBUG: Item has NexusCore ID: " + nexusId);
                    // BiomeGifts items in NexusCore usually don't have namespace in ID value
                    // Try to guess namespace or return raw if it looks like one
                    if (nexusId.contains(":")) {
                        return nexusId.toUpperCase();
                    } else {
                        // Assuming BiomeGifts if it's a known material? Or just return raw?
                        // For safety, let's try to map it if we can contextually guess, 
                        // but for now let's append BIOMEGIFTS if it matches known BG items logic
                        // Or better yet, just return BIOMEGIFTS:ID if it matches our recipe expectation
                        return "BIOMEGIFTS:" + nexusId.toUpperCase();
                    }
                }
            }
        }

        // 4. Fallback to Material Name (Minecraft Namespace)
        plugin.getLogger().info("DEBUG: Item fallback to Material: " + item.getType().name());
        return item.getType().name();
    }

    private AlloyRecipe matchRecipe(List<ItemStack> inputs) {
        Map<String, Integer> inputCounts = new HashMap<>();
        for (ItemStack item : inputs) {
            String id = getItemId(item);
            inputCounts.merge(id, item.getAmount(), (a, b) -> a + b);
        }
        
        plugin.getLogger().info("DEBUG: Current Input Counts: " + inputCounts);

        for (AlloyRecipe recipe : recipes) {
            boolean match = true;
            for (Map.Entry<String, Integer> req : recipe.inputs().entrySet()) {
                String reqKey = req.getKey().toUpperCase().replace("MINECRAFT:", "");
                int has = inputCounts.getOrDefault(reqKey, 0);
                
                plugin.getLogger().info("DEBUG: Checking Recipe " + recipe.resultId() + ": Need " + reqKey + " x" + req.getValue() + ", Has x" + has);
                
                if (has < req.getValue()) {
                    match = false;
                    break;
                }
            }
            if (match) {
                plugin.getLogger().info("DEBUG: Matched Recipe: " + recipe.resultId());
                return recipe;
            }
        }
        plugin.getLogger().info("DEBUG: No recipe matched.");
        return null;
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof AlloyForgeHolder) {
            for (int slot : event.getRawSlots()) {
                if (slot < event.getInventory().getSize()) {
                    if (!slotsInput.contains(slot) && slot != slotFlux && slot != slotOutput) {
                        event.setCancelled(true);
                        return;
                    }
                    if (slot == slotOutput) {
                        // Don't allow drag into output
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof AlloyForgeHolder) {
            AlloyForgeHolder holder = (AlloyForgeHolder) event.getInventory().getHolder();

            // Save current contents (Inputs + Flux)
            saveInventoryToBlock(holder.coreBlock, event.getInventory());
        }
    }

    private Block findCoreBlock(Block clicked) {
        // Check if clicked block is the custom Alloy Forge Core (LODESTONE with PDC)
        if (isAlloyForgeCore(clicked)) {
            return clicked;
        }

        // 如果点击的是锻造台，寻找相邻的合金锻炉核心
        if (clicked.getType() == Material.SMITHING_TABLE) {
            BlockFace[] faces = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };
            for (BlockFace face : faces) {
                Block neighbor = clicked.getRelative(face);
                if (isAlloyForgeCore(neighbor)) {
                    return neighbor;
                }
            }
        }
        return null;
    }

    private boolean isAlloyForgeCore(Block block) {
        if (block.getType() != Material.BLAST_FURNACE) {
            return false;
        }
        if (block.getState() instanceof org.bukkit.block.TileState state) {
            PersistentDataContainer pdc = state.getPersistentDataContainer();
            String id = pdc.get(Keys.ITEM_ID_KEY, PersistentDataType.STRING);
            return "ALLOY_FORGE_CORE".equals(id);
        }
        return false;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (isAlloyForgeCore(block)) {
            dropOutput(block);
            dropInputs(block);
        }

        // Handle Smithing Table break (the input/controller block)
        if (block.getType() == Material.SMITHING_TABLE) {
            Block coreBlock = findCoreBlock(block);
            if (coreBlock != null) {
                dropOutput(coreBlock);
                dropInputs(coreBlock);
            }
        }
    }

    private void saveOutputToBlock(Block block, ItemStack item) {
        if (block == null || item == null)
            return;
        try {
            String data = SerializationUtils.itemToBase64(item);
            // Apply naming logic for hot items
            // But we actually store the CLEAN item, and apply lore/name when viewing?
            // Actually, we should store it as is.
            // We use PDC on the BlockState
            if (block.getState() instanceof org.bukkit.block.TileState state) {
                state.getPersistentDataContainer().set(Keys.ALLOY_OUTPUT_ITEM, PersistentDataType.STRING, data);
                state.getPersistentDataContainer().set(Keys.ALLOY_OUTPUT_TIME, PersistentDataType.LONG,
                        System.currentTimeMillis());
                state.update();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ItemStack getOutputFromBlock(Block block) {
        if (block == null)
            return null;
        if (block.getState() instanceof org.bukkit.block.TileState state) {
            PersistentDataContainer pdc = state.getPersistentDataContainer();
            if (pdc.has(Keys.ALLOY_OUTPUT_ITEM, PersistentDataType.STRING)) {
                try {
                    String data = pdc.get(Keys.ALLOY_OUTPUT_ITEM, PersistentDataType.STRING);
                    ItemStack item = SerializationUtils.itemFromBase64(data);

                    if (PDCManager.hasTemperature(item)) {
                        // Logic: Natural Cooling inside Forge is DISABLED by Phase 2 requirements.
                        // We simply retain the stored info.
                    }

                    return item;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    private void clearOutputFromBlock(Block block) {
        if (block == null)
            return;
        if (block.getState() instanceof org.bukkit.block.TileState state) {
            state.getPersistentDataContainer().remove(Keys.ALLOY_OUTPUT_ITEM);
            state.getPersistentDataContainer().remove(Keys.ALLOY_OUTPUT_TIME);
            state.update();
        }
    }

    private void dropOutput(Block block) {
        ItemStack item = getOutputFromBlock(block);
        if (item != null) {
            block.getWorld().dropItemNaturally(block.getLocation(), item);
            clearOutputFromBlock(block);
        }
    }

    private void dropInputs(Block block) {
        if (block.getState() instanceof org.bukkit.block.TileState state) {
            PersistentDataContainer pdc = state.getPersistentDataContainer();
            NamespacedKey[] keys = { Keys.ALLOY_INPUT_0, Keys.ALLOY_INPUT_1, Keys.ALLOY_INPUT_2, Keys.ALLOY_FLUX_ITEM };
            for (NamespacedKey key : keys) {
                if (pdc.has(key, PersistentDataType.STRING)) {
                    try {
                        ItemStack item = SerializationUtils.itemFromBase64(pdc.get(key, PersistentDataType.STRING));
                        if (item != null) {
                            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 1, 0.5), item);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    pdc.remove(key);
                }
            }
            state.update();
        }
    }

    private void saveInventoryToBlock(Block block, Inventory inv) {
        if (block == null || inv == null)
            return;
        if (block.getState() instanceof org.bukkit.block.TileState state) {
            PersistentDataContainer pdc = state.getPersistentDataContainer();
            // Inputs
            for (int i = 0; i < slotsInput.size(); i++) {
                int slot = slotsInput.get(i);
                ItemStack item = inv.getItem(slot);
                NamespacedKey key = switch (i) {
                    case 0 -> Keys.ALLOY_INPUT_0;
                    case 1 -> Keys.ALLOY_INPUT_1;
                    case 2 -> Keys.ALLOY_INPUT_2;
                    default -> null;
                };
                if (key != null) {
                    if (item != null && item.getType() != Material.AIR) {
                        try {
                            pdc.set(key, PersistentDataType.STRING, SerializationUtils.itemToBase64(item));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        pdc.remove(key);
                    }
                }
            }
            // Flux
            ItemStack flux = inv.getItem(slotFlux);
            if (flux != null && flux.getType() != Material.AIR) {
                try {
                    pdc.set(Keys.ALLOY_FLUX_ITEM, PersistentDataType.STRING, SerializationUtils.itemToBase64(flux));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                pdc.remove(Keys.ALLOY_FLUX_ITEM);
            }
            state.update();
        }
    }

    private void loadInventoryFromBlock(Block block, Inventory inv) {
        if (block == null || inv == null)
            return;
        if (block.getState() instanceof org.bukkit.block.TileState state) {
            PersistentDataContainer pdc = state.getPersistentDataContainer();
            // Inputs
            for (int i = 0; i < slotsInput.size(); i++) {
                int slot = slotsInput.get(i);
                NamespacedKey key = switch (i) {
                    case 0 -> Keys.ALLOY_INPUT_0;
                    case 1 -> Keys.ALLOY_INPUT_1;
                    case 2 -> Keys.ALLOY_INPUT_2;
                    default -> null;
                };
                if (key != null && pdc.has(key, PersistentDataType.STRING)) {
                    try {
                        inv.setItem(slot, SerializationUtils.itemFromBase64(pdc.get(key, PersistentDataType.STRING)));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            // Flux
            if (pdc.has(Keys.ALLOY_FLUX_ITEM, PersistentDataType.STRING)) {
                try {
                    inv.setItem(slotFlux,
                            SerializationUtils
                                    .itemFromBase64(pdc.get(Keys.ALLOY_FLUX_ITEM, PersistentDataType.STRING)));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void startSmelting(Player player, Block furnace, BlockFace facing, AlloyRecipe recipe, FluxType flux, List<ItemStack> inputs) {
        // Calculate raw stars from inputs (NexusCore Standard)
        // Rule: Un-starred items (Vanilla) default to 1 Star (Standard Quality).
        // Only explicitly 0-star items (Trash) should pull down the average.
        int totalStars = 0;
        int count = 0;
        for (ItemStack item : inputs) {
            if (item == null || item.getType() == Material.AIR) continue;
            
            int stars = 1; // Default to 1
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(NexusKeys.STAR_RATING, PersistentDataType.INTEGER)) {
                stars = meta.getPersistentDataContainer().get(NexusKeys.STAR_RATING, PersistentDataType.INTEGER);
            }
            
            totalStars += stars;
            count++;
        }
        // Round to nearest integer
        int rawStars = count > 0 ? Math.round((float) totalStars / count) : 1;

        player.sendMessage(mm.deserialize(plugin.getConfigManager().getMessage("alloy.start")));
        player.sendMessage(
                mm.deserialize(plugin.getConfigManager().getMessage("alloy.flux_used").replace("{flux}", flux.name())));

        SmeltingSession session = new SmeltingSession(player, furnace, facing, flux, rawStars, recipe);
        sessions.put(player.getUniqueId(), session);
        session.runTaskTimer(plugin, 0L, 1L);
    }

    // --- GUI Holder ---
    private static class AlloyForgeHolder implements InventoryHolder {
        private final Block coreBlock;
        private final BlockFace facing;

        public AlloyForgeHolder(Block coreBlock, BlockFace facing) {
            this.coreBlock = coreBlock;
            this.facing = facing;
        }

        @Override
        public Inventory getInventory() {
            return null; // Not needed
        }
    }

    // --- 助熔剂类型枚举 ---
    public enum FluxType {
        NONE,
        COAL, // 稳定
        BLAZE, // 激进
        SLIME; // 粘性

        public static FluxType fromMaterial(Material mat) {
            if (mat == Material.COAL || mat == Material.CHARCOAL)
                return COAL;
            if (mat == Material.BLAZE_POWDER)
                return BLAZE;
            if (mat == Material.SLIME_BALL)
                return SLIME;
            return NONE;
        }
    }

    // --- 熔炼会话 ---
    private class SmeltingSession extends BukkitRunnable {
        private final Player player;
        private final Block furnace;
        private final FluxModifier fluxMod;
        private final int rawStars;
        private final List<Block> structureBlocks;

        // 物理状态
        private double temperature = 0.0; // 0.0 到 100.0
        private double velocity = 0.0;
        private double sweetSpotCenter = 50.0;
        private double sweetSpotTarget = 50.0;

        // 游戏状态
        private int strikes = 0;
        private double qualityAccumulator = 0.0;
        private int overHeatTicks = 0;
        private long lastStrikeTime = 0;

        private final AlloyRecipe recipe;

        public SmeltingSession(Player player, Block furnace, BlockFace facing, FluxType flux, int rawStars,
                AlloyRecipe recipe) {
            this.player = player;
            this.furnace = furnace;
            this.fluxMod = fluxModifiers.getOrDefault(flux, fluxModifiers.get(FluxType.NONE));
            this.rawStars = rawStars;
            this.recipe = recipe;
            this.structureBlocks = multiBlockManager.getStructureBlocks(furnace, "alloy_blast_furnace", facing);
        }

        public boolean isPartOfStructure(Block b) {
            return structureBlocks.contains(b);
        }

        public void handleBoost() {
            // 视觉反馈
            player.playSound(furnace.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 0.5f, 2.0f);
            velocity += physicsBoostForce * fluxMod.heatRate();
        }

        public void handleStrike() {
            long now = System.currentTimeMillis();
            if (now - lastStrikeTime < gameStrikeCooldown)
                return; // 冷却时间
            lastStrikeTime = now;

            double sweetSpotWidth = physicsSweetSpotBaseWidth * fluxMod.sweetSpotSize() * recipe.sweetSpotMult();
            double diff = Math.abs(temperature - sweetSpotCenter);

            if (diff <= sweetSpotWidth / 2.0) {
                // 成功
                strikes++;
                qualityAccumulator += gameQualityReward;
                player.playSound(furnace.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1.0f, 1.5f);
                spawnStructureParticles(Particle.WAX_ON, Color.YELLOW);

                String msg = plugin.getConfigManager().getMessage("alloy.hit_success")
                        .replace("{strikes}", String.valueOf(strikes))
                        .replace("{max_strikes}", String.valueOf(recipe.maxStrikes()));
                player.sendMessage(mm.deserialize(msg));
            } else {
                // 失败
                qualityAccumulator -= gameQualityPenalty; // 惩罚
                player.playSound(furnace.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);
                player.sendMessage(mm.deserialize(plugin.getConfigManager().getMessage("alloy.hit_fail")));
            }

            if (strikes >= recipe.maxStrikes()) {
                finish();
            }
        }

        @Override
        public void run() {
            if (!player.isOnline() || multiBlockManager.checkStructure(furnace, "alloy_blast_furnace") == null) {
                this.cancel();
                sessions.remove(player.getUniqueId());
                return;
            }

            // 1. 更新物理
            updatePhysics();

            // 2. 检查熔毁
            if (temperature >= physicsMaxTemp) {
                overHeatTicks++;
                if (overHeatTicks % 5 == 0) {
                    player.playSound(furnace.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
                    spawnStructureParticles(Particle.SMOKE, Color.BLACK);
                }
                if (overHeatTicks > gameOverheatTicks) {
                    explode();
                    return;
                }
            } else {
                overHeatTicks = Math.max(0, overHeatTicks - 1);
            }

            // 3. 视觉效果
            updateVisuals();
            updateActionBar();
        }

        private void updatePhysics() {
            // 甜蜜点移动
            if (Math.abs(sweetSpotCenter - sweetSpotTarget) < 1.0) {
                sweetSpotTarget = 20.0 + random.nextDouble() * 60.0; // 20-80 之间的随机新目标
            }
            double moveDir = Math.signum(sweetSpotTarget - sweetSpotCenter);
            sweetSpotCenter += moveDir * 0.2 * fluxMod.sweetSpotMoveSpeed();

            // 温度物理
            // 重力 (冷却)
            velocity += physicsGravity * recipe.coolingMult();

            // 阻尼 (潜行)
            if (player.isSneaking()) {
                velocity *= 0.90; // 高摩擦
            } else {
                velocity *= 0.98; // 空气阻力
            }

            temperature += velocity * recipe.heatRateMult();

            // 边界
            if (temperature < 0) {
                temperature = 0;
                velocity = 0;
            }
        }

        private void updateVisuals() {
            // 热发光
            if (temperature > 50) {
                if (random.nextDouble() < (temperature / 200.0)) {
                    spawnStructureParticles(Particle.FLAME, null);
                }
            }

            // 蒸汽 (冷却)
            if (velocity < -0.5 && temperature > 20) {
                if (random.nextDouble() < 0.2) {
                    furnace.getWorld().spawnParticle(Particle.CLOUD, furnace.getLocation().add(0.5, 1, 0.5), 1, 0.2,
                            0.5, 0.2, 0.01);
                }
            }
        }

        private void updateActionBar() {
            // [❄️ ---|==★==|--- 🔥]
            int totalBars = 40;
            double sweetSpotWidth = physicsSweetSpotBaseWidth * fluxMod.sweetSpotSize() * recipe.sweetSpotMult();

            StringBuilder bar = new StringBuilder();
            bar.append("<gradient:aqua:blue>❄</gradient> <gray>[");

            for (int i = 0; i < totalBars; i++) {
                double pos = (i / (double) totalBars) * 100.0;

                boolean isSweet = Math.abs(pos - sweetSpotCenter) < (sweetSpotWidth / 2.0);
                boolean isPointer = Math.abs(pos - temperature) < (100.0 / totalBars);

                if (isPointer) {
                    bar.append("<white>█</white>");
                } else if (isSweet) {
                    bar.append("<gold>|</gold>");
                } else {
                    if (pos > 90)
                        bar.append("<red>-</red>"); // 危险区
                    else
                        bar.append("<dark_gray>-</dark_gray>");
                }
            }
            bar.append("<gray>] <gradient:yellow:red>🔥</gradient>");

            if (overHeatTicks > 0) {
                bar.append(" <red><b>WARNING!</b></red>");
            }

            player.sendActionBar(mm.deserialize(bar.toString()));
        }

        private void spawnStructureParticles(Particle particle, Color color) {
            for (Block b : structureBlocks) {
                if (random.nextDouble() > 0.1)
                    continue; // 优化
                if (color != null && particle == Particle.DUST) {
                    b.getWorld().spawnParticle(particle, b.getLocation().add(0.5, 0.5, 0.5), 1, 0.3, 0.3, 0.3, 0,
                            new Particle.DustOptions(color, 1));
                } else {
                    b.getWorld().spawnParticle(particle, b.getLocation().add(0.5, 0.5, 0.5), 1, 0.3, 0.3, 0.3, 0);
                }
            }
        }

        private void explode() {
            this.cancel();
            sessions.remove(player.getUniqueId());

            // Prepare Mineral Slag before explosion
            ItemStack slagItem = createMineralSlag();
            org.bukkit.Location dropLocation = furnace.getLocation().add(0.5, 1.5, 0.5);

            // Create explosion visual effect ONLY (no block destruction)
            furnace.getWorld().createExplosion(furnace.getLocation(), 0.0f, false);
            // Additional visual/sound effects for "Meltdown"
            furnace.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, furnace.getLocation(), 1);
            furnace.getWorld().spawnParticle(Particle.LAVA, furnace.getLocation(), 20, 1, 1, 1, 0.1);
            furnace.getWorld().playSound(furnace.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
            
            // Damage the player slightly as feedback
            player.damage(4.0);
            
            // Clear the inputs/session data from the block (effectively "consuming" them)
            clearOutputFromBlock(furnace);
            if (furnace.getState() instanceof org.bukkit.block.TileState state) {
                PersistentDataContainer pdc = state.getPersistentDataContainer();
                NamespacedKey[] keys = { Keys.ALLOY_INPUT_0, Keys.ALLOY_INPUT_1, Keys.ALLOY_INPUT_2, Keys.ALLOY_FLUX_ITEM };
                for (NamespacedKey key : keys) {
                    pdc.remove(key);
                }
                state.update();
            }

            // Drop slag
            if (slagItem != null) {
                dropLocation.getWorld().dropItemNaturally(dropLocation, slagItem);
            }

            player.sendMessage(mm.deserialize(plugin.getConfigManager().getMessage("alloy.meltdown")));
        }

        private ItemStack createMineralSlag() {
            // Use Unified ScrapManager
            ScrapManager scrapManager = plugin.getScrapManager();
            
            // Calculate return amount (30-80% of input)
            int totalSpecialOre = recipe.inputs().values().stream().findFirst().orElse(1);
            int returnAmount = (int) Math.ceil(totalSpecialOre * (0.3 + random.nextDouble() * 0.5));
            if (returnAmount < 1) returnAmount = 1;
            
            String firstInputType = recipe.inputs().keySet().stream().findFirst().orElse("UNKNOWN");
            
            return scrapManager.createScrap(
                ScrapManager.ScrapType.ALLOY_SLAG,
                firstInputType,
                recipe.resultId(), // Using result ID as target name context
                rawStars,
                returnAmount
            );
        }

        private void finish() {
            this.cancel();
            sessions.remove(player.getUniqueId());

            // 计算星级
            // 公式: Stars = floor(RawStars * (0.5 + 0.5 * (Score / MaxStrikes)))
            int finalStars = 1;
            if (recipe.hasQuality()) {
                double completionRate = Math.max(0, qualityAccumulator / recipe.maxStrikes());
                double finalRating = rawStars * (0.5 + 0.5 * completionRate);
                finalStars = (int) Math.floor(finalRating);
                if (finalStars < 1) finalStars = 1;
                
                player.sendMessage(
                    mm.deserialize(plugin.getConfigManager().getMessage("alloy.complete") + "⭐".repeat(finalStars)));
            } else {
                // No quality system for this recipe
                player.sendMessage(mm.deserialize(plugin.getConfigManager().getMessage("alloy.complete")));
            }

            ItemStack result = null;
            if (plugin.getItemManager() != null) {
                result = plugin.getItemManager().getItem(recipe.resultId());
            }
            if (result == null) {
                // Fallback to vanilla
                Material mat = Material.matchMaterial(recipe.resultId());
                if (mat != null) { // Original check for mat != null
                    result = new ItemStack(mat);
                } else {
                    result = new ItemStack(Material.IRON_INGOT); // Fallback
                }
            } else {
                result = result.clone();
            }
            result.setAmount(1);

            // Apply Stars to Name (Only if hasQuality)
            ItemMeta meta = result.getItemMeta();

            if (recipe.hasQuality()) {
                // Set NexusCore Standard Rating
                meta.getPersistentDataContainer().set(NexusKeys.STAR_RATING, PersistentDataType.INTEGER, finalStars);

                if (meta.hasDisplayName()) {
                    Component currentName = meta.displayName();
                    Component starsSuffix = mm.deserialize(" <yellow>" + "⭐".repeat(finalStars));
                    meta.displayName(currentName.append(starsSuffix));
                } else {
                    Component starsSuffix = mm.deserialize(" <yellow>" + "⭐".repeat(finalStars));
                    // Clean name
                    String matName = result.getType().name().replace("_", " ");
                    // title case
                    matName = matName.substring(0, 1).toUpperCase() + matName.substring(1).toLowerCase();
                    meta.displayName(Component.text(matName).append(starsSuffix));
                }
            }
            result.setItemMeta(meta);

            // Set Temperature from recipe (not game temperature)
            double outputTemp = recipe.outputTemperature();
            PDCManager.setTemperature(result, outputTemp);
            // Also store max temperature for re-heating
            ItemMeta resultMeta = result.getItemMeta();
            resultMeta.getPersistentDataContainer().set(Keys.MAX_TEMPERATURE_KEY, PersistentDataType.DOUBLE, outputTemp);
            result.setItemMeta(resultMeta);
            // Initial Lore update (only if above ambient)
            if (outputTemp > 25.0) {
                plugin.getThermodynamicsManager().updateItemLore(result, outputTemp);
            }

            saveOutputToBlock(furnace, result);
            player.playSound(furnace.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
        }
    }
}
