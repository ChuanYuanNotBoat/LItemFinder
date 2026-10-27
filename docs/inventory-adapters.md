# 客户端库存识别接口

原生识别接口与可选模组读取位于 `neoforge-1.21.1`；观测覆盖、合并、液体记录/汇总、分组与
位置投影由 Core 共享实现，SQLite 不依赖模组 API。所有活物品读取发生在客户端线程，完成
映射后才向后台提交不可变快照。当前编译基线是 NeoForge 21.1.248。职责划分见
[Core 与适配层边界](core-adapter-boundary.md)。

## 随身扩展槽位

实现 `EquipmentInventorySource` 并通过 `EquipmentInventorySources.register(source)` 注册。

- `id()` 必须唯一；来源 ID 会作为槽位标识的前缀。
- `read(wearer)` 返回 `Optional<List<AccessorySlot>>`，每项包含槽位类型、该类型内的编号和物品。
- 返回 `Optional.empty()` 表示暂时无法读取，保留该来源最后一次观测；返回空列表表示当前没有槽位，
  应清除旧内容。离开世界或更换玩家会清除缓存。
- 来源应包括真实空槽，避免背饰卸下后旧内容残留；不得同时提供实际物品和它的显示副本。
- Curios 默认适配读取 `getCuriosInventory → getCurios → getStacks`，不读取 cosmetic 槽。

扩展槽追加在原版物品栏之后，作为同一个玩家根快照一起替换。换装、数量变化、布局变化和已同步的
嵌套背包变化都参与防抖指纹；物品不会因在原版栏和背饰栏间移动而留下两个长期根记录。

## 容器菜单

实现 `StorageMenuAdapter`，通过 `StorageMenuAdapters.register(adapter)` 注册。返回
`MenuStorage` 描述真实 backing storage、可选的方块实体和已确认的方块位置。

```java
StorageMenuAdapters.register(menu -> {
    if (!(menu instanceof MyStorageMenu storage)) {
        return Optional.empty();
    }
    var owner = storage.getBlockEntity();
    return Optional.of(new MenuStorage(
            List.of(owner.getInputInventory(), owner.getOutputInventory()),
            Optional.of(owner), owner.getBlockPos()));
});
```

`MyStorageMenu` 为外部模组自行提供的菜单类型。支持 `Container` 和 `IItemHandler`，多 handler
按列表顺序分配互不冲突的槽位编号；同一 backing slot 的重复视图只计一次。注册返回的
`AutoCloseable` 可用于卸除适配器。识别器只能返回实际物品库存，不能把过滤条件 handler 当作物品。

默认识别器无需逐个列举模组菜单名，支持以下公开结构：

- `getBlockEntity()` 或 `getTileEntity()` 返回方块实体，本体实现 `Container`，或提供
  `getInventory()`、`getItemHandler()`、`getInventoryHandler()`。
- `getStorageWrapper().getInventoryHandler()` 提供库存，且 `getBlockPosition()` 提供位置。

只采集菜单里直接指向这些存储的槽位，玩家物品栏、其他临时 handler、配方结果容器及重复槽位
不会混入。TFC 4.2.9 的 `BlockEntityContainer → getBlockEntity → getInventory` 结构已经通过
实例 JAR 检查，覆盖大缸、木桶及使用相同结构的炉具菜单。缸内流体不属于物品索引。

未提供这些结构的模组可用专门适配器接入；未识别菜单仍报告 unsupported，不按槽位数量猜测。
手持精妙背包不另建根容器，以免与玩家根里的嵌套内容重复。

`MenuStorage.position` 为可选值。已核对沉浸工程 12.4.2 的 `CrateMenu`：客户端使用私有
`container`（27 格 `SimpleContainer`），没有方块 owner getter。可选适配器只读取该已知菜单
的实际 backing container，不猜测最后 36 格；来源位置仍由近期实际交互确认，超时降级为会话
身份，不把缺失位置写成零坐标。自动回归覆盖截图中的红石 2 个，以及玩家/无关槽位排除。

## 液体库存

无菜单采集通过 `FluidInventoryAdapters.registerBlock(BlockFluidInventoryAdapter)` 注册。返回的
`BlockFluidInventory` 必须提供和真实菜单一致的类型与规范主块坐标，以及已确认同步的储罐。
每 5 tick 仅读取当前准星目标；不遍历区块、不主动获取网络数据。液体独立更新，不覆盖未观测的物品槽。
返回 `Optional.empty()` 或抛出读取异常时不提交更新。只有明确的空储罐读数表示排空。

默认接入 TFC 大桶；沉浸工程焦炉自身不向客户端同步其储罐，因此通过可选的 Jade 桥仅读取当前
同一世界、同一目标且 `verifyData` 通过的已有服务端数据，归并到已加载的多方块主块。没有 Jade
或数据不可用时保留旧记录，不将客户端的默认空罐写入索引。

`itemFluids.*` 与方块 `fluids.*` 分开保存。物品流体 capability 仅读取副本，叠放数量按件数乘算；
精妙背包读取已经同步的 upgradeInventory，指纹包含缓存内流体变化。储罐升级拆下后从自身内容
组件读取；已装入时按原模组行数/堆叠配置求容量，拆下时容量标为未知，不假设容量为零。
数据保留根容器和嵌套槽位路径，液体直接接入容器地图的显示与导航，逻辑容器不会产生世界停靠点。

`FluidInventoryAdapter` 通过 `FluidInventoryAdapters.register(adapter)` 注册，只读取已经同步
到客户端的实际 `IFluidHandler` 或等价数据。`FluidInventory` 由 `Tank(index, fluid, amount, capacity)`
组成，体积和容量均以 long mB 保存；模板 `FluidStack` 规范为 1 mB，仅携带液体及组件身份。
零体积为已确认排空，`Optional.empty()` 为不可用，两者严格区分。流体池、掉落物、任意未同步
capability 和虚拟过滤器不是实际储罐，不自动扫描或采集。

默认入口为 `StorageMenuAdapters` 确认的菜单 owner，只接受已核对的 `tfc:barrel` 类型，读取其
`getInventory()` 返回的 `BarrelInventory / DelegateFluidHandler`。其序列化数据包含 tank；通过
已有方块/菜单同步获取，不调用 fill、drain，不改变密封状态或主动发送请求。
液体数量或类型/组件变化参与打开菜单的指纹，关闭时也作最后采集；关闭后的内容属于历史观测，
没有后台读取未加载储罐。

Loader 的 `FluidInventoryMetadata` 只负责原生流体映射；Core 的 `FluidSnapshotMetadata` 将独立的
版本化储罐记录存入既有 metadata：液体 ID、组件指纹、可重建
显示模板、long mB 体积/容量和独立观测时间。沿用 SQLite schema v1，不制造代表液体的物品栈，
不改变 Core 物品搜索、分类和获取计划。暂时不可读取时保留上次液体数据及其原时间；已确认排空
则替换旧数量。储罐类型改变时不会继承旧液体。

总览和内容页在同一页面切换物品/液体内容，列表共用行样式，液体图标读取原模组纹理和 tint。
液体的数量、容量在行内展开，地图直接显示液体内容，不经过独立来源页。显示单位按桶数缩写：
1000 mB = 1 B；1000 B = 1 kB；1000000 B = 1 MB；后续为 GB、TB、PB。
`mB`（毫桶）与 `MB`（百万桶）大小写不同，缩写最多保留两位小数，悬停保留精确整数 mB。

## 存储终端与自动网络分组

`StorageTerminalAdapter` 通过 `TerminalMenuAdapters.register(adapter)` 注册，`supports(menu)`
单独声明接管；即使 `read(menu)` 暂时返回 empty，原始菜单槽位也不会被普通采集器当作新箱子。
不使用模组名称、附近箱子或界面行数猜测网络归属。当前仓库提供接口和模拟测试，尚无内置
AE2/RS/Tom's 等具体终端适配，不能把此通用入口当作这些模组已通过兼容验收。

`StorageTerminalView(networkId, name, viewKey, windows)` 必须由模组适配器提供已确认的稳定网络
标识；viewKey 包含当前页、筛选和排序等观察范围。每个 `Window` 描述一个真实底层容器：

- 使用与该容器直接打开时**完全一致**的根身份和原始槽位编号，必须是 EXACT 的世界容器身份。
  不能把汇总面板、虚拟结果、幽灵槽或无法分解归属的网络总量伪装成另一份实体库存。
- slotCount 为底层容量，slots 为本次确实看见的槽位，包括已确认的空槽。只替换这些键，
  不把未观察槽位当作空；过滤/排序/翻页不会删除其他页的内容。
- complete 只有在整个底层库存都已确认（包括真实空槽）时设为 true，此时才执行完整替换。
  局部页面给出的更小容量提示不能截断先前未观察的范围。空结果页也不能清空其他页。
- 可选 fluids 以真实 tank index 更新可见储罐；显式零体积清空相应槽，未给出的储罐保留。
  complete 的液体数据必须同样覆盖整个底层液体库存；完全未提供液体表示暂时无法读取。
- 数据必须是已经收到的客户端信息，不自动点击、翻页、请求同步或枚举未知底层方块。

终端每 5 tick 只读取当前开着的界面，内容或观察范围变化后提交。部分页使用有界独立串行队列，
不能用同根 latest-value 合并丢掉前一页。后台将 visible slots 合并进原根快照，不再建立终端副本，
因此直接访问与终端访问同一库存不会增加一份数量。过期更新被拒绝，关闭重开后保留已观察范围。
为不宣称不可见数据是新鲜的，保留旧快照时间，单独存本次局部观测时间；内容页标记部分更新，
从未观察的槽显示 `?`，与已确认空槽区别。

同网络的已确认真实根写入按 scope 隔离的 `storageNetworkId`、名称和独立关系观测时间，
`ContainerOverview.groups()` 自动生成分组，地图提供分组筛选。直接重新打开容器保留最后确认的
网络归属，但不会把关系观测时间更新成新的；重新观察另一网络可改变分组。没有提供者关系证据的
箱子不会只因相邻、材质相同或物品相似而自动合并。分组不复制条目，也不改变物品总数。

模拟终端回归覆盖连续排队翻页、筛选无结果、可见空槽、容量提示变化、完整替换、拒绝过期消息、
重启后恢复、液体分页、同网络分组/不同网络分隔，以及加载期间禁止原始槽位采集。

## 内容页的真实槽位布局

`InventoryLayout` 用 `Position(slot, x, y)` 描述物品槽坐标；slot 始终是真实 backing slot 编号，
不能按菜单列表顺序重编号。菜单采集自动保存已选槽位的 `Slot.x/y`（包括空槽），排除玩家或
临时槽位；饰品扩展槽在玩家原槽位区域下方追加。布局变化也参与菜单变更指纹。

`InventoryLayoutMetadata` 将布局版本、位置条目数和坐标写入已有容器 metadata，不改 Core 或
数据库 schema。内容页按坐标呈现并共用渲染、滚动、点击的几何计算；宽布局缩小以适应窗口，
高布局支持滚动。历史记录、未知版本、重叠或损坏的布局回退为规则网格，重新打开菜单即可更新。
只恢复槽位位置，不复制原模组背景纹理和装饰性的配方/燃料提示图案。

无菜单库存可用 `BlockInventory.withLayout(layout)` 提供自己的布局；未提供时使用网格。
布局属于变更比较的一部分，不能引用超过库存容量的槽位。普通抽屉按单列 1/2 格或 2×2 格显示。

## 不经过菜单的方块库存

实现 `BlockInventoryAdapter` 并通过 `BlockInventoryAdapters.register(adapter)` 注册。它与菜单识别
并列，仅读取已加载方块实体中**已经同步到客户端的真实库存**。不能只因某个客户端 capability
存在，就将其中尚未同步的默认空值当作空库存。

```java
BlockInventoryAdapters.register(owner -> {
    if (!(owner instanceof MySyncedStorage storage)) return Optional.empty();
    return Optional.of(BlockInventory.fromHandler(
            "my_mod:physical_storage", storage.getInventory(), storage.getPhysicalSlotCount()));
});
```

`MySyncedStorage` 为示例类型。`BlockInventory` 复制槽位物品并保留真实数量，最多 4096 槽。
`Optional.empty()` 表示不支持或暂时不可读取，保留旧记录；成功返回含空物品的槽位列表才表示
库存清空。只返回物理存储槽，过滤器、升级槽、虚空入口、其他库存的代理视图及换算视图不能混入。
同一物理库存应选择菜单或方块采集其中一种入口，避免以不同根身份重复提供。

客户端每 5 tick 检查准星目标，左右键交互也会记录目标；后台最多保留 64 个已观察方块，
每 tick 至多读取一个目标和一个轮询目标，不枚举区块。读取前检查区块已经加载，卸载后停止
轮询并保留历史记录。切换世界或维度、退出游戏时清除观察状态；内容不变不重复写盘。

默认 `FunctionalStorageInventoryReader` 已按实例 Functional Storage 1.5.4 和 Titanium 4.0.45
的实际 JAR 核对：普通 `DrawerTile` 和继承它的框架抽屉，通过 `getStorage()` 读取
`IItemHandler`，通过 `getDrawerType().getSlots()` 确认 1/2/4 个物品槽。
`getStorageSlotAmount()` 在该版本固定为 4，描述的是升级槽数，不能用于物品布局。
排除 handler 的额外虚空槽，锁定但已清空的物品样板不会入库。handler 的 `onChange` 会触发
方块更新，Titanium 更新包包含已保存库存；采集器只读取收到的数据，不主动请求同步。

控制器及扩展、共享末影抽屉、压缩抽屉没有被当作独立普通库存接入，避免网络代理、共享频率、
物品换算导致重复计数。新增同类模组可以注册此接口，而无需改 Core。

`FunctionalStorageFluidReader` 通过通用方块流体适配接口接入普通及框架 `FluidDrawerTile`。
已核对 Functional Storage 1.5.4 / Titanium 4.0.45：`getFluidHandler()` 返回真实 `BigFluidHandler`，
罐数来自 `getDrawerType().getSlots()` 并与 handler 的 1/2/4 个罐一致，数量/容量直接读取原模组。
`fluidHandler` 带 Titanium `@Save`，初始更新标签包含保存数据；内容与容量变化使用 `syncObject`
同步到客户端。采集仅读取已收到的数据，不主动发包、填充或排液。

流体抽屉不产生物品槽，过滤样板和升级槽不计库存；真实清空会提交零体积，读取失败不提交更新。
创造型 `CustomFluidTank.getFluidInTank` 会将活跃栈数量改为 `MAX_VALUE`，因此读取内容前先检查
`isDrawerCreative()` 并跳过无限视图。控制器/代理对象不在正向类型契约中，罐数不一致时也不猜测。
方块身份采用已有无菜单库存身份，液体记录、汇总、显示、地图与时间保留仍共用 Core 实现。

TFC 4.2.9 的展示式库存通过同一接口接入，并在调用库存 getter **之前**检查方块实体注册 ID：
只接受 `tfc:tool_rack`、`tfc:placed_item`，然后调用 `InventoryBlockEntity.getInventory()`。
工具架四格按 2×2 显示；放置的小物品保留四格，大物品由 `holdingLargeItem()` 确认，仅显示
其真实槽 0。`InventoryBlockEntity.saveAdditional` 保存库存，`TFCBlockEntity.getUpdateTag`
包含这些数据，采集器读取已有同步，不请求额外数据。

`GroundcoverBlock`、`LooseRockBlock` 等自然地表散落物不在类型名单中，不能根据名称、外观、
掉落表、pick/clone 物品或“同样有 getInventory”判断成储物。掉落物实体也不会采集。
同样排除继承 PlacedItem 的其他机器（例如 pit kiln），不将它们未经核对的槽位猜测为摆放物品。
使用与自然散落物相同类型且没有可验证来源的手放树枝/石块也不入库；背包内已收集物品照常统计。
回归测试包含反复观察不受支持类型后数据库仍为空的验证。

## 精妙背包版本兼容

实例版本 3.25.74.2043 没有 `BackpackStorage.getBackpackContents(UUID)`。适配器在该版本读取
已同步的 `backpackContents` 缓存；新版本优先使用公开读取方法。不调用会创建数据的
`getOrCreateBackpackContents`，也不主动发送同步请求。

识别物品时使用可选的 `BackpackItem` 实际类型（包含子类），不按模组命名空间筛选。
实例 `tfcsbu` 0.3.1 的锻铁及其他金属背包直接注册 `BackpackItem`，因此共用同一 UUID 和同步缓存
读取链路。只携带 `storage_uuid` 或相似 custom data 的普通物品不会因此被当作背包。

自动回归覆盖旧缓存接口、背饰内背包的嵌套索引/更新/移除、槽位变化、来源暂时不可用、
多 handler 编号、重复视图、临时槽位排除和菜单来源身份。完整整合包内的交互效果仍需实测。
