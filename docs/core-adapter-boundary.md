# Core 与游戏适配层

新增能力先确定领域模型与规则。凡是不需要 Minecraft 实例、原生物品/流体对象、游戏事件或界面绘制
就能表达和测试的规则，应放在 Core。具体整合包中的故障可以触发修复，但通用解决方案不能只留在
`neoforge-1.21.1` 中。

| 职责 | 位置 |
| --- | --- |
| 观测完整性、可见槽位覆盖、旧页保留、过期更新拒绝 | `core.snapshot.InventoryObservation`、`SnapshotMerger` |
| 同槽同身份容器读取不可用时递归保留旧内容、确认清空后替换 | `core.snapshot.NestedInventoryObservation`；Loader 提供读取状态与不透明稳定身份 |
| 物品内液体、独立储罐、数量/容量/时间、持久化字段协议 | `core.snapshot.FluidSnapshotMetadata`、`ItemFluidSnapshotMetadata` |
| 物品身份与液体身份、嵌套路径、世界/逻辑位置 | `core.model` |
| 世界/服务器身份归一化与隐私 scope 哈希协议 | `core.model.WorldScopeId`；Loader 负责取得原始存档/连接身份 |
| 容器分组、液体汇总、按根/路径筛选、地图停靠点、体积单位 | `core.view` |
| 物品聚合、数量编辑规则、精确来源分配/预留、会话需求与停靠排序 | `core.view.InventoryOverview`、`QuantityFormat`、`AcquisitionPlan`、`AcquisitionDraft`、`ContainerRouteOptimizer` |
| 精确 variant 的嵌套内容汇总、玩家根快照定位 | `core.view.ContainerPreview` |
| 路线展示几何、短前缀插值、淡出/淡入和跨 scope 重置 | `core.view.MapRoute`、`RouteTransition` |
| 导航摘要状态、缺口、方式、逐段估时、停靠覆盖及证据约束 | `core.view.NavigationSummary`、`NavigationReport`；Loader 负责 provider 投影与本地化绘制 |
| 已加载对象的读取、客户端同步状态、原生组件编解码 | 当前 Loader 的 `capture`、`mapping` |
| Sophisticated、TFC、Jade 等模组的可选 API/反射 | 当前 Loader 的 `compat` |
| 兼容插件发现、版本条件、原生类型分发与故障隔离 | 当前 Loader 的 `compat.InventoryPluginLoader`、`capture.TypedInventoryAdapterRegistry` |
| 搜索框、切换按钮、列表组件、图标/纹理、地图绘制 | 当前 Loader 的 `client.gui` |
| 可选 provider 投影、异步请求生命周期、玩家渲染插值与 2D/3D 路线绘制 | 当前 Loader 的 `client.gui.map.RoutePresentation`、`ContainerMapScreen` |
| 数值碰撞面、身体通行规则、已知连接图、搜索与路径压缩 | `navigation-core`，不含 Minecraft 对象 |
| 已加载区块、原生碰撞/危险读取、分帧采样、结果复核和可选库存桥接 | `navigation-neoforge-1.21.1` |
| 数据库连接、事务、SQLite 实现 | `storage-sqlite` |

## 观测协议

适配器先确认真实物理库存的身份，再转换为不可变 Core 快照。`InventoryObservation` 单独声明本次
看见的槽位，包括已确认空槽；快照只包含非空物品。局部页面只能替换覆盖范围，完整观测才能删除
整份旧库存。读取不可用不等于成功读到空容器，不能提交伪造的空观测。

```java
InventoryObservation observation = new InventoryObservation(snapshot, visibleSlots, false);
SnapshotMerger.merge(previous, observation).ifPresent(repository::save);
```

该调用只依赖 Core，不需要游戏启动、NeoForge 或任何第三方模组。方块替换与世界同步是否可信由
适配器判断；替换后的来源传入 `previous = null`，Core 不读取 `minecraftBlock` 来猜测实体生命周期。
NeoForge 的 `PartialSnapshotMerger` 只保留方块身份和原生布局检查，共用 Core 的实际合并规则。

嵌套容器使用 `nestedUnavailableSlots` 声明读取暂不可用，`nestedIdentity.<slot>` 可提供不透明稳定身份。
只在相同根和槽位、相同载体或已确认的稳定身份下保留旧子树；保留子树沿用原观测时间，并标记
`nestedReadUnavailable` 供界面提示。移除、更换或成功读取为空不会保留。具体背包 UUID、组件和缓存
读取属于 Loader，Core 不解析它们；升级展示 metadata 不参与物品聚合。

液体记录使用标准化 mB 数量与容量。variant 与显示模板是适配器生成的不透明数据，Core 不解析
JSON、NBT 或游戏组件。未知容量与空液体是不同状态。物品液体沿用 `itemFluids.*`，方块液体沿用
`fluids.*`；本次下沉保留版本 1 字段和 SQLite schema，不要求清空历史索引。

## 界面边界

物品与液体共用宿主页面的标题、搜索框、地图与设置，切换下半部分列表。`FluidInventoryPanel`
只负责绘制与输入，汇总、根容器和嵌套路径筛选调用 Core。两种列表共用 `InventoryListStyle`，液体
不用独立页面或来源列表。地图从 Core 的按根液体投影获得容器内容和停靠点，再由 Loader 绘制。

## 验证

- 新的通用规则先在 `core/src/test` 中用普通 Java 数据测试，再补充游戏适配链路的集成回归。
- `:core:check` 验证 Core 测试和导入边界，禁止游戏、Loader、具体模组和持久化实现的依赖。
- `check` 验证全部模块及安装 JAR；SQLite 回归验证旧版 metadata 字段仍可读取。
- Core 功能通过适配层接线使用，避免复制一套只服务当前游戏版本的规则。

液体与分页观测链路、物品聚合、数量规则、精确来源分配、会话需求和停靠排序已下沉 Core。
会话创建/清理仍由客户端生命周期调用；翻译标签和最大堆叠数由 Loader 传入通用规则。
其他既有界面状态/视图仍可逐步整理；不为尚未实现的 Loader 建空模块，不将原生模组读取移入 Core。
