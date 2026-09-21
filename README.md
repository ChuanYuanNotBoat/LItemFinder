# LItemFinder

[![CI](https://github.com/ChuanYuanNotBoat/LItemFinder/actions/workflows/ci.yml/badge.svg)](https://github.com/ChuanYuanNotBoat/LItemFinder/actions/workflows/ci.yml)

LItemFinder 是一个面向 Minecraft 客户端的物品与容器索引项目。纯 Java Core v1 已完成，
当前正在实现第一个 Minecraft 平台适配器：NeoForge 1.21.1 客户端 Mod。

## 当前结构

```text
LItemFinder/
├── core/              # 纯 Java 领域模型、索引、存储接口、搜索与规划
├── storage-sqlite/    # 独立的 SQLite 快照持久化实现
├── neoforge-1.21.1/   # NeoForge 1.21.1 客户端适配器
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

Core v1 的完成范围和集成边界见 [docs/core-v1.md](docs/core-v1.md)。

当前平台阶段的完整计划见
[docs/next-phase-neoforge-1.21.1.md](docs/next-phase-neoforge-1.21.1.md)。执行顺序为：

1. NeoForge 1.21.1 只读容器采集、SQLite 接入与 Alpha 打包。
2. 将游戏标签与组件数据规范化为 Core 的标签和 variant。
3. 实现搜索 UI、HUD 和路线显示。
4. 最后再接入 Meteor/Baritone 执行 `MoveTask` 和 `RoutePlan`。
