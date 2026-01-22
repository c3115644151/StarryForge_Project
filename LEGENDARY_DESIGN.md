# StarryForge 传奇武装设计文档 (Legendary Armament Design)

> **版本**: 1.0
> **状态**: Approved for Implementation
> **视觉标准**: 遵循 `mc-visual-design-system` (Cosmic Awe + Rustic Warmth)

本文档记录了 StarryForge 插件中 10 种传奇武装的最终设计方案。所有数值与机制均基于 NexusCore 通用 RPG 框架实现。

---

## 1. 设计原则 (Design Principles)

*   **星级质变**: 1-4★ 提供线性数值成长，5★ 解锁改变玩法的核心机制 (God Mode)。
*   **沉浸体验**: 强调视觉 (粒子/Shader)、听觉 (音效) 与操作手感 (QTE/反馈) 的结合。
*   **职业定位**: 明确区分 坦克、刺客、法师、辅助、采集者。

---

## 2. 传奇武装列表 (The Arsenal)

### ⚔️ 幽影匕首 (Shadow Dagger)
*   **定位**: 刺客 / 爆发 / 高机动
*   **核心属性**: `ATTACK_DAMAGE` (6.0), `ATTACK_SPEED` (3.0), `ATTACK_RANGE` (2.5), `CRITICAL_CHANCE` (20%), `CRITICAL_DAMAGE` (250%)
*   **被动技能: 暗影背刺 (Shadow Meld)**
    *   **效果**: 当处于目标身后（60°扇区）发动攻击时，必定**暴击**。
*   **5★ 技能: 血影遁 (Void Step)**
    *   **类型**: 主动技 (右键)
    *   **效果**: 获得 `SPEED V` + `INVISIBILITY` + `NIGHT_VISION`，持续 6 秒。
    *   **机制**: 技能期间获得**无限攻速**（忽略目标无敌帧）。
    *   **视觉**: 屏幕边缘泛起**红色血雾** (WorldBorder Vignette)，身后留下**红紫色虚影拖尾** (Ghost Trail)。
    *   **听觉**: 播放沉重的**心跳声** (Heartbeat)，频率随持续时间加快。
    *   **终结**: 隐身状态下攻击必定**暴击**。受到伤害超过 2秒 后会打破隐身，失去暴击加成。
    *   **CD**: 60s

### 🛡️ 青铜壁垒 (Bronze Shield)
*   **定位**: 坦克 / 控制 / 动能
*   **核心属性**: `KNOCKBACK_RESISTANCE`, `ARMOR_TOUGHNESS`
*   **5★ 技能: 动能释放 (Kinetic Discharge)**
    *   **类型**: 混合技 (被动充能 + 主动释放)
    *   **被动**: 每次成功格挡积攒一层“动能电荷” (最大 5 层)。
    *   **主动**: 左键点击消耗所有电荷，释放扇形冲击波。
    *   **效果**: 造成高额击退 + **眩晕** (缓慢 X + 挖掘疲劳 X) 2秒。
    *   **视觉**: 盾牌周围粒子随电荷层数变密，释放时产生空气扭曲波纹。

### 🌌 星陨套装 (Star Steel Set)
*   **定位**: 守护 / 坦克 / 团队核心
*   **核心属性**: `ARMOR`, `ARMOR_TOUGHNESS`, `MAX_HEALTH`
*   **单件 5★ 特性**:
    *   **头盔 (Star Gaze)**: 永久夜视 + 高亮显示 20 格内的发光生物 (Glowing)。
    *   **胸甲 (Meteor Heart)**: 额外护甲韧性 + 抗击退。
    *   **护腿 (Gravity Anchor)**: 免疫“漂浮”效果，潜行时无法被推动。
    *   **靴子 (Comet Stride)**: 掉落伤害 -80%，跳跃高度微增。
*   **全套 5★ 技能: 星之守护 (Astral Guardian)**
    *   **类型**: 被动保命
    *   **触发**: 生命值 < 30% 且受到伤害时。
    *   **效果**: 爆发抗拒光环 (Repulsion) 击退周围 5 格敌人，并获得 **伤害吸收 IV (Temp HP)**。
    *   **CD**: 60s

### ❄️ 霜叹·寂灭 (Frostsigh: Oblivion) - [太刀]
*   **定位**: 单挑 / 持续控制 / 核爆级终结
*   **核心属性**: `ATTACK_DAMAGE` (9.0), `ATTACK_SPEED` (2.0), `CRITICAL_CHANCE` (15%)
*   **被动: 凛冬之印 (Frost Mark)**
    *   **触发**: 近战普攻 (需冷却 > 0.9) 有 20% 概率施加一层印记，上限 10 层。
    *   **效果**:
        *   **绝对冻结**: 目标身上覆盖冰霜 (Freeze Ticks 维持 150)。
        *   **渐进减速**: `SLOWNESS` 等级随层数提升 (Level = 层数 / 2)。10层时寸步难行。
        *   **极寒冻伤**: 每 2 秒受到一次真实伤害，伤害值 = `1.0 (基础) + 层数 * 0.5`。
*   **5★ 绝技: 断空 (Event Horizon)**
    *   **机制**: 右键蓄力进入专注状态，根据蓄力时长分为 4 个阶段。
        *   **Stage 1 (蓄力)**: 无效果。
        *   **Stage 2 (READY)**: 基础突进，可造成基础伤害。
        *   **Stage 3 (EMPOWERED)**: 蓄力 > 5s。突进距离提升 (12->18格)，基础伤害提升 (20->30)。伴随漫天飞雪特效。
        *   **Stage 4 (WARNING)**: 蓄力 > 12.5s。UI 剧烈抖动警示。
        *   **Overcharge**: > 15s。专注打断，受到虚弱反噬。
    *   **释放 (次元斩)**: 松开右键发动。
        *   若命中带有印记的敌人，**引爆所有层数**。
        *   **终结伤害**: `基础伤害 * (1.0 + 层数 * 0.5)`。最高可达 6.0 倍伤害。
    *   **视觉**: 刀身在蓄力时逐渐被冰雪包裹，突进时留下星空轨迹 (Cyan -> Gold)。

### 🏹 霜叹·凛冬 (Frostsigh: Winter) - [长弓]
*   **定位**: 远程 / 区域封锁 / 狙击
*   **核心属性**: `PROJECTILE_DAMAGE` (+50%), `DRAW_SPEED` (+20%)
*   **1-4★ 被动: 穿甲冰凌 (Glacial Spike)**
    *   **效果**: 满蓄力射击的箭矢转化为冰凌，无视目标 20% 护甲。
*   **5★ 技能: 凛冬领域 (Winter's Domain)**
    *   **触发方式**: **潜行 (Sneak) + 满蓄力射击**。
    *   **效果**: 射出一支 **极寒之箭 (Comet Arrow)**。
        *   **命中**: 在落点炸开半径 5 格的 **极寒力场**，持续 5秒。
        *   **力场效果**: 区域内敌人获得 `SLOWNESS III` + `WEAKNESS I`。且每秒受到 2 点冻伤 (Freezing Damage)。
    *   **视觉**: 箭矢拖着暴风雪尾迹，力场边缘有冰晶旋绕 (End Rod)。
    *   **CD**: 15s

### 🔥 狱火神兵 (Vulcan Sword)
*   **定位**: 战士 / 领域 / 毁灭
*   **核心属性**: `ATTACK_DAMAGE`, `FIRE_ASPECT`
*   **5★ 技能: 灰烬领域 (Domain of Cinder)**
    *   **类型**: 主动技 (右键)
    *   **效果**: 展开半径 8 格的火焰领域。
        *   **敌方**: 每秒受到火属性伤害 + 护甲降低 20% (Meltdown)。
        *   **自身**: 获得 `STRENGTH II` + `FIRE_RESISTANCE`。
    *   **视觉**: 地面投影熔岩裂纹，空气中有余烬飘散。
    *   **CD**: 120s

### 🌊 潮汐行者 (Abyssal Boots)
*   **定位**: 探索 / 水下战斗
*   **核心属性**: `SWIM_SPEED`
*   **5★ 技能: 深海主宰 (Hydro-Jet)**
    *   **类型**: 环境被动 + 主动位移
    *   **效果**: 水中获得海豚恩惠 + 水下呼吸。水中按跳跃键可进行一段 **喷气冲刺**。

### 👑 君王权冠 (Royal Crown)
*   **定位**: 辅助 / 经济
*   **核心属性**: `LUCK`
*   **5★ 技能: 点石成金 (Midas Touch)**
*   **类型**: 击杀被动
*   **效果**: 击杀亡灵生物有 10% 概率额外掉落金粒/金锭。

### 🌾 星辰镰刀 (Starry Scythe)
*   **定位**: 农业 / 范围收割 / 经济
*   **核心属性**: `ATTACK_DAMAGE` (5.0), `ATTACK_SPEED` (1.2)
*   **5★ 技能: 丰饶星域 (Astral Harvest)**
*   **类型**: 主动技 (右键)
*   **效果**:
    *   **大范围收割**: 收割以自身为中心 5x5 范围内的成熟作物。
    *   **自动补种**: 消耗背包内的种子自动补种。
    *   **星辉祝福**: 收割时有 30% 概率触发双倍掉落 (叠加 Fortune)。
    *   **视觉**: 挥动时产生金色麦浪粒子 (Gold Dust Wave)。
    *   **CD**: 无 (消耗 10 点耐久)

### ☘️ 平安扣 (Jade Amulet)
*   **定位**: 生存 / 回复
*   **核心属性**: `REGENERATION`
*   **5★ 技能: 业力轮回 (Karmic Rebirth)**
    *   **类型**: 触发被动
    *   **效果**: 生命值 < 20% 时，瞬间获得大量生命恢复与护盾。
    *   **CD**: 120s

### ⚡ 过载指环 (Overload Ring)
*   **定位**: 采集 / 效率
*   **核心属性**: `DIG_SPEED`, `MOVEMENT_SPEED`
*   **5★ 技能: 量子隧穿 (Quantum Tunneling)**
    *   **类型**: 主动工具技
    *   **效果**: 副手持有时获得 `HASTE III` + `SPEED II`。右键消耗耐久瞬间破坏前方 5 格直线方块 (隧道模式)。

### 🔨 泰坦重锤 (Titan's Hammer) - [Pending]
*   **状态**: 暂不实装 (Phase 2)
*   **定位**: 锻造专用神具
*   **设计**: 只有 5★ 锤子才能打造其他 5★ 装备。提供锻造 QTE 加成。

---

## 3. 技术架构 (Technical Architecture)

所有逻辑基于 **NexusCore** 提供的通用 RPG 模块实现：

1.  **NexusStatRegistry**: 注册所有自定义属性 (如 `CRITICAL_CHANCE`, `DODGE`).
2.  **NexusAbilityFramework**: 统一处理技能触发 (如 `ON_ATTACK`, `ON_RIGHT_CLICK`).
3.  **PDC Integration**: 物品星级存储在 `nexuscore:star_rating` (Integer) 中。

