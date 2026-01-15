package com.starryforge.features.sluice;

import com.starryforge.StarryForge;
import com.starryforge.utils.Keys;
import com.starryforge.utils.LogUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SluiceManager implements Listener {

    private final StarryForge plugin;
    // 活跃会话：区块已加载且正在运行
    private final Map<Location, SluiceSession> activeSessions = new ConcurrentHashMap<>();
    // 挂起会话：区块已卸载，等待加载
    private final Map<Location, SluiceSession> pendingSessions = new ConcurrentHashMap<>();

    private final Random random = new Random();
    private final MiniMessage mm = MiniMessage.miniMessage();

    // 槽位定义
    public static final int SLOT_INPUT = 10;
    public static final int SLOT_SOLVENT = 4;
    public static final List<Integer> SLOT_OUTPUTS = Arrays.asList(6, 7, 15, 16);
    public static final int SLOT_PROGRESS = 13;

    public static final List<Integer> BORDER_SLOTS = Arrays.asList(
            0, 1, 2, 3, 5, 8,
            9, 11, 12, 14, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26);

    // Session 定义
    private static class SluiceSession {
        int time;
        int maxTime;
        int stars;
        boolean hasSolvent;
        int tier;
        long lastTickTime; // 用于计算离线时间

        public SluiceSession(int time, int maxTime, int stars, boolean hasSolvent, int tier) {
            this.time = time;
            this.maxTime = maxTime;
            this.stars = stars;
            this.hasSolvent = hasSolvent;
            this.tier = tier;
            this.lastTickTime = System.currentTimeMillis();
        }
    }

    public SluiceManager(StarryForge plugin) {
        this.plugin = plugin;
        loadActiveSluices(); // 初始加载
        startTask();
    }

    private void startTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // 直接遍历 ConcurrentHashMap 是安全的
                for (Map.Entry<Location, SluiceSession> entry : activeSessions.entrySet()) {
                    Location loc = entry.getKey();
                    SluiceSession session = entry.getValue();

                    // 1. 检查区块加载状态
                    if (!loc.isChunkLoaded()) {
                        pendingSessions.put(loc, session);
                        activeSessions.remove(loc);
                        continue;
                    }

                    Block block = loc.getBlock();
                    if (!(block.getState() instanceof Barrel)) {
                        activeSessions.remove(loc);
                        continue;
                    }

                    // 获取 Snapshot
                    Barrel barrel = (Barrel) block.getState();

                    // 尝试获取 Live Inventory (玩家正在打开的)
                    Inventory liveInv = getLiveInventory(loc);
                    Inventory workingInv = (liveInv != null) ? liveInv : barrel.getInventory();

                    // tickSession 处理逻辑
                    // 注意：这里传入 workingInv 用于物品操作，barrel 用于 PDC 操作
                    tickSession(loc, session, workingInv, barrel, liveInv != null);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // 1秒 tick 一次
    }

    private Inventory getLiveInventory(Location loc) {
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            InventoryView view = p.getOpenInventory();
            Inventory top = view.getTopInventory();
            if (top.getHolder() instanceof BlockState) {
                BlockState holder = (BlockState) top.getHolder();
                if (holder.getWorld().getName().equals(loc.getWorld().getName()) &&
                        holder.getX() == loc.getX() &&
                        holder.getY() == loc.getY() &&
                        holder.getZ() == loc.getZ()) {
                    return top;
                }
            }
        }
        return null;
    }

    // 强制同步视图给所有观察者 (仅当 workingInv 是 Snapshot 时需要，如果是 Live 则自带同步)
    private void syncView(Location loc, Inventory snapshotInv) {
        // ... (保留现有逻辑，作为 fallback)
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            InventoryView view = p.getOpenInventory();
            Inventory top = view.getTopInventory();
            if (top.getHolder() instanceof BlockState) {
                BlockState holder = (BlockState) top.getHolder();
                if (holder.getWorld().getName().equals(loc.getWorld().getName()) &&
                        holder.getX() == loc.getX() &&
                        holder.getY() == loc.getY() &&
                        holder.getZ() == loc.getZ()) {

                    // 只有当 snapshotInv 内容不同于 top 时才同步，避免闪烁
                    // 但为保险起见，这里强制覆盖
                    top.setContents(snapshotInv.getContents());
                    p.updateInventory();
                }
            }
        }
    }

    // 核心逻辑
    private boolean tickSession(Location loc, SluiceSession session, Inventory inv, Barrel barrel, boolean isLive) {
        session.lastTickTime = System.currentTimeMillis();
        boolean dirty = false;
        boolean needsSave = false; // 是否需要写入世界

        // 1. 减少时间
        if (session.time > 0) {
            session.time--;
            updateProgressBar(inv, session.time, session.maxTime);
            dirty = true;
        }

        // 2. 周期完成
        if (session.time <= 0) {
            boolean cycleDirty = finishCycle(loc, session, inv, barrel);
            if (cycleDirty) {
                dirty = true;
                needsSave = true;
            }
        }

        if (dirty) {
            // 更新 PDC (必须操作 Snapshot)
            applySessionToPdc(barrel.getPersistentDataContainer(), session);

            // 如果操作的是 Live Inventory，我们需要把内容同步回 Snapshot
            // 否则 barrel.update() 会用旧的 Snapshot 覆盖 Live Inventory
            if (isLive) {
                barrel.getInventory().setContents(inv.getContents());
            }

            // 执行存盘
            // 1. 如果 needsSave=true (物品变了)，必须存盘
            // 2. 如果只是倒计时更新 (dirty=true)，不要频繁调用 update(true)，否则会导致客户端 GUI 闪烁或回滚
            // 3. PDC 的持久化推迟到 needsSave 或 ChunkUnload

            if (needsSave) {
                // 统一强制刷新策略：无论 Live 还是 Snapshot，都获取最新的 BlockState 进行保存
                // 这解决了 Snapshot 模式下 update(true) 可能无效的问题
                BlockState newState = loc.getBlock().getState();
                if (newState instanceof Barrel) {
                    Barrel newBarrel = (Barrel) newState;

                    // 1. 同步物品：将当前内存中的 Inventory (inv) 状态复制到新的 Snapshot 中
                    // inv 可能是 LiveInv (最新的)，也可能是 tick 开始时的 SnapshotInv (我们刚才修改过的)
                    // 无论哪种，inv 都包含了我们期望的最新物品状态
                    newBarrel.getInventory().setContents(inv.getContents());

                    // 2. 同步 PDC
                    applySessionToPdc(newBarrel.getPersistentDataContainer(), session);

                    // 3. 强制写入世界
                    boolean success = newBarrel.update(true);
                    if (success) {
                        LogUtil.debug("Saved sluice state (Force Refresh) at " + loc);
                    } else {
                        plugin.getLogger().warning("Failed to save sluice state (Force Refresh) at " + loc);
                    }
                }
            } else {
                // 仅倒计时更新，不需要存盘
            }

            // 如果不是 Live Inventory，我们需要手动同步视图
            if (!isLive) {
                syncView(loc, inv);
            }
        }

        return false;
    }

    private boolean finishCycle(Location loc, SluiceSession session, Inventory inv, Barrel barrel) {
        boolean dirty = false;

        // 生成产物
        ItemStack loot = generateLoot(session.stars, session.hasSolvent);
        List<Integer> validSlots = getOutputSlotsForTier(session.tier);

        // 尝试放入产物
        if (!addItemToSlots(inv, loot, validSlots)) {
            // 放不下，掉落 (掉落到世界不需要 update inventory，但这里是逻辑分支)
            loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 1, 0.5), loot);
        } else {
            // 放入成功，Inventory 变脏
            dirty = true;
        }

        LogUtil.debug("Sluice cycle finished at " + loc);

        // 尝试开始下一轮
        if (!tryStartNextCycle(loc, session, inv, barrel)) {
            stopSession(loc, inv, barrel);
            dirty = true; // 进度条重置了
        } else {
            dirty = true; // 扣除了物品，更新了进度条
        }

        return dirty;
    }

    private boolean tryStartNextCycle(Location loc, SluiceSession session, Inventory inv, Barrel barrel) {
        ItemStack inputItem = inv.getItem(SLOT_INPUT);
        ItemStack solventItem = inv.getItem(SLOT_SOLVENT);

        if (inputItem == null || !plugin.getItemManager().isCustomItem(inputItem, "UNIDENTIFIED_CLUSTER")) {
            return false;
        }

        // 准备下一轮数据
        int stars = 1;
        if (inputItem.getItemMeta().getPersistentDataContainer().has(Keys.CLUSTER_QUALITY_KEY,
                PersistentDataType.INTEGER)) {
            int val = inputItem.getItemMeta().getPersistentDataContainer().get(Keys.CLUSTER_QUALITY_KEY,
                    PersistentDataType.INTEGER);
            if (val <= 5)
                stars = val;
            else
                stars = (val / 20) + 1;
        }

        boolean hasSolvent = false;
        if (solventItem != null && plugin.getItemManager().isCustomItem(solventItem, "STARRY_SOLVENT")) {
            hasSolvent = true;
        }

        int seconds = plugin.getConfigManager().getInt("machines.sluice.processing_times.tier_" + stars, 20);

        // 扣除物品 (直接操作 Inventory)
        if (inputItem.getAmount() <= 1) {
            inv.setItem(SLOT_INPUT, null);
            LogUtil.debug("Consumed input item (cleared slot) at " + loc);
        } else {
            inputItem.setAmount(inputItem.getAmount() - 1);
            inv.setItem(SLOT_INPUT, inputItem);
            LogUtil.debug("Consumed input item (amount=" + inputItem.getAmount() + ") at " + loc);
        }

        if (hasSolvent && solventItem != null) {
            if (solventItem.getAmount() <= 1) {
                inv.setItem(SLOT_SOLVENT, null);
            } else {
                solventItem.setAmount(solventItem.getAmount() - 1);
                inv.setItem(SLOT_SOLVENT, solventItem);
            }
        }

        // 更新 Session
        session.time = seconds;
        session.maxTime = seconds;
        session.stars = stars;
        session.hasSolvent = hasSolvent;

        updateProgressBar(inv, seconds, seconds);

        // 保存 PDC (直接更新 Barrel 的 PDC，不调用 update，由调用者统一调用)
        if (barrel != null) {
            applySessionToPdc(barrel.getPersistentDataContainer(), session);
        }

        LogUtil.debug("Starting next cycle at " + loc);
        return true;
    }

    private void stopSession(Location loc, Inventory inv, Barrel barrel) {
        LogUtil.debug("Stopping sluice at " + loc);
        activeSessions.remove(loc);
        resetProgressBar(inv);

        // 清除 PDC
        if (barrel != null) {
            PersistentDataContainer pdc = barrel.getPersistentDataContainer();
            pdc.remove(Keys.SLUICE_PROCESSING_TIME);
            // 不调用 update，由调用者统一调用
        }
    }

    public void startProcessing(Barrel barrel, Inventory inv, PersistentDataContainer pdc) {
        Location loc = barrel.getLocation();

        // 检查是否已经在运行
        if (activeSessions.containsKey(loc))
            return;

        // 尝试启动 (复用逻辑)

        ItemStack input = inv.getItem(SLOT_INPUT);
        ItemStack solvent = inv.getItem(SLOT_SOLVENT);

        if (input == null || !plugin.getItemManager().isCustomItem(input, "UNIDENTIFIED_CLUSTER")) {
            return;
        }

        int stars = 1;
        if (input.getItemMeta().getPersistentDataContainer().has(Keys.CLUSTER_QUALITY_KEY,
                PersistentDataType.INTEGER)) {
            int val = input.getItemMeta().getPersistentDataContainer().get(Keys.CLUSTER_QUALITY_KEY,
                    PersistentDataType.INTEGER);
            if (val <= 5)
                stars = val;
            else
                stars = (val / 20) + 1;
        }

        boolean hasSolvent = false;
        if (solvent != null && plugin.getItemManager().isCustomItem(solvent, "STARRY_SOLVENT")) {
            hasSolvent = true;
        }

        int seconds = plugin.getConfigManager().getInt("machines.sluice.processing_times.tier_" + stars, 20);
        int tier = pdc.getOrDefault(Keys.SLUICE_TIER, PersistentDataType.INTEGER, 1);

        SluiceSession session = new SluiceSession(seconds, seconds, stars, hasSolvent, tier);

        // 扣除物品
        if (input.getAmount() <= 1)
            inv.setItem(SLOT_INPUT, null);
        else {
            input.setAmount(input.getAmount() - 1);
            inv.setItem(SLOT_INPUT, input);
        }

        if (hasSolvent && solvent != null) {
            if (solvent.getAmount() <= 1)
                inv.setItem(SLOT_SOLVENT, null);
            else {
                solvent.setAmount(solvent.getAmount() - 1);
                inv.setItem(SLOT_SOLVENT, solvent);
            }
        }

        updateProgressBar(inv, seconds, seconds);

        // 关键修复：直接更新传入的 PDC，避免双重 Snapshot 冲突
        applySessionToPdc(pdc, session);

        activeSessions.put(loc, session);

        // 优化：不再在 startProcessing 中立即调用 update(true)
        // 1. inv 是 Live Inventory，setItem 会直接更新 TileEntity 的物品数据
        // 2. update(true) 会导致区块更新，可能会干扰玩家当前的 GUI 视图 (导致按钮变色失效)
        // 3. PDC 的持久化推迟到 tickSession (1秒后) 进行，这是安全的
        // barrel.getInventory().setContents(inv.getContents());
        // barrel.update(true);

        LogUtil.debug("Started sluice manually at " + loc);
    }

    public boolean canProcess(Inventory inv) {
        ItemStack input = inv.getItem(SLOT_INPUT);
        return input != null && plugin.getItemManager().isCustomItem(input, "UNIDENTIFIED_CLUSTER");
    }

    public boolean isProcessing(Location loc) {
        return activeSessions.containsKey(loc) || pendingSessions.containsKey(loc);
    }

    private void loadActiveSluices() {
        activeSessions.clear();
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                restoreSluicesInChunk(chunk);
            }
        }
    }

    private void restoreSluicesInChunk(org.bukkit.Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Barrel) {
                PersistentDataContainer pdc = ((Barrel) state).getPersistentDataContainer();
                if (pdc.has(Keys.SLUICE_PROCESSING_TIME, PersistentDataType.INTEGER)) {
                    SluiceSession session = loadSessionFromPdc(pdc);
                    Location loc = state.getLocation();

                    // 模拟离线进度
                    if (pendingSessions.containsKey(loc)) {
                        session = pendingSessions.remove(loc);
                        simulateOfflineProgress(loc, session, (Barrel) state);
                    }

                    // 关键修复：如果 session 在模拟中已停止，不添加到活跃列表
                    if (isProcessing(loc)) {
                        activeSessions.put(loc, session);
                    }
                }
            }
        }
    }

    private SluiceSession loadSessionFromPdc(PersistentDataContainer pdc) {
        int time = pdc.getOrDefault(Keys.SLUICE_PROCESSING_TIME, PersistentDataType.INTEGER, 0);
        int maxTime = pdc.getOrDefault(Keys.SLUICE_MAX_TIME, PersistentDataType.INTEGER, 20);
        int stars = pdc.getOrDefault(Keys.SLUICE_QUALITY, PersistentDataType.INTEGER, 1);
        boolean hasSolvent = pdc.getOrDefault(Keys.SLUICE_HAS_SOLVENT, PersistentDataType.BYTE, (byte) 0) == 1;
        int tier = pdc.getOrDefault(Keys.SLUICE_TIER, PersistentDataType.INTEGER, 1);
        return new SluiceSession(time, maxTime, stars, hasSolvent, tier);
    }

    private void applySessionToPdc(PersistentDataContainer pdc, SluiceSession session) {
        pdc.set(Keys.SLUICE_PROCESSING_TIME, PersistentDataType.INTEGER, session.time);
        pdc.set(Keys.SLUICE_MAX_TIME, PersistentDataType.INTEGER, session.maxTime);
        pdc.set(Keys.SLUICE_QUALITY, PersistentDataType.INTEGER, session.stars);
        pdc.set(Keys.SLUICE_HAS_SOLVENT, PersistentDataType.BYTE, (byte) (session.hasSolvent ? 1 : 0));
    }

    private void saveSessionToPdc(Location loc, SluiceSession session) {
        if (!loc.isChunkLoaded())
            return;
        BlockState state = loc.getBlock().getState();
        if (state instanceof Barrel) {
            PersistentDataContainer pdc = ((Barrel) state).getPersistentDataContainer();
            applySessionToPdc(pdc, session);
            state.update(true);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (BlockState state : event.getChunk().getTileEntities()) {
            if (state instanceof Barrel) {
                Location loc = state.getLocation();
                if (pendingSessions.containsKey(loc)) {
                    SluiceSession session = pendingSessions.remove(loc);
                    simulateOfflineProgress(loc, session, (Barrel) state);

                    // 关键修复：如果 session 在模拟中已停止，不添加到活跃列表
                    if (isProcessing(loc)) {
                        activeSessions.put(loc, session);
                    }
                } else {
                    PersistentDataContainer pdc = ((Barrel) state).getPersistentDataContainer();
                    if (pdc.has(Keys.SLUICE_PROCESSING_TIME, PersistentDataType.INTEGER)) {
                        SluiceSession session = loadSessionFromPdc(pdc);
                        activeSessions.put(loc, session);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Iterator<Map.Entry<Location, SluiceSession>> it = activeSessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Location, SluiceSession> entry = it.next();
            if (entry.getKey().getChunk().equals(event.getChunk())) {
                SluiceSession session = entry.getValue();
                saveSessionToPdc(entry.getKey(), session);
                pendingSessions.put(entry.getKey(), session);
                it.remove();
            }
        }
    }

    private void simulateOfflineProgress(Location loc, SluiceSession session, Barrel barrel) {
        long now = System.currentTimeMillis();
        long elapsedMillis = now - session.lastTickTime;
        int elapsedSeconds = (int) (elapsedMillis / 1000);

        if (elapsedSeconds <= 0)
            return;

        LogUtil.debug("Simulating offline progress for sluice at " + loc + ": " + elapsedSeconds + "s");

        if (elapsedSeconds > 3600)
            elapsedSeconds = 3600;

        Inventory inv = barrel.getInventory();
        boolean dirty = false;

        while (elapsedSeconds > 0) {
            if (session.time > elapsedSeconds) {
                session.time -= elapsedSeconds;
                elapsedSeconds = 0;
                dirty = true; // 时间变了 (虽然 GUI 可能看不见，但逻辑上变了)
            } else {
                elapsedSeconds -= session.time;
                session.time = 0;

                // 模拟完成周期
                ItemStack loot = generateLoot(session.stars, session.hasSolvent);
                List<Integer> validSlots = getOutputSlotsForTier(session.tier);

                if (!addItemToSlots(inv, loot, validSlots)) {
                    loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 1, 0.5), loot);
                } else {
                    dirty = true;
                }

                // 尝试开始下一轮 (模拟)
                if (!tryStartNextCycle(loc, session, inv, barrel)) {
                    stopSession(loc, inv, barrel);
                    dirty = true;
                    return; // 停止模拟
                } else {
                    dirty = true;
                }
            }
        }

        session.lastTickTime = now;
        updateProgressBar(inv, session.time, session.maxTime);

        // 关键修复：离线模拟结束后，统一更新 BlockState
        if (dirty) {
            barrel.update(true);
        }
    }

    // Helper Methods

    public void registerSluice(Block block, int tier) {
        if (block.getState() instanceof Barrel) {
            Barrel barrel = (Barrel) block.getState();
            barrel.getPersistentDataContainer().set(Keys.SLUICE_MACHINE, PersistentDataType.BYTE, (byte) 1);
            barrel.getPersistentDataContainer().set(Keys.SLUICE_TIER, PersistentDataType.INTEGER, tier);
            barrel.update(true);
            setupGui(barrel.getInventory(), tier);
        }
    }

    public boolean isSluice(Block block) {
        if (block != null && block.getState() instanceof Barrel) {
            Barrel barrel = (Barrel) block.getState();
            return barrel.getPersistentDataContainer().has(Keys.SLUICE_MACHINE, PersistentDataType.BYTE);
        }
        return false;
    }

    public void removeSluice(Location loc) {
        activeSessions.remove(loc);
        pendingSessions.remove(loc);
    }

    public void shutdown() {
        for (Map.Entry<Location, SluiceSession> entry : activeSessions.entrySet()) {
            saveSessionToPdc(entry.getKey(), entry.getValue());
        }
        activeSessions.clear();
        pendingSessions.clear();
    }

    public List<Integer> getOutputSlotsForTier(int tier) {
        if (tier == 1)
            return Collections.singletonList(16);
        if (tier == 2)
            return Arrays.asList(6, 7, 15, 16);
        if (tier == 3)
            return Arrays.asList(6, 7, 8, 15, 16, 17, 24, 25, 26);
        return Collections.singletonList(16);
    }

    private void updateProgressBar(Inventory inv, int timeLeft, int maxTime) {
        ItemStack pane;
        double progress = 1.0 - ((double) timeLeft / maxTime);
        Material mat;
        if (progress < 0.33)
            mat = Material.RED_STAINED_GLASS_PANE;
        else if (progress < 0.66)
            mat = Material.YELLOW_STAINED_GLASS_PANE;
        else
            mat = Material.LIME_STAINED_GLASS_PANE;

        pane = new ItemStack(mat);
        ItemMeta meta = pane.getItemMeta();
        String msg = plugin.getConfigManager().getMessage("sluice.processing").replace("{time}",
                String.valueOf(timeLeft));
        meta.displayName(mm.deserialize(msg));
        pane.setItemMeta(meta);
        inv.setItem(SLOT_PROGRESS, pane);
    }

    public void resetProgressBar(Inventory inv) {
        ItemStack pane = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        String msg = plugin.getConfigManager().getMessage("sluice.click_to_start");
        meta.displayName(mm.deserialize(msg));
        pane.setItemMeta(meta);
        inv.setItem(SLOT_PROGRESS, pane);
    }

    public void setupGui(Inventory inv, int tier) {
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        meta.displayName(Component.text(" "));
        border.setItemMeta(meta);

        List<Integer> activeOutputSlots = getOutputSlotsForTier(tier);
        for (int i = 0; i < inv.getSize(); i++) {
            if (i == SLOT_INPUT || i == SLOT_SOLVENT || i == SLOT_PROGRESS)
                continue;
            if (activeOutputSlots.contains(i)) {
                ItemStack current = inv.getItem(i);
                if (current != null && current.getType() == Material.BLACK_STAINED_GLASS_PANE) {
                    inv.setItem(i, null);
                }
            } else {
                inv.setItem(i, border);
            }
        }
        if (inv.getItem(SLOT_PROGRESS) == null) {
            resetProgressBar(inv);
        }
    }

    private ItemStack generateLoot(int stars, boolean hasSolvent) {
        // Recover approximate quality from stars (1-5)
        // 1->0-19, 2->20-39, etc.
        int baseQuality = (stars - 1) * 20;
        int randomOffset = random.nextInt(20);
        int effectiveQuality = baseQuality + randomOffset;

        if (hasSolvent)
            effectiveQuality += 15;
        if (effectiveQuality > 100)
            effectiveQuality = 100;

        Material[] ores = {
                Material.COAL, Material.RAW_COPPER, Material.RAW_IRON,
                Material.RAW_GOLD, Material.REDSTONE, Material.LAPIS_LAZULI,
                Material.DIAMOND, Material.EMERALD
        };
        String[] specialOreKeys = {
                "LIGNITE", "RICH_SLAG", "GOLD_DUST", "CHARGED_DUST",
                "ICE_SHARD", "TIDE_ESSENCE", "COPPER_CRYSTAL", "JADE_SHARD"
        };

        double specialChance = hasSolvent ? 0.5 : 0.4;
        boolean isSpecial = random.nextDouble() < specialChance;

        ItemStack item;
        if (isSpecial) {
            String key = specialOreKeys[random.nextInt(specialOreKeys.length)];
            ItemStack specialItem = null;
            if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("BiomeGifts")) {
                try {
                    org.bukkit.plugin.Plugin bgPlugin = org.bukkit.Bukkit.getPluginManager().getPlugin("BiomeGifts");
                    Object itemManager = bgPlugin.getClass().getMethod("getItemManager").invoke(bgPlugin);
                    Object itemObj = itemManager.getClass().getMethod("getItem", String.class).invoke(itemManager, key);
                    if (itemObj instanceof ItemStack) {
                        specialItem = (ItemStack) itemObj;
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to get item from BiomeGifts: " + e.getMessage());
                }
            }
            if (specialItem == null) {
                item = new ItemStack(ores[random.nextInt(ores.length)]);
            } else {
                item = specialItem;
            }
        } else {
            // Standard Ore Generation Logic
            // Reuse quality to influence type? Or just random?
            // Existing logic uses random + quality influence.
            // "int roll = random.nextInt(100) + (effectiveQuality / 2);"
            // I'll keep this part.

            int roll = random.nextInt(100) + (effectiveQuality / 2);
            Material result;
            if (roll < 30)
                result = Material.COAL;
            else if (roll < 60)
                result = Material.RAW_COPPER;
            else if (roll < 90)
                result = Material.RAW_IRON;
            else if (roll < 110)
                result = Material.RAW_GOLD;
            else if (roll < 125)
                result = Material.REDSTONE;
            else if (roll < 135)
                result = Material.LAPIS_LAZULI;
            else if (roll < 145)
                result = Material.DIAMOND;
            else
                result = Material.EMERALD;
            item = new ItemStack(result);
        }

        // Calculate Star Rating using Probability Distribution
        int resultStars = plugin.getItemManager().rollSluiceStars(effectiveQuality);

        // Apply Star Rating (PDC + Lore)
        plugin.getItemManager().applyOreStar(item, resultStars);

        return item;
    }

    private boolean addItemToSlots(Inventory inv, ItemStack item, List<Integer> slots) {
        ItemStack remaining = item.clone();
        for (int slot : slots) {
            if (remaining.getAmount() <= 0)
                break;

            ItemStack current = inv.getItem(slot);
            if (current == null || current.getType() == Material.AIR) {
                inv.setItem(slot, remaining.clone());
                remaining.setAmount(0);
            } else if (current.isSimilar(remaining)) {
                int space = current.getMaxStackSize() - current.getAmount();
                int toAdd = Math.min(space, remaining.getAmount());
                if (toAdd > 0) {
                    current.setAmount(current.getAmount() + toAdd);
                    remaining.setAmount(remaining.getAmount() - toAdd);
                }
            }
        }
        return remaining.getAmount() <= 0;
    }
}
