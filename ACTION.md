# 星野体系：铁心锻造 (IronHeart) 模块化重构技术白皮书
**(IronHeart Modular Refactoring Technical Document)**

**版本**：2.0 (Refactor)
**受众**：服务端开发组、技术美术
**核心目标**：实现装备的**组件化存储**、**动态属性计算**与**双模式 GUI 交互**。

---

### 项目愿景：晶械朋克下的文明复兴
我们要构建的不仅仅是一个插件列表，而是一个逻辑闭环的“后启示录工业复兴”世界。玩家身处一个科技断层的“星寰帝国”，通过**“精密手工技艺”**来驱动旧时代的黑箱科技。我们的核心目标是打破原版 Minecraft 枯燥的“合成表”逻辑，建立一套基于**深度定制与数值博弈**的资源循环系统。（注：本模块完全摒弃QTE操作，专注于静态的策略组装体验，以此作为高强度战斗后的“减负”环节）。

### 核心任务：铁心锻造 (IronHeart) 的模块化重构
目前的开发重点是将锻造系统从传统的“配方合成”升级为“类 Tetra (模组) 的模块化组装系统”。这是一次底层的技术重构，具体需求如下：

1.  **数据驱动的物品架构 (NBT-Driven)**：
    彻底摒弃静态物品 ID。一把武器不再是简单的 `DIAMOND_SWORD`，而是一个携带复杂 NBT 数据（JSON 格式存储于 PDC）的容器。它由**刃、脊、杆、柄、护、重**六大组件 ID 动态组合而成。武器的最终属性（攻击力、攻速、耐久、特殊词条）不再查表，而是通过遍历所有组件属性**实时计算**得出。

2.  **双模式 GUI 交互逻辑**：
    我们需要开发一个基于 54 格箱子的深度定制 GUI，并通过资源包的**字体偏移技术 (Negative Space)** 绘制全屏背景，突破原版界面限制（顶部/右侧信息区）。该 GUI 需支持两种状态机：
    *   **制造模式**：放入蓝图，显示幻影组件图标，引导玩家填充材料，从零构建武器。
    *   **改装模式**：放入成品武器，自动解析 NBT 并拆解显示当前组件。允许玩家进行**无损替换**（例如将“木柄”换成“雷狼龙骨柄”），并实时在右侧面板预览属性涨跌（Diff View）。
    *   **静态交互**：所有操作均为点击确认即完成，无 QTE 判定。

3.  **数值博弈：构造值 (Structure/Integrity)**：
    引入“构造值”作为限制核心。核心骨架（如星钢刃）提供构造上限，功能配件（如重型配重）消耗构造值。若消耗超过上限，则禁止组装。这迫使玩家必须先升级基础材料，才能承载更强力的配件，形成完美的数值成长曲线。

### 生态联动与资源闭环
此锻造系统是服务器经济的**“吞金兽”**。
*   **输入端**：它将消耗`BiomeGifts`（特产矿物）、`PastureSong`（高阶兽骨/皮革）、`CuisineFarming`（特殊植物纤维）以及`StarryEconomy`（图纸交易）的所有产出。
*   **输出端**：产出的定制化武器将直接服务于`RPG战斗`模块，不同的组件组合将决定武器的战斗手感（如攻速流、削韧流、暴击流）。

## 1. 核心架构变更 (Architecture Migration)

| 维度 | 旧架构 (Legacy) | **新架构 (Modular v2.0)** |
| :--- | :--- | :--- |
| **物品定义** | 静态 ID (如 `iron_katana`) | **动态 NBT 容器** (由组件 ID 实时构建) |
| **属性计算** | 读取配置文件的固定数值 | **实时计算** (遍历组件 NBT 求和) |
| **存储方式** | ItemMeta / Lore | **PDC (PersistentDataContainer)** + JSON |
| **交互逻辑** | 配方匹配 -> 产出 | **蓝图定义结构 + 组件填充数值** |
| **扩展性** | 新武器需写新配方 | 新武器只需定义组件，组合无限 |

---

## 2. 数据结构设计 (Data Structure)

### 2.1 物品 NBT 存储规范 (PDC)
所有通过组装台产出的物品，必须携带名为 `starfield:forge_data` 的 `PersistentDataType.STRING` (JSON格式) 数据。

**JSON 数据模型：**
```json
{
  "uuid": "unique-item-uuid-v4",
  "blueprint_id": "katana_standard", // 蓝图ID，决定有哪些槽位
  "tier": 3, // 整体材质等级（用于显示）
  "integrity": {
    "current": 3, // 剩余构造值
    "max": 8      // 上限 (由骨架组件决定)
  },
  "components": {
    "head": "blade_steel_t2",      // 刃模 ID
    "spine": "spine_iron_t1",      // 脊模 ID
    "handle": "grip_leather_t1",   // 柄模 ID
    "weight": "counterweight_void",// 重模 ID
    "guard": "guard_standard",     // 护模 ID
    "shaft": null                  // 杆模 (太刀不需要杆，故为null)
  },
  "stats_cache": {                 // 缓存属性，避免每次攻击都重新计算
    "damage": 12.5,
    "speed": 1.6,
    "poise_dmg": 5.0
  },
  "history": {                     // 记录制造者/强化次数
    "crafter": "PlayerName",
    "created_at": 1715420000,
    "veteran": {                   // [方案C] 历战数据
      "total_damage": 0.0,         // 累计有效伤害
      "kill_count": 0,             // 累计击杀数
      "rank": 0                    // 当前军阶 (0=新兵, 1=老兵, 2=传奇)
    }
  }
}
```

### 2.2 配置文件结构 (`components.yml`)
组件不再是单纯的物品，而是携带属性的配置节点。

```yaml
blade_steel_t2:
  type: HEAD          # 组件类型：主刃
  name: "精钢打刀刃"
  item_id: IRON_INGOT # 材质
  cmd: 10001          # CustomModelData
  stats:
    damage: 6.0
    reach: 3.0
    integrity_provider: 5  # 提供构造值
  requirements:
    forge_level: 10   # 锻造等级需求

grip_dragon_bone:
  type: GRIP          # 组件类型：握把
  name: "龙骨握把"
  stats:
    speed: 0.2        # 攻速加成
    integrity_cost: 3 # 消耗构造值
  abilities:
    - "DRAGON_ROAR"   # 触发特殊技能
```

---

### 2.3 组件生产与获取 (Component Acquisition)
**组件来源：合金锻炉 (Alloy Forge)**
为了强化工业流程的仪式感，所有核心组件（无论是金属刃、龙骨柄还是纤维重块）均需通过 **合金锻炉** 进行“热加工”或“模具浇筑”产出。
*   **流程**：`原材料 (Ingots/Bones)` + `模具 (Mold)` -> `合金锻炉` -> `成品组件 (Component Item)`。
*   **定位**：合金锻炉负责将原材料转化为具有属性的零部件（热加工/前处理），组装台负责将零部件组装为成品武器（冷加工/总装）。

### 2.4 蓝图与解锁机制 (Blueprint System - Future Proofing)
*   **通用组件**：默认对所有玩家开放制作权限。
*   **特殊组件**：需要玩家通过消耗特定的 **组件蓝图 (Component Blueprint)** 来解锁制作配方。
    *   *注：此机制为未来开发预留接口，本次组装台开发需预留 `RecipeUnlockCheck` 的钩子。*

## 3. GUI 交互逻辑 (Interaction Logic)

采用 **54格箱子界面**，利用资源包字体偏移技术绘制全屏背景。

### 3.1 界面状态机 (State Machine)
GUI 需维护一个 `Session` 对象，记录当前状态：

1.  **STATE_IDLE (空闲态)**
    *   **中央槽 (20)**：显示 **[配方书]** 按钮 (Knowledge Book)。
    *   **点击逻辑**：点击打开 `BlueprintSelectionGUI`，展示玩家已解锁（已消耗学习）的蓝图列表。
    *   **组件槽**：显示灰色玻璃板。

2.  **STATE_FABRICATION (制造态)**
    *   **触发**：从配方书选择蓝图后。
    *   **逻辑**：
        1.  中央槽显示 **[蓝图投影]** (不可拿取)。
        2.  点亮对应位置的组件槽，显示 **幻影物品 (Ghost Item)**。
        3.  **校验**：`checkIntegrity()`。
    *   **退出**：点击中央槽或关闭 GUI 返回空闲态。

3.  **STATE_MODIFICATION (改装态)**
    *   **触发**：中央槽放入带有 `starfield:forge_data` 的武器。
    *   **逻辑**：
        1.  解析 NBT，将当前的组件实体化，放入周围的组件槽。
        2.  **替换逻辑 (The Swap Protocol)**：
            *   **硬绑定 (Hard Bind)**：针对结构性组件（刃、脊、杆）。旧组件无法取下，只能被新组件**覆盖销毁**（变成废料或消失）。体现“核心骨架不可逆”的物理特性。
            *   **软绑定 (Soft Bind)**：针对附件组件（柄、护、重）。取下或替换时，进行一次 `Luck Check`。成功则退回原组件，失败则损毁。
        3.  **动态预览 (Diff View)**：
            *   监听 `InventoryClickEvent`。
            *   每当组件槽发生变动（但未点击确认）时，调用 `previewStats()`。
            *   更新信息区 Lore，格式化为：`攻击: 10 -> 12 (+2)`。

### 3.3 改装损耗协议 (Modification Protocol)
为防止“无限倒卖配件”，组件与武器的结合分为两种物理状态：

| 绑定类型 | 适用组件 | 物理逻辑 | 替换后果 |
| :--- | :--- | :--- | :--- |
| **硬绑定 (Hard)** | **刃 (Edge)**, **脊 (Spine)**, **杆 (Shaft)** | 核心骨架，通过焊接/铸造一体化。 | **必毁**：旧组件直接销毁，产出对应材质的 `废料 (Scrap)`。 |
| **软绑定 (Soft)** | **柄 (Grip)**, **护 (Guard)**, **重 (Weight)** | 外部配件，通过铆钉/绑带固定。 | **概率毁**：70% 成功回收。30% 失败并产出 `废料 (Scrap)`。 |

*设计意图：迫使玩家在“追求极致属性”与“保护现有资产”之间做决策，增加改装的沉没成本。*

### 3.4 废料回收机制 (Scrap Recovery)
沿用合金锻炉的逻辑，损毁的组件将转化为 `StarryScrap` (带有 NBT 记录原材质)。
*   **回收路径**：`StarryScrap` -> 高炉 (Blast Furnace) 烧制 -> 返还 25%-50% 的原材料 (Core Material)。
*   **示例**：销毁“精钢打刀刃” -> 获得“废弃钢渣” -> 烧制获得 2-3 个钢锭。

### 3.5 构造值校验算法 (The Validation)
这是核心限制逻辑，必须在每次 GUI 更新时运行。

```java
public boolean validateStructure(Map<Slot, Component> currentComponents) {
    int maxIntegrity = 0;
    int usedIntegrity = 0;

    for (Component comp : currentComponents.values()) {
        if (comp.isProvider()) {
            maxIntegrity += comp.getIntegrityValue();
        } else {
            usedIntegrity += comp.getIntegrityValue();
        }
    }

    int remaining = maxIntegrity - usedIntegrity;
    updateInfoPanel(remaining); // 更新UI显示进度条

    return remaining >= 0; // 返回是否合法
}
```

---

## 4. 视觉与渲染实现 (Visual Implementation)

### 4.1 资源包字体偏移 (Negative Space Font)
*   **目标**：覆盖原版箱子 UI，绘制包含右侧信息面板的大图。
*   **文件**：`assets/minecraft/font/default.json`
*   **代码**：
    ```json
    {
        "providers": [
            {"type": "bitmap", "file": "starfield:gui/forge_bg.png", "ascent": -15, "height": 256, "chars": ["\uE001"]},
            {"type": "space", "advances": {"\uF801": -18}} 
        ]
    }
    ```
*   **Java 调用**：`inventory.open(player, Component.text("\uF801\uE001" + ChatColor.RESET));`

### 4.2 动态 Lore 生成器 (Lore Builder)
废弃静态 Lore 配置，使用 Builder 模式实时生成。

*   **格式规范**：
    *   Line 1: `动态名字` (如：`沉重的 星钢 太刀`)
    *   Line 2: `构造值进度条` (如：`🏗️ ■■■□□`)
    *   Line 3-N: `属性列表` (自动根据数值生成颜色，正增益绿，负增益红)
    *   Line N+: `组件列表` (灰色小字显示：`+ [刃] 精钢`, `+ [柄] 龙骨`)
    *   Line End: `历战铭文` (如：`⚔️ 累计造成 12,500 伤害 [老兵]`)

### 4.3 工业交响曲 (Auditory Feedback) 
为了在静态交互中提供极致的“体感”，我们将通过音效构建组装的仪式感。
*   **组件放置音效**：根据组件材质播放不同音效，避免单调。
    *   **金属类 (Metal)**: `BLOCK_ANVIL_PLACE` (Pitch: 1.5) - 清脆
    *   **木/骨类 (Organic)**: `ITEM_ARMOR_EQUIP_LEATHER` (Pitch: 1.0) - 沉闷
    *   **宝石/虚空类 (Void)**: `BLOCK_AMETHYST_BLOCK_CHIME` (Pitch: 0.8) - 空灵
*   **防噪处理**：设置 100ms 的 `Debounce` 时间，防止快速点击时的音效重叠刺耳。
*   **铸造和弦 (The Forging Chord)**：
    *   当点击“确认制造”时，根据武器的 **Tier** 播放一段组合音效。
    *   Tier 1: 单声 `BLOCK_ANVIL_USE`。
    *   Tier 3+: 混合 `ENTITY_IRON_GOLEM_REPAIR` + `BLOCK_BEACON_ACTIVATE` (Pitch: 2.0)，营造神兵出世的震撼感。

---

## 5. 核心算法逻辑 (Core Logic)

### 5.1 属性计算 (Stats Calculation)
当武器生成或更新时，执行此逻辑并写入 `stats_cache`。

$$FinalDamage = (Blade.Base + \sum Component.Bonus) \times (1 + ReinforceLevel \times 0.05)$$
$$FinalSpeed = (BaseSpeed + Grip.Bonus - Weight.Penalty)$$

### 5.2 监听器接管 (Event Handling)
*   **EntityDamageByEntityEvent**:
    *   读取攻击者手持物品的 `stats_cache` NBT。
    *   **不要**每次攻击都遍历组件计算（性能开销大）。
    *   直接应用 `cache.damage` 到事件。
    *   若存在特殊词条（如“静电”），触发 `AbilityManager.trigger(player, target, "STATIC_CHARGE")`。

### 5.3 隐秘契约 (Resonance System) 
为了增加社区讨论度与探索深度，组件之间存在“相性”判定。
*   **配置**：在 `resonance.yml` 中定义组合。
*   **逻辑**：
    ```java
    // 检查是否满足共鸣条件
    if (components.containsAll(resonance.getRequiredComponents())) {
        // 1. 赋予额外属性 (Stats Bonus)
        stats.addDamage(resonance.getBonusDamage());
        // 2. 修改武器名称颜色 (Visual)
        meta.displayName(mm.deserialize(resonance.getColor() + weaponName));
        // 3. 添加特殊 Lore
        lore.add(resonance.getHiddenLore());
    }
    ```
*   **示例**：同时装备 `deep_sea_blade` 和 `deep_sea_grip` -> 触发 "深海共鸣"，攻击附带 10% 水下增伤，武器名变为青色。

### 5.4 历战之躯 (Veteran Evolution)
武器随玩家一同成长，记录战斗数据并提供长线回报。
*   **数据记录**：
    *   监听 `EntityDamageByEntityEvent` (PVE/PVP)。
    *   过滤无效伤害（如打靶子、打自己的宠物），防止刷数据。
    *   异步更新 NBT 中的 `veteran.total_damage`。
*   **进化阈值**：
    *   **Level 1 (老兵)**: 累计 100,000 伤害 -> `Integrity Max +1` (允许额外改装一次)。
    *   **Level 2 (传奇)**: 累计 1,000,000 伤害 -> 解锁 `Legendary Title` (如 "斩星者") + 特殊粒子特效。
*   **逻辑闭环**：配合“核心绑定”，玩家不会轻易销毁一把“老兵”级别的武器，从而建立深厚的情感羁绊。

---

## 6. 开发分步计划 (Development Sprint)

1.  **Phase 1: 数据层 (Day 1-2)**
    *   完成 `ComponentManager` (读取 YML)。
    *   完成 `ItemNBTHandler` (PDC 读写 JSON)。
2.  **Phase 2: GUI 框架 (Day 3-5)**
    *   实现资源包偏移背景。
    *   实现 `Fabrication` 模式（蓝图识别、幻影物品显示）。
3.  **Phase 3: 逻辑层 (Day 6-8)**
    *   实现 `Modification` 模式（组件拆卸、替换）。
    *   编写 `StatsCalculator` 和 `IntegrityValidator`。
4.  **Phase 4: 视觉打磨 (Day 9)**
    *   对接 Lore 生成器。
    *   添加打铁音效、成功/失败粒子效果。

---

### ⚠️ 风险控制 (Risk Management)

1.  **NBT 溢出**：虽然 JSON 很灵活，但不要存储过多的描述性文本。只存 ID，描述文本在运行时通过 ID 从 YML 读取。
2.  **版本兼容**：组件 ID 一旦确定（如 `blade_steel`），后续修改 YML 属性时，已生成的武器会自动继承新属性（因为是实时计算或缓存刷新），这是该架构的最大优势。但**绝对不要删除已发布的 ID**，否则会导致物品损坏（Null Pointer）。
3.  **并发问题**：GUI 操作不需要异步，但 NBT 保存建议放在主线程，避免数据竞争。

此文档现已作为**技术规范**发布。请以此为准进行代码重构。