# 星野体系：铁心锻造 (IronHeart) 架构设计文档
**(IronHeart Architecture Design Document)**

**版本**：2.0
**状态**：[规划中]
**模块**：`features.ironheart`

---

## 1. 架构总览 (Architectural Overview)

本模块旨在构建一个**深度定制化、数据驱动**的武器组装系统。核心思想是摒弃原版静态物品 ID，转为基于 **NBT (PDC)** 的动态组件容器。

### 核心设计原则
1.  **功能优先 (Feature-First)**：所有相关代码（GUI, Logic, Listeners）均收敛于 `features/ironheart` 包下，严禁按层分包。
2.  **数据驱动 (Data-Driven)**：武器属性由组件 NBT 实时计算，而非查表。
3.  **防御性编程**：严格校验 NBT 完整性，处理版本兼容与空指针异常。
4.  **静态交互**：GUI 逻辑必须保持简洁，无 QTE，强调策略性。

---

## 2. 模块结构设计 (Module Structure)

```
com.starryforge.features.ironheart
├── IronHeartManager.java       # 模块入口，负责生命周期管理
├── config
│   ├── ComponentConfig.java    # 加载 components.yml
│   ├── BlueprintConfig.java    # 加载 blueprints.yml
│   └── ResonanceConfig.java    # 加载 resonance.yml (隐秘契约)
├── data
│   ├── model
│   │   ├── IronHeartWeapon.java # 武器实体包装类 (Wrapper)
│   │   ├── WeaponComponent.java # 组件数据对象
│   │   └── VeteranStats.java    # 历战数据记录
│   └── PDCAdapter.java         # NBT 读写适配器 (JSON <-> PDC)
├── gui
│   ├── AssemblerGUI.java       # 组装台主界面 (54格)
│   ├── BlueprintSelector.java  # 蓝图选择界面 (配方书)
│   └── GUIStateSession.java    # 玩家会话状态机
├── logic
│   ├── StatCalculator.java     # 属性计算器 (含共鸣逻辑)
│   ├── IntegrityValidator.java # 构造值校验器
│   ├── ModificationLogic.java  # 改装/损耗/回收判定
│   └── ScrapRecycler.java      # 废料回收逻辑
└── listeners
    ├── CombatListener.java     # 伤害计算与历战数据更新
    └── AssemblerListener.java  # 方块交互监听
```

---

## 3. 数据流架构 (Data Flow Architecture)

### 3.1 武器生成流程 (Creation)
1.  **Input**: 玩家选择 `Blueprint` + 放入 `Component Items`。
2.  **Process**:
    *   `IntegrityValidator` 校验构造值合法性。
    *   `StatCalculator` 遍历组件计算基础属性。
    *   `ResonanceConfig` 检查特殊组合并应用加成。
3.  **Output**: 生成 `ItemStack`，写入 `starfield:forge_data` (JSON)。

### 3.2 战斗结算流程 (Combat)
1.  **Event**: `EntityDamageByEntityEvent`
2.  **Read**: `PDCAdapter` 读取攻击者手持物品的 `stats_cache`。
3.  **Apply**: 直接应用 `damage` 数值。
4.  **Update**: 异步更新 `veteran` 数据 (累积伤害)。

---

## 4. 分阶段开发规划 (Development Roadmap)

为确保代码质量与可维护性，开发将分为 5 个阶段进行，每个阶段必须经过测试验证后方可进入下一阶段。

### ✅ 阶段一：数据基石 (Phase 1: Foundation)
**目标**：建立数据模型与配置加载系统。
*   [x] **1.1 配置系统**: 实现 `ComponentConfig` 和 `BlueprintConfig`，能够从 YML 读取数据并缓存到内存。
*   [x] **1.2 NBT 适配器**: 实现 `PDCAdapter`，支持将 `IronHeartWeapon` 对象序列化为 JSON 并存入 ItemStack PDC。
*   [x] **1.3 核心模型**: 定义 `WeaponComponent` (组件) 和 `IronHeartWeapon` (武器) 的 Java 类结构。
*   **交付物**：`/ih test item <blueprint>` 指令，可生成一把带有完整 NBT 数据的测试武器。

### 🛠️ 阶段二：组装台交互 (Phase 2: Assembler Interaction)
**目标**：实现可视化的 GUI 交互框架。
*   [x] **2.1 状态机**: 实现 `GUIStateSession`，管理 IDLE (空闲) / FABRICATION (制造) / MODIFICATION (改装) 状态。
*   [x] **2.2 配方书**: 开发 `BlueprintSelector`，允许玩家点击选择蓝图。
*   [x] **2.3 幻影渲染**: 在 `AssemblerGUI` 中实现“幻影物品”显示，指引玩家放置组件。
*   **交付物**：能够打开组装台，选择蓝图，并看到正确的组件槽位高亮。

### ⚙️ 阶段三：核心逻辑 (Phase 3: Logic & Mechanics)
**目标**：实现数值计算与制造/改装逻辑。
*   [x] **3.1 构造值校验**: 实现 `IntegrityValidator`，实时计算并限制组件安装。
*   [x] **3.2 属性计算**: 编写 `StatCalculator`，实现 `Base + Component + Resonance` 的计算公式。
*   [x] **3.3 制造执行**: 完成“点击确认 -> 消耗材料 -> 生成成品”的完整闭环。
*   [x] **3.4 改装协议**: 实现“硬绑定销毁”与“软绑定概率回收”的物理逻辑。
*   **交付物**：功能完整的组装台，可以制造和改装武器，且属性计算正确。

### ♻️ 阶段四：经济闭环 (Phase 4: Economy & Integration)
**目标**：接入服务器经济与资源循环。
*   [x] **4.1 废料系统**: 实现组件销毁时产出 `StarryScrap` 的逻辑。
*   [ ] **4.2 外部集成**: (预留) 对接合金锻炉的组件生产接口。
*   **交付物**：改装失败后会正确返还废料，且废料可被熔炼。

### 🎵 阶段五：灵魂注入 (Phase 5: Polish & Soul)
**目标**：提升手感与叙事深度。
*   [ ] **5.1 工业交响曲**: 添加 GUI 操作音效 (放置、取下、锻造成功)。
*   [ ] **5.2 动态 Lore**: 实现 `LoreBuilder`，实时生成包含属性、组件列表和历战铭文的描述。
*   [ ] **5.3 历战系统**: 实现战斗监听与“老兵/传奇”进化逻辑。
*   **交付物**：最终成品的打磨版本，包含音效与视觉特效。

---

## 5. 风险控制 (Risk Management)
1.  **NBT 数据膨胀**: 严格控制 JSON 大小，仅存储 ID 和必要数值，描述性文本一律动态生成。
2.  **并发安全**: GUI 操作虽在主线程，但需注意防止刷物品（如背包满时的返还逻辑）。
3.  **版本迁移**: 组件 ID 一旦定型严禁修改，否则会导致旧存档物品失效。
