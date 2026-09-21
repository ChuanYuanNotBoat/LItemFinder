# 开发准备状态

盘点日期：2026-09-21

## 初始状态

- `E:\projects\tools\working\LItemFinder` 是新建空目录。
- 目录中没有 Git 元数据、源文件、构建脚本或未提交改动。
- 没有发现适用于该目录的 `AGENTS.md`。

## 本机环境

| 项目 | 状态 |
| --- | --- |
| Git | 可用，2.54.0.windows.1 |
| 默认 Java/Javac | Oracle JDK 17.0.12 |
| JDK 21 工具链 | 已安装，Temurin 21.0.12.1，位于 Gradle 工具链目录 |
| 全局 Gradle | 未安装 |
| Gradle Wrapper | 项目准备时生成，固定使用 Gradle 8.12 |

默认 `java` 仍指向 JDK 17，但 Gradle 构建脚本明确要求 JDK 21，并可从本机 Gradle
工具链目录发现现有的 Temurin 21。日常构建应使用仓库内的 Wrapper。

## 当前准备范围

- 初始化单仓库、多模块 Gradle 基础结构，但目前只包含 `core`。
- Core 使用 Java 21，并预留 JUnit 5 测试依赖。
- 增加轻量的 Core 导入边界检查。
- SQLite 驱动已隔离到独立模块；仍未创建任何 Minecraft Loader 模块。

## Core Model 实现状态

- 已建立 `dev.litemfinder.core.model`，全部类型仅依赖 Java 标准库。
- 已实现物品标识和数量、容器身份/类型、世界与逻辑位置。
- 已实现不可变容器快照、嵌套容器槽位和容器路径。
- 已添加 JUnit 5 测试，覆盖标识校验、不可变性、槽位约束与嵌套快照。

## Core Index/Search 实现状态

- 已实现递归快照到 `StorageEntry` 的展开，保留根容器和完整嵌套路径。
- 已定义 `StorageIndex`，并提供线程安全的 `InMemoryStorageIndex`。
- 索引支持根快照替换、旧更新拒绝、根容器删除和精确物品查询。
- 已定义 `SearchEngine`，支持精确查询、variant 隔离、数量汇总和稳定排序。
- 集成测试已覆盖 10 个容器、100 种物品的纯 Java 场景。

## Snapshot Persistence 实现状态

- 已在 Core 定义技术无关的 `SnapshotRepository` 和启动索引恢复器。
- 已新增独立 `storage-sqlite` 模块，固定 SQLite JDBC 3.53.4.0。
- schema v1 保存根快照、递归容器、元数据、槽位、variant 和观测时间。
- 写入使用事务，支持旧快照拒绝、完整替换和外键级联删除。
- 集成测试覆盖关闭后重新打开数据库、模型完整往返和索引重建。
- 高于当前支持版本的数据库 schema 会被安全拒绝。

## Search/Classification 实现状态

- 搜索支持 ID 文本、命名空间、标签、最小总量和玩家位置组合条件。
- 世界距离计算只在相同作用域和维度内进行，逻辑位置保持不可路由。
- 已定义技术无关的标签解析接口和可持久化物品分类规则。
- 已实现带优先级的仓库分组，以及不依赖输入顺序的冲突处理。

## Optimizer/Route Planner 实现状态

- 已实现声明式 `MoveTask`、精确嵌套源槽位和确定性目标分组选择。
- 整理器区分已正确放置、未分类和嵌套容器本体，避免冲突计划。
- 已实现多物品路线、同维度距离排序、单站合并拾取和缺货报告。
- Core v1 的模型、索引、搜索、持久化、分类、整理和路线范围已完成。

## 下一阶段状态

- 已选择 NeoForge 1.21.1 作为首个 Loader 和 Minecraft 版本。
- 已完成只读容器采集阶段的模块、线程、身份、组件、持久化、测试与发布计划。
- 计划基线为 NeoForge 21.1.251、ModDevGradle 2.0.147、Gradle 9.2.1、Java 21。
- 实现尚未开始；当前 Wrapper 仍为 Gradle 8.12，升级属于下一阶段 M0 的独立验证提交。
- 详细计划见 `docs/next-phase-neoforge-1.21.1.md`。

## 待确认的设计决策

- 发布坐标和 Java 包名目前使用 `dev.litemfinder`，正式发布前仍可调整。
- 首个目标已确定为 Minecraft 1.21.1 + NeoForge；Fabric 1.21.1 在适配边界稳定后跟进。
- `ItemKey` 已预留不透明 `variant`；数据组件/NBT 的规范化与模糊匹配规则仍需在 Loader 接入前确定。
- SQLite 已确定使用独立 `storage-sqlite` 模块；历史快照保留和 schema 迁移策略仍待确定。
