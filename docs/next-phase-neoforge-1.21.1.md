# 下一阶段计划：NeoForge 1.21.1 只读采集适配器

状态：**实施中；M0-M4 已完成，正在进行 M5 Alpha 验收**
计划基线日期：2026-09-21

## 1. 阶段目标

在不改变 Core 边界的前提下，实现第一个真实 Minecraft 客户端适配器：

```text
Minecraft / NeoForge 事件与菜单
                |
                v
       NeoForge 1.21.1 Adapter
                |
                v
InventorySnapshot -> StorageIndex -> SnapshotRepository
```

本阶段结束时，客户端应能记录玩家实际打开过的支持容器，在重启后恢复索引，并通过日志或
开发调试入口验证 Core 搜索结果。此阶段只采集，不自动点击、不导航、不扫描未访问区域。

## 2. 版本与工具链基线

| 项目 | 计划值 | 说明 |
| --- | --- | --- |
| Minecraft | `1.21.1` | 第一支持版本，暂不声明跨补丁兼容 |
| NeoForge | `21.1.251` | 采用计划日期时官方 1.21.1 MDK 基线 |
| ModDevGradle | `2.0.147` | 使用官方 1.21.1 ModDevGradle 模板路线 |
| Gradle Wrapper | `9.2.1` | 先验证现有 Core/SQLite 构建后再升级 |
| Java | `21` | 与 Minecraft 1.21.1 和现有 Core 一致 |
| Parchment | `2024.11.17` for 1.21.1 | 仅改善参数名和文档，不改变正式映射身份 |
| Mod ID | `litemfinder` | 客户端专用入口 |
| License | `GPL-3.0` | 与仓库保持一致 |

参考：

- [NeoForge 1.21.1 Getting Started](https://docs.neoforged.net/docs/1.21.1/gettingstarted/)
- [NeoForge 官方 1.21.1 ModDevGradle MDK](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle)
- [ModDevGradle 官方项目](https://github.com/neoforged/ModDevGradle)

版本号只在 M0 完成时锁定一次。本阶段中途不追逐 1.21.1 的新补丁，安全或构建阻断修复除外。

## 3. 模块与包结构

新增模块：

```text
neoforge-1.21.1/
├── build.gradle.kts
└── src/
    ├── main/java/dev/litemfinder/neoforge/
    │   ├── LItemFinderNeoForgeClient.java
    │   ├── capture/
    │   ├── identity/
    │   ├── mapping/
    │   ├── persistence/
    │   └── lifecycle/
    ├── main/resources/
    │   └── META-INF/neoforge.mods.toml
    └── test/java/dev/litemfinder/neoforge/
```

依赖方向固定为：

```text
neoforge-1.21.1 -> storage-sqlite -> core
neoforge-1.21.1 ------------------> core
```

`core` 和 `storage-sqlite` 不得反向依赖 Minecraft 或 NeoForge。NeoForge 类型不得出现在它们的
公开 API、测试 fixture 或持久化 schema 中。

## 4. 运行侧与线程模型

- Mod 使用 `Dist.CLIENT` 客户端入口；不向服务器注册协议或服务端逻辑。
- 所有 `Minecraft`、菜单、槽位和 `ItemStack` 读取只发生在客户端线程。
- 离开客户端线程前，数据必须完全转换为不可变 Core 模型。
- 索引和 SQLite 写入由单线程后台执行器串行处理。
- 同一 `ContainerId` 的待写快照可合并，只保留队列中最新值，避免快速拖动物品造成写入风暴。
- 世界退出和客户端关闭时停止接收新任务、限时排空队列并关闭仓储。
- UI 读取仍通过线程安全的 `StorageIndex`，不得读取活的 Minecraft 菜单对象。

NeoForge 官方说明客户端代码位于独立物理侧，客户端专用入口应通过 `Dist.CLIENT` 隔离：
[Mod Files](https://docs.neoforged.net/docs/1.21.1/gettingstarted/modfiles/)。

## 5. 采集生命周期

第一版采用事件加轻量轮询，不使用 Mixin：

1. 容器 Screen 打开时登记候选菜单，延迟一个客户端 tick 等待初始槽位同步。
2. Screen 保持打开期间，每个客户端 tick 计算轻量内容指纹。
3. 指纹变化后进行防抖；稳定后创建完整 `InventorySnapshot`。
4. Screen 关闭前提交最后一次快照。
5. 只有快照内容变化才写入索引和 SQLite。

菜单同步本身由 `Slot`/`DataSlot` 在客户端更新，相关基础行为见
[NeoForge Menus 文档](https://docs.neoforged.net/docs/1.21.1/gui/menus/)。如果公开事件无法可靠覆盖
关闭前的最后状态，先增加显式状态协调器；只有在事件方案有可复现缺口时才评估最小 Mixin。

默认参数：

- 打开后延迟：1 tick
- 变化防抖：5 ticks
- 最大待写根容器：128
- 关闭排空超时：3 秒

这些值进入客户端配置，但 M1-M3 期间先保持固定以减少变量。

## 6. 容器身份策略

`ContainerId` 必须在重新打开和客户端重启后稳定，且不同服务器/世界不得碰撞。

### 6.1 Scope

- 单人世界：规范化存档身份后取 SHA-256。
- 多人服务器：规范化服务器地址后取 SHA-256。
- 数据库与 metadata 不保存原始服务器地址或本地存档路径。

格式：`scope:v1:<sha256>`。

### 6.2 Resolver 链

按以下顺序尝试：

1. 玩家背包：`player:<scope>:<uuid>:inventory`
2. 末影箱：`player:<scope>:<uuid>:ender_chest`
3. 方块容器：`block:<scope>:<dimension>:<x>,<y>,<z>:<menu_type>`
4. 实体容器：`entity:<scope>:<dimension>:<uuid>`
5. 模组专用 resolver
6. 未解析的会话身份

方块身份由最近一次客户端交互目标提供，只在菜单于限定 tick 窗口内打开且类型校验通过时使用。
双箱需要将两半坐标规范化为同一根 ID。无法确认位置的菜单只进入内存会话，不写入长期数据库，
避免把不同远程容器误合并。

每个结果带内部置信度：`EXACT`、`LOGICAL`、`SESSION_ONLY`。只有前两类允许持久化。

## 7. 槽位边界与支持矩阵

菜单通常同时包含容器槽和玩家背包槽。适配器必须先确定容器槽集合，再生成快照：

- 不通过“固定减去最后 36 格”作为唯一规则。
- 优先依据槽位的 backing storage、已知菜单布局和专用 resolver。
- 输出槽、合成临时槽以及玩家背包槽不得作为根容器内容写入。
- 无法可靠分区的模组菜单标记为 unsupported，不保存猜测数据。

M3 必须支持：

- 单箱与双箱
- 陷阱箱
- 木桶
- 潜影盒 Screen
- 末影箱
- 玩家背包、装备和副手（常驻轻量指纹观察，不要求打开界面）
- 熔炉、高炉、烟熏炉
- 漏斗、酿造台、发射器和投掷器
- 合成器的 9 个持久输入槽，不记录虚拟结果槽

工作台、铁砧、附魔台、村民交易和创造物品选择等临时或虚拟菜单不作为持久容器保存。任意模组
容器是尽力支持项，不是本阶段阻断条件。

## 8. ItemStack 转换

### 8.1 基本身份与数量

- 注册表 ID -> `NamespacedId`
- stack count -> 正 `long`
- `ItemStack.EMPTY` -> 不生成 `SlotSnapshot`
- 读取前复制或在同一客户端 tick 内完成转换，不把可变 stack 传给后台线程

### 8.2 Variant v1

Minecraft 1.21.1 的 stack 差异存储在 Data Components 中。官方说明见
[Data Components](https://docs.neoforged.net/docs/1.21.1/items/datacomponents/) 和
[ItemStack](https://docs.neoforged.net/docs/1.21.1/items/)。

计划算法：

1. 复制 stack 并将 count 设为 1。
2. 使用带注册表上下文的官方 codec 编码可持久化组件。
3. 移除已经由 `ItemKey.itemId` 表达的 ID 和 count。
4. 递归排序对象键；数组/列表保持原顺序。
5. 对规范化 UTF-8 数据取 SHA-256。
6. 无非默认持久化组件时 variant 为空，否则为 `components:v1:<hex>`。

必须以测试固定以下行为：不同 count 得到相同 variant；组件键顺序不影响结果；不同损伤、附魔、
药水或自定义数据得到不同 variant。没有持久化 codec 的临时组件不进入 v1 指纹，并记录一次调试
日志。

### 8.3 标签

在客户端线程从 Item Holder 取得标签，转换为 `Set<NamespacedId>` 后缓存。`ItemTagResolver` 只返回
不可变 Core 标识，不在搜索线程访问游戏注册表。资源重载或断开世界时清空缓存。

## 9. 嵌套容器

第一支持目标是 `minecraft:container` 数据组件中的潜影盒内容：

- 子 `ContainerId` 由根 ID 和完整槽位路径派生，不依赖显示名或颜色。
- 默认最大递归深度为 8，可配置范围为 1-16。
- 超过深度或无法解码时保留外层物品并记录截断 metadata，不抛弃整个根快照。
- 路径中的每个子 ID 必须唯一，满足 `SnapshotFlattener` 的树约束。
- Bundle 和模组自定义容器组件列为后续扩展，不以字符串/NBT 猜测。

容器组件的官方背景见
[NeoForge Containers 文档](https://docs.neoforged.net/docs/1.21.1/inventories/container/)。

## 10. 持久化与打包

数据库位置：

```text
<game-dir>/config/litemfinder/index/<scope-hash>.db
```

每个 scope 单独数据库；切换服务器或世界时关闭旧仓储并载入对应索引。

NeoForge 1.21.8 及以下不会自动加载普通非 Minecraft 运行时依赖。开发运行需要显式 runtime
classpath，生产包需要 Jar-in-Jar：

- `core`、`storage-sqlite` 和 `sqlite-jdbc` 必须在开发客户端可见。
- 发布任务使用 NeoForge `jarJar`，不使用会破坏 SQLite native resources 的简单 fat-jar 合并。
- Windows 和 Linux 各进行一次打开数据库、写入、关闭、重启读取 smoke test。
- 发布物中保留第三方许可证信息。

参考：

- [Non-Minecraft Dependencies](https://docs.neoforged.net/toolchain/docs/dependencies/nonmclibs/)
- [Jar-in-Jar](https://docs.neoforged.net/toolchain/docs/dependencies/jarinjar/)

## 11. 里程碑

### M0 — 构建与依赖验证

- [x] 升级 Wrapper 到官方 MDK 使用的 Gradle 9.2.1。
- [x] 保证原有 24 项测试和 Core 零运行时依赖检查继续通过，并增加 1 项 SQLite runtime probe 测试。
- [x] 新建 `neoforge-1.21.1` 模块和客户端专用空入口。
- [x] `runClient` 能进入主菜单。
- [x] 验证 Core/SQLite/SQLite native library 在开发和 Jar-in-Jar 两种运行方式下可加载。

退出条件：**已满足（2026-09-21）**。构建、主菜单启动、数据库 smoke test 和隔离成品 JAR
启动全部通过；详细证据见 [M0 验收记录](validation/m0-neoforge-1.21.1.md)。

### M1 — 映射与纯协调器

- [x] `ResourceLocation`、ItemStack 和标签转换器。
- [x] variant v1 规范化及特征测试。
- [x] scope、方块、玩家、实体和会话身份 resolver。
- [x] 不依赖 Minecraft 类型的防抖/合并协调器测试。

退出条件：**已满足（2026-09-21）**。相同组件输入稳定生成同一 Core variant；物品数量不进入
variant；原始服务器地址和存档路径只作为 SHA-256 输入，不进入 scope、容器 metadata 或数据库。
详细证据见 [M1 验收记录](validation/m1-mapping-identities.md)。

### M2 — 菜单发现与槽位分区

- [x] Screen 打开/关闭与客户端 tick 生命周期。
- [x] 玩家背包槽排除、已知原版持久菜单 resolver。
- [x] 内容指纹与仅变化时采集。
- [x] 开发日志显示容器 ID、槽位数和采集原因，不打印物品敏感组件全文。

退出条件：**已满足（2026-09-22）**。支持矩阵中的基础容器不会混入玩家背包槽，不支持菜单不会
被误保存。M2-M4 的游戏内证据见 [M2-M4 验收记录](validation/m2-m4-neoforge-alpha.md)。

### M3 — 快照、嵌套与持久化

- [x] 创建根 `InventorySnapshot`。
- [x] 解析潜影盒嵌套内容和稳定路径。
- [x] 写入 `InMemoryStorageIndex` 与 `SqliteSnapshotRepository`。
- [x] 切换 scope 和客户端退出时正确排空/关闭。

退出条件：**已满足（2026-09-22）**。修改、关闭、重开容器后只保留最新状态；实测重启恢复 5 个
既有根容器，并在后续测试中继续恢复扩展后的索引。详细证据见
[M2-M4 验收记录](validation/m2-m4-neoforge-alpha.md)。

### M4 — 查询调试面与可观察性

- [x] 提供仅开发用途的命令或简易调试入口：精确查询、文本查询、索引统计、删除当前 scope 数据。
- [x] 记录被跳过菜单和身份失败的原因计数。
- [x] 日志可在不打开 debug 时保持安静。

退出条件：**已满足（2026-09-22）**。`stats`、文本查询和精确物品 ID 查询已在真实客户端执行；
删除命令要求显式 `clear confirm`，本阶段不制作正式 HUD。详细证据见
[M2-M4 验收记录](validation/m2-m4-neoforge-alpha.md)。

### M5 — 兼容、打包与 Alpha 验收

- [x] 完成第一批原版持久容器兼容矩阵。
- [ ] 至少两个常见模组容器的探索性测试。
- [ ] Windows/Linux Jar-in-Jar smoke test。
- [x] CI 编译 NeoForge 模块并运行纯 Java 测试。
- [x] 生成带 GPL-3.0 与第三方许可证的可安装 JAR。
- [x] 更新用户安装、数据位置和卸载说明。

退出条件：满足第 12 节 Definition of Done，产物标记为 `0.2.0-alpha.1` 候选。

## 12. Definition of Done

### 自动验收

- `gradlew clean check` 通过，现有 Core/SQLite 测试无回归。
- NeoForge 模块使用 Java 21 编译。
- Core 边界检查仍确认没有 Minecraft/Loader 导入。
- variant、身份、防抖、槽位分区和嵌套映射具有自动化测试。
- 发布 JAR 包含所需依赖并能加载 SQLite native library。

### 游戏内验收

- 打开支持容器后索引出现正确物品和数量。
- 菜单中的玩家背包物品不会计入箱子。
- 拖动、快速点击或服务端同步不会造成数量叠加。
- 重新打开同一容器会替换原记录，而不是创建重复根。
- 未打开过的容器不会出现在索引中。
- 潜影盒内部物品具有正确 `ContainerPath`。
- 退出并重启后搜索结果从 SQLite 恢复。
- 切换服务器/世界不会串库。
- 断开连接和关闭客户端没有未处理异常或损坏数据库。

### 发布验收

- Mod 元数据明确声明客户端用途、Minecraft 1.21.1、NeoForge 版本范围和 GPL-3.0。
- 安装 JAR 在干净的 NeoForge 1.21.1 客户端启动。
- 不要求服务端安装，不发送自定义网络数据包。
- README 明确数据目录、已知限制和删除索引方法。

## 13. 明确非目标

本阶段不实现：

- 正式搜索 HUD、ESP 或路线渲染
- 自动点击、搬运、Baritone 或 Meteor
- Fabric/Forge 适配
- 未访问区域或关闭容器的扫描
- 数据包注入、绕过服务器限制或后台世界读取
- 所有模组菜单的通用猜测支持
- SQLite 历史快照和 schema v2 迁移

## 14. 风险与决策门

| 风险 | 处理方式 | 决策门 |
| --- | --- | --- |
| Gradle 9 升级破坏现有模块 | M0 单独提交，先运行全部回归测试 | 未通过则评估兼容的 MDK/Wrapper 组合 |
| SQLite native library 无法从 Jar-in-Jar 加载 | M0 提前制作最小打包 smoke test | 失败则拆分可选 sidecar 或改用受支持打包方式 |
| 菜单无法可靠得到世界位置 | resolver 链与 `SESSION_ONLY` 降级 | 不持久化不确定身份，不猜坐标 |
| 模组菜单无法区分玩家槽 | 菜单专用 resolver 注册表 | 不支持优于错误索引 |
| Data Components 编码不稳定 | canonical variant v1 和 golden tests | 编码方案稳定前不写正式数据库 |
| 每 tick 采集造成卡顿 | 指纹、5 tick 防抖、后台串行写入 | 以客户端 profiler 数据调整阈值 |
| 客户端断线时丢最后快照 | close 前最终采集和限时排空 | 超时只丢待写数据，不阻塞退出 |

## 15. 建议提交序列

1. `chore(build): add NeoForge 1.21.1 toolchain`
2. `feat(neoforge): add client-only adapter skeleton`
3. `feat(neoforge): map item stacks tags and identities`
4. `feat(neoforge): capture supported container menus`
5. `feat(neoforge): persist nested container snapshots`
6. `feat(neoforge): add capture diagnostics and scope controls`
7. `build(neoforge): package runtime dependencies for alpha`

每个提交都必须保持根 `check` 可通过。M0、M3 和 M5 完成后进行阶段性推送与验收记录。
