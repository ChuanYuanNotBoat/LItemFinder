# LItemFinder

[![CI](https://github.com/ChuanYuanNotBoat/LItemFinder/actions/workflows/ci.yml/badge.svg)](https://github.com/ChuanYuanNotBoat/LItemFinder/actions/workflows/ci.yml)

LItemFinder 是一个面向 Minecraft 客户端的物品与容器索引项目。纯 Java Core v1 已完成，
NeoForge 1.21.1 只读采集适配器已进入 `0.2.0-alpha.1` 验收阶段。

它只记录玩家实际打开过的容器，不扫描未访问区块、不发送自定义网络包，也不执行自动点击或搬运。

## Alpha 安装

要求：

- Minecraft `1.21.1`
- NeoForge `21.1.251` 或同一 1.21.1 分支的更高版本
- Java 21

将 `litemfinder-neoforge-1.21.1-0.2.0-alpha.1.jar` 放入客户端的 `mods` 目录即可；服务端不需要
安装。当前开发版已有库存总览、精确 variant 的会话获取计划和容器地图；按 `O`（可在按键设置中
改绑）或输入 `/litemfinder gui` 打开。地图在未安装导航提供者时只显示停靠点示意连接线。

已支持的原版持久容器：

- 单箱、双箱、陷阱箱、木桶、潜影盒、末影箱和玩家背包
- 熔炉、高炉、烟熏炉、漏斗、酿造台、发射器、投掷器和合成器

工作台、铁砧、村民交易和创造模式物品选择等临时或虚拟菜单不会写入长期索引。

## 游戏内调试命令

这些命令只在客户端执行：

```text
/litemfinder stats
/litemfinder gui
/litemfinder search <文本>
/litemfinder exact <namespace:item>
/litemfinder clear
/litemfinder clear confirm
```

`clear` 本身只显示确认提示；只有完整输入 `clear confirm` 才会删除当前世界或服务器的索引。

## 数据与卸载

索引按世界或服务器隔离，存放于：

```text
<游戏目录>/config/litemfinder/index/<scope-hash>.db
```

文件名、容器 ID 和 metadata 不保存原始服务器地址或本地存档路径。若要卸载，先正常退出游戏，
删除 `mods` 中的 LItemFinder JAR；若也要移除索引，再删除 `config/litemfinder` 目录。删除索引不可撤销。

## 当前结构

```text
LItemFinder/
├── core/              # 纯 Java 领域模型、索引、存储接口、搜索与规划
├── storage-sqlite/    # 独立的 SQLite 快照持久化实现
├── neoforge-1.21.1/   # NeoForge 1.21.1 客户端适配器
├── navigation-core/   # 独立的纯 Java 导航计算原型；不打入 LItemFinder 安装 JAR
└── docs/              # 架构记录、计划和验收记录
```

计划中的 Loader 模块不属于 Core：

```text
fabric/         # Minecraft/Fabric 事件与数据适配
forge/          # Minecraft/Forge 适配
meteor/         # 可选的 Meteor 自动化层
```

Fabric、Forge 和 Meteor 仍未创建；它们会在 NeoForge 适配边界稳定后接入。

## 架构边界

- `core` 只使用 Java 标准库和明确选择的通用库。
- `core` 不得导入 Minecraft、Fabric、Forge、NeoForge 或 Meteor 类。
- 游戏内的 `ItemStack`、容器位置、NBT/组件数据必须先由 Loader 转换为 Core 自有模型。
- SQLite 等持久化实现依赖 Core 接口，而不是让领域对象依赖数据库或游戏类。
- Loader 负责采集与展示；搜索、索引和规划规则留在 Core。

`core` 的 `check` 任务包含一个轻量导入检查，用来尽早发现 Loader 依赖越界。

## 构建

要求 JDK 21。项目使用 Gradle Wrapper，不需要全局安装 Gradle。

Windows：

```powershell
.\gradlew.bat check
```

macOS / Linux：

```bash
./gradlew check
```

启动 NeoForge 开发客户端：

```powershell
.\gradlew.bat :neoforge-1.21.1:runClient
```

验证实际安装 JAR 及其 Jar-in-Jar 依赖：

```powershell
.\gradlew.bat :neoforge-1.21.1:runPackagedClient
```

两个运行任务只用于开发。普通 `runClient` 从源码运行；`runPackagedClient` 在隔离目录中仅加载
构建后的安装 JAR。

本机环境盘点见 [docs/development-status.md](docs/development-status.md)。

## 许可证

本项目使用 [GNU General Public License v3.0](LICENSE) 发布。

## 当前进度

Core model 基础结构已经包含：

- `ItemKey` 与可扩展的命名空间标识
- `ContainerRecord`、世界坐标位置和无坐标逻辑位置
- 不可变的 `InventorySnapshot`、槽位内容与嵌套容器
- 用于索引结果的 `ContainerPath`

Core index/search 基础结构已经包含：

- 将递归容器快照展开为带完整路径的 `StorageEntry`
- 可替换、可删除并防止旧快照倒灌的 `StorageIndex`
- 用于测试和非持久会话的线程安全 `InMemoryStorageIndex`
- 区分物品 variant、汇总总数量并稳定排序的精确搜索
- 文本、命名空间、标签、最小总量和玩家距离组合查询

分类与分组基础结构已经包含：

- 不暴露游戏注册表的 `ItemTagResolver`
- 精确物品、命名空间、标签和组合分类规则
- 带优先级和确定性冲突处理的 `StorageGroup`

规划基础结构已经包含：

- 带嵌套源路径的声明式 `MoveTask`
- 保护嵌套容器本体的仓库分类整理计划
- 多物品需求、世界距离、确定性访问顺序和缺货报告

SQLite 持久化基础结构已经包含：

- 版本化数据库 schema 和完整递归快照往返
- 事务性替换、旧快照拒绝和外键级联删除
- 重启后从持久快照恢复内存索引
- 隔离在独立模块中的 SQLite JDBC 运行时依赖

NeoForge 1.21.1 适配器 M0 已经包含：

- `Dist.CLIENT` 隔离的最小 Mod 入口
- Gradle 9.2.1、ModDevGradle 2.0.147、NeoForge 21.1.251 和 Java 21 构建链
- Core、SQLite 存储层与 SQLite JDBC 的开发运行时装配
- 包含三个依赖的 NeoForge Jar-in-Jar 安装包
- 源码客户端与成品 JAR 客户端的 SQLite native smoke test

NeoForge 1.21.1 适配器 M1 已经包含：

- `ResourceLocation`、`ItemStack` 与物品标签到 Core 值的映射
- 基于持久化 Data Components 的 `components:v1:<sha256>` variant
- 不暴露存档路径或服务器地址的 `scope:v1:<sha256>`
- 玩家、末影箱、方块、实体和会话级容器身份策略
- 可独立测试的 1 tick 初始延迟、5 tick 变化防抖与按容器最新值合并
- 资源重载和断线时清空、搜索线程无需访问游戏注册表的标签缓存

NeoForge 1.21.1 适配器 M2-M4 已经包含：

- Screen 打开/关闭与客户端 tick 驱动的稳定变化采集
- 玩家物品栏常驻轻量指纹观察，拾取、丢弃、消耗、装备和耐久变化无需打开界面即可更新
- 已索引方块容器在已加载区块中被破坏或替换后，会从内存索引和 SQLite 自动删除
- 已知原版持久菜单的显式槽位边界，排除玩家背包和虚拟结果槽
- 潜影盒递归快照、每世界/服务器独立 SQLite 数据库及重启恢复
- 后台串行写入、按根容器合并和登出/关闭幂等排空
- 统计、文本搜索、精确物品查询和带确认的当前 scope 清除命令
- 与命令输出解耦的客户端查询/状态/清理接口，供后续 GUI 面板和 HUD 直接复用
- 被跳过菜单和会话级身份的诊断计数，普通采集日志保持安静

Phase 5 首批客户端界面已经包含：

- 按已记录总数降序的紧凑库存总览，物品行点击后展开纯整数获取数量编辑
- 默认 `±64`、盒/组/个滚轮、库存数量上限与设置页中的数量显示模式
- 包含空容器的只读根容器摘要查询，为后续容器地图准备数据边界
- 精确 variant 的会话获取计划，按记录槽位分配来源并报告缺货
- WorldMap 式全屏容器坐标地图、逻辑容器侧栏和按 Y 层筛选的局部投影视图

当前计划仅保存在本次游戏会话中，不自动取物。地图只绘制已记录容器；没有导航提供者时，虚线
仅是停靠点示意，不能当作实际可走路线。`navigation-core` 已有独立寻路和前置任务提案原型，
但尚无游戏世界观测、可安装导航 Mod 或实际路线提供者。

Core v1 的完成范围和集成边界见 [docs/core-v1.md](docs/core-v1.md)。

当前平台阶段的完整计划见
[docs/next-phase-neoforge-1.21.1.md](docs/next-phase-neoforge-1.21.1.md)。执行顺序为：

1. NeoForge 1.21.1 只读容器采集、SQLite 接入与 Alpha 打包。
2. 将游戏标签与组件数据规范化为 Core 的标签和 variant。
3. 实现库存总览、获取计划和容器地图；路线显示由可选导航服务接入。
4. Meteor/Baritone 自动执行属于后续独立阶段，需在任务与路线能力稳定后评估。

后续 GUI、容器地图与可选独立导航的设计及分阶段验收见
[GUI 与导航架构](docs/gui-navigation-architecture.md) 和
[实施计划](docs/gui-navigation-implementation-plan.md)。GUI 的首批功能与独立导航计算原型已实现；
实际游戏导航仍需独立 Mod 适配和实机验收。现有库存 Core 路线规划只按同维度容器直线距离安排
取物停靠点，不提供实际可通行路径。
