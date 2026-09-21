# LItemFinder Core v1

Core v1 是 LItemFinder 与 Minecraft 平台集成之间的稳定边界。它可以在纯 Java 环境中完成
容器建模、索引、查询、分类、持久化、整理规划和取物路线规划。

## 模块

| 模块 | 职责 | 运行时依赖 |
| --- | --- | --- |
| `core` | 模型、索引、搜索、分类、整理、路线和持久化接口 | 无 |
| `storage-sqlite` | 最新根容器快照的 SQLite 实现 | SQLite JDBC |

Minecraft、Fabric、Forge、NeoForge、Meteor、Baritone 和渲染类型均不属于这些模块。

## 已完成能力

- 不可变物品、容器、位置、槽位和递归快照模型
- 潜影盒等嵌套容器的完整路径展开
- 可替换、可删除并拒绝旧快照的线程安全内存索引
- 精确、文本、命名空间、标签、数量和距离组合查询
- 可扩展标签解析、分类规则与带优先级的仓库分组
- 事务性 SQLite 快照保存和启动索引恢复
- 将错误分类的叶子物品转换为声明式 `MoveTask`
- 多物品需求的可到达容器路线及缺货报告

## 典型数据流

```text
Loader ItemStack / Menu
          |
          v
InventorySnapshot ---> SnapshotRepository (SQLite)
          |
          v
StorageIndex ---> SearchEngine
          |             |
          |             +--> SearchResponse
          |
          +--> StorageClassifier ---> StorageOptimizer ---> MoveTask[]
          |
          +--> RoutePlanner -----------------------------> RoutePlan
```

Loader 只负责采集、显示和执行。Core 不点击槽位、不发送数据包，也不尝试扫描玩家未访问的区域。

## v1 行为边界

- `ItemKey.variant` 是 Loader 生成的不透明稳定字符串；Core 不直接解释 NBT 或数据组件。
- 标签通过 `ItemTagResolver` 提供，Core 不访问游戏注册表。
- 整理器只规划叶子物品，不同时移动嵌套容器本体及其内容。
- `MoveTask` 不推测物品最大堆叠数或目标空槽；Loader 在执行前必须重新验证容量和实时状态。
- 路线规划使用确定性的最近邻启发式，不承诺旅行商问题的全局最优解。
- 路线只包含与起点处于相同世界作用域和维度的坐标容器；逻辑容器出现在搜索中，但不可导航。
- SQLite schema v1 只保留每个根容器的最新快照，不保留历史版本。

## 验收

根项目的以下命令必须通过：

```powershell
.\gradlew.bat clean check
```

测试覆盖：

- 10 个容器、100 种物品的索引与查询
- 嵌套潜影盒路径
- 快照替换、旧数据拒绝和删除
- SQLite 关闭后重新打开及索引恢复
- 标签分类和冲突优先级
- 整理任务生成、嵌套容器保护
- 多物品路线、跨维度排除和缺货报告

公开仓库的 CI 会在 JDK 21 上执行同一套 `check` 任务。
