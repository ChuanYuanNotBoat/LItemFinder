# Core 与游戏适配层

新增能力先确定领域模型与规则。凡是不需要 Minecraft 实例、原生物品/流体对象、游戏事件或界面绘制
就能表达和测试的规则，应放在 Core。具体整合包中的故障可以触发修复，但通用解决方案不能只留在
`neoforge-1.21.1` 中。

| 职责 | 位置 |
| --- | --- |
| 观测完整性、可见槽位覆盖、旧页保留、过期更新拒绝 | `core.snapshot.InventoryObservation`、`SnapshotMerger` |
| 物品内液体、独立储罐、数量/容量/时间、持久化字段协议 | `core.snapshot.FluidSnapshotMetadata`、`ItemFluidSnapshotMetadata` |
| 物品身份与液体身份、嵌套路径、世界/逻辑位置 | `core.model` |
| 容器分组、液体汇总、按根/路径筛选、地图停靠点、体积单位 | `core.view` |
| 已加载对象的读取、客户端同步状态、原生组件编解码 | 当前 Loader 的 `capture`、`mapping` |
| Sophisticated、TFC、Jade 等模组的可选 API/反射 | 当前 Loader 的 `compat` |
| 搜索框、切换按钮、列表组件、图标/纹理、地图绘制 | 当前 Loader 的 `client.gui` |
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

这次先下沉液体与分页观测链路。其他既有界面状态/视图仍可逐步整理；不为尚未实现的 Loader 建立
空模块，也不把原生模组读取代码移动到 Core。
