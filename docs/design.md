> **LItem Finder = Minecraft 跨 Loader 客户端仓储智能系统**
> Core 负责“理解仓库”，Loader/Meteor 负责“连接 Minecraft”和“执行操作”。

---

# LItem Finder Design Document

## 1. 项目目标

LItem Finder 旨在提供一个跨 Minecraft 客户端平台的智能仓库管理系统。

核心能力：

- 记录玩家访问过的存储空间
- 建立本地物品索引
- 搜索物品来源
- 分析仓库结构
- 规划物品获取路线
- 优化仓库存储结构

支持：

- Fabric
- Forge
- NeoForge
- Meteor Addon

---

# 2. 核心设计原则

## 2.1 Core 与 Minecraft 解耦

Core 禁止依赖：

```
net.minecraft.*
fabric.*
forge.*
neoforge.*
meteor.*
```

Core 只处理抽象数据：

```
Item
Container
Inventory
Storage
Task
```

---

## 2.2 Core 不执行操作

Core 输出：

```
我要移动什么
从哪里
到哪里
多少数量
```

但是：

Core:

```
MoveTask
```

Loader:

```
点击槽位
打开箱子
发送packet
```

---

## 2.3 信息来源有限

LItem Finder 不扫描未知区域。

数据来源：

- 玩家打开过的容器
- 玩家背包
- 模组提供的数据接口

---

# 3. 总体架构

```
                    LItem Finder

                         |
        ------------------------------------
        |                                  |
      Core                           Platform Layer
        |                                  |
        |                     --------------------------
        |                     |      |       |        |
        |                  Fabric Forge NeoForge Meteor
        |
 ------------------------------------------------
 |
 Model
 Index
 Search
 Planner
 Optimizer
 Database
```

---

# 4. Core 模块设计

## 4.1 Model

负责定义世界模型。

---

## ItemKey

表示一个物品类型。

```java
ItemKey
{
    namespace
    id
    variant
}
```

例：

```
minecraft:diamond

create:copper_sheet
```

未来支持：

- NBT
- 附魔
- 数据组件

---

## ItemStackInfo

表示物品数量。

```java
ItemStackInfo
{
    ItemKey item;

    long count;
}
```

---

## Container

表示一个存储单位。

包括：

- 箱子
- 玩家背包
- 末影箱
- 潜影盒
- 模组背包

抽象：

```java
Container
{
    id;

    type;

    location;

    metadata;
}
```

---

## ContainerLocation

位置独立。

```java
Location
{
    dimension;

    x;

    y;

    z;
}
```

原因：

玩家背包没有坐标。

---

## InventorySnapshot

容器某一时间状态。

例如：

```
Chest A

diamond 64
iron 128
stone 500
```

---

# 5. Storage Index

## 目标

建立：

```
Item
 |
 +-- Container
        |
        +-- Nested Container
```

关系。

例如：

```
Diamond

来源:

Chest A
 └── Blue Shulker
       └── Slot 12
```

---

## 数据结构

```
ItemIndex

ItemKey
 |
 List<StorageEntry>
```

StorageEntry:

```
container
amount
path
lastUpdate
```

---

# 6. 潜影盒/嵌套容器系统

支持：

```
Chest

 └── Shulker Box

       └── Item
```

形成：

```
Container Tree
```

例如：

```
Chest#1

 └── Slot 5

      └── Shulker#A

            └── Diamond
```

---

# 7. Search Engine

## 功能

输入：

```
diamond
```

输出：

```
Container A
距离 30m
数量 128

Container B
距离 100m
数量 64
```

---

支持：

- 精确搜索
- 标签搜索
- 模组搜索
- 数量过滤

---

# 8. Storage Group

仓库分组。

例如：

```
Base Storage

├── Mineral
├── Building
├── Food
├── Redstone
└── Tools
```

---

Group 属性：

```
name

containers

rules
```

---

# 9. Storage Classification

分类规则。

例如：

```json
{
 "Mineral":[
    "#forge:ores",
    "minecraft:diamond"
 ],

 "Food":[
    "#minecraft:foods"
 ]
}
```

---

用途：

- 搜索过滤
- 整理规划

---

# 10. Storage Optimizer（核心特色）

## 定义

不是箱子排序。

而是：

> 对整个仓库进行重新组织。

类似：

Windows 磁盘碎片整理。

---

输入：

```
当前仓库状态
+
分类规则
```

输出：

```
优化方案
```

---

例：

当前：

```
Chest A:

Iron 32
Stone 64


Chest B:

Iron 128


Chest C:

Diamond 5
Iron 20
```

目标：

```
Mineral Chest:

Iron 180
Diamond 5


Building Chest:

Stone 64
```

---

生成：

```
MoveTask[]
```

---

# 11. MoveTask

核心任务单位。

```java
MoveTask
{
    source;

    target;

    item;

    amount;
}
```

例如：

```
Move:

Iron x32

Chest A

-->

Mineral Chest
```

---

# 12. Route Planner

用于获取资源。

## 输入

需求：

```
iron 500

diamond 20
```

库存：

```
Chest A
Iron 300

Chest B
Iron 200

Chest C
Diamond 20
```

---

输出：

```
Route:

Chest A

↓

Chest B

↓

Chest C
```

---

目标：

最小化：

```
距离

+
打开次数

+
操作次数
```

---

# 13. Database

第一版：

SQLite。

保存：

```
containers

snapshots

items

groups

tasks

history
```

---

# 14. Platform Adapter

## Fabric / Forge / NeoForge

负责：

- 读取 ItemStack
- 监听容器打开
- 获取世界信息
- 渲染

---

## Meteor Addon

额外能力：

### 自动导航

调用：

```
Baritone API
```

### 自动整理

执行：

```
MoveTask
```

### 自动搬运

模拟：

```
Container Click
```

---

# 15. 功能分级

## Core

✅

- 数据模型
- 索引
- 搜索
- 潜影盒解析
- 分组
- 分类
- 整理规划

---

## Client Mod

✅

- 读取容器
- HUD
- ESP
- 路线显示

---

## Meteor

增强：

- Baritone
- 自动移动
- 自动整理
- 自动取物

---

# 16. 开发阶段

## Phase 0

工程：

- Gradle
- Core
- Test

---

## Phase 1

Core：

- Model
- Index
- Search

---

## Phase 2

Persistence:

- SQLite
- Cache

---

## Phase 3

Planner:

- MoveTask
- StorageOptimizer

---

## Phase 4

Fabric:

- Container Scanner
- Render

---

## Phase 5

NeoForge / Forge

---

## Phase 6

Meteor:

- Baritone
- Automation

---

# 17. 非目标

暂不考虑：

- 服务端 Mod
- 自动扫描未访问区域
- 绕过服务器限制
- AE2 网络替代
- 自动破坏/放置方块

---

# 当前第一开发任务

> 实现 Core Model 层。

验收：

可以在纯 Java 环境模拟：

```
10个箱子
100种物品
嵌套潜影盒

搜索物品

生成整理方案
```

并通过单元测试。

---

# 18. Core Model v0.1 约定

当前实现将设计中的抽象落实为以下纯 Java 约束：

- 所有游戏注册表标识使用显式的 `namespace:path`，不隐式补全命名空间。
- `ItemKey.variant` 是可选的、不透明的适配器数据；Core 不直接解析 NBT 或数据组件。
- 物品数量使用正 `long`，避免把空槽伪装成数量为零的物品；空槽不写入快照。
- 容器类型使用可扩展的命名空间标识，而不是封闭枚举，以容纳模组容器。
- 位置分为带世界/服务器作用域的方块坐标和无坐标逻辑位置。
- `InventorySnapshot` 是带采集时间的不可变快照，并允许槽位指向另一个嵌套快照。
- `ContainerPath` 从根容器开始记录“父槽位 → 子容器”的路径，供索引层使用。

Loader 必须在进入 Core 前完成 `ItemStack`、注册表 ID、维度与容器身份的转换。

---

# 19. Core Index/Search v0.1 约定

索引层首先实现内存版本，用来固定行为，再为 SQLite 提取持久化边界：

- 根快照递归展开为 `StorageEntry`，外层容器物品和内层物品都会进入索引。
- 每个条目同时保留根容器、实际承载容器、嵌套路径、槽位和观测时间。
- 同一根容器的新快照原子替换旧条目，不进行数量叠加。
- 早于当前根快照时间的更新会被忽略，防止异步事件覆盖新状态。
- 删除根容器时同时删除它产生的所有嵌套条目。
- 一个快照树内不允许重复使用容器 ID，避免同一逻辑容器产生冲突路径。
- 精确搜索严格区分 `ItemKey.variant`，并汇总所有匹配槽位的 `long` 数量。
- 没有玩家坐标作为查询输入时，结果优先按观测时间从新到旧排列。

当前内存索引只保证进程内状态，不承担历史记录或持久化；这些能力由后续仓储接口和
SQLite 实现提供。

---

# 20. Snapshot Persistence v0.1 约定

持久化边界位于 Core，SQLite 实现位于独立的 `storage-sqlite` 模块：

- `core` 只定义 `SnapshotRepository`，不依赖 JDBC 驱动或 SQLite API。
- SQLite JDBC 是 `storage-sqlite` 的运行时依赖，不传递到领域模型。
- schema 版本通过 SQLite `user_version` 管理；当前版本为 1。
- 容器、元数据和槽位使用规范化表保存，不依赖 JSON 序列化库。
- 保存根快照时在同一事务内删除旧树并写入新树，失败时整体回滚。
- 数据库已有更新快照时拒绝旧写入，与内存索引保持一致。
- 外键和级联删除保证移除根快照时不会遗留嵌套容器或槽位。
- schema 版本高于当前程序能力时拒绝打开，避免新数据被旧程序破坏。
- 应用启动时使用 `SnapshotIndexLoader` 将持久快照恢复到 `StorageIndex`。

v0.1 只保存每个根容器的最新快照，不保存变化历史；历史表和迁移脚本将在实际升级
需求出现时增加。

---

# 21. Search/Classification v0.1 约定

搜索在精确查询之外支持组合条件：

- 注册表 ID 文本包含匹配
- 命名空间精确过滤
- 一个或多个必需标签
- 跨全部槽位汇总后的最小总数量
- 给出玩家位置时按同世界、同维度直线距离排序

标签集合由 Loader 实现 `ItemTagResolver` 提供，Core 不读取 Minecraft 注册表。无法计算距离的
逻辑容器、其他世界和其他维度排在可到达世界容器之后。

分类使用可持久化规则对象，而不是闭包：精确物品、命名空间、标签和 `AnyOf` 组合。一个物品
同时匹配多个 `StorageGroup` 时，优先级更高的组获胜；优先级相同则按稳定组 ID 排序，避免
结果依赖配置载入顺序。

---

# 22. Optimizer/Route Planner v0.1 约定

`StorageOptimizer` 读取索引和 `StorageGroup` 分类结果，输出声明式 `MoveTask`：

- source 是包含嵌套路径和槽位号的精确引用。
- target 是分组配置中的首选根容器。
- 已处于目标分组根容器内的物品不产生移动。
- 未匹配规则的条目单独报告，不进行猜测。
- 作为嵌套容器载体的槽位不会与内部物品同时生成移动任务。
- Core 不知道最大堆叠数和实时空槽，因此执行器必须在点击前重新验证容量。

`RoutePlanner` 输入玩家世界位置和多个精确物品需求，输出有序 `RouteStop`：

- 只使用与起点相同世界作用域、相同维度的坐标容器。
- 每次访问会取得该容器中所有仍需要的物品，减少重复打开。
- 使用确定性的最近邻启发式，距离相同时按容器 ID 决胜。
- 无法满足的数量进入 `missing`，不会假装路线已经完成。

路线是 Core 建议，不包含导航或点击；Baritone 和容器操作仍属于平台/Meteor 层。
