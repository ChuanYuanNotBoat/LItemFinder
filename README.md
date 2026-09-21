# LItemFinder

LItemFinder 是一个面向 Minecraft 客户端的物品与容器索引项目。当前阶段搭建与
Minecraft API 无关的 Core 和独立持久化模块；Fabric、Forge、NeoForge 和 Meteor 集成将在
Core 的领域模型、存储与搜索接口稳定后再单独接入。

## 当前结构

```text
LItemFinder/
├── core/             # 纯 Java 领域模型、索引、存储接口、搜索与规划
├── storage-sqlite/   # 独立的 SQLite 快照持久化实现
└── docs/             # 架构记录和开发状态
```

计划中的 Loader 模块不属于 Core：

```text
fabric/         # Minecraft/Fabric 事件与数据适配
forge/          # Minecraft/Forge 适配
neoforge/       # Minecraft/NeoForge 适配
meteor/         # 可选的 Meteor 自动化层
```

这些目录暂未创建，以免在核心接口尚未确定时引入映射、Mixin 和 Loader 构建配置。

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

SQLite 持久化基础结构已经包含：

- 版本化数据库 schema 和完整递归快照往返
- 事务性替换、旧快照拒绝和外键级联删除
- 重启后从持久快照恢复内存索引
- 隔离在独立模块中的 SQLite JDBC 运行时依赖

下一步：

1. 增加名称、命名空间、数量和标签查询模型。
2. 增加基于玩家位置的距离计算与结果排序。
3. 设计 SQLite schema 迁移和可选历史快照保留策略。
4. Core 稳定后，先选择一个 Loader 实现只读容器采集适配。
