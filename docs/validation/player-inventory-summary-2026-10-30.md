# 玩家饰品背包、内容预览与导航摘要验证

日期：2026-10-30。系统：Windows 10 amd64；Java 21.0.12.1；Minecraft 1.21.1 / NeoForge 21.1.248。
开发版本：`0.2.3-beta.1-SNAPSHOT`。本次未升版、打标签或发布，也未替换用户实例中的模组。

## 结论与边界

F1 玩家库存入口、Shift 内容预览及原槽位点击已实现；I5 导航摘要、原因和证据详情已实现。
全量 `check`、联装探针、14 个原生界面夹具及库存本地构建通过。当前实例索引只读回放确实包含
饰品背包内物品，**尚未复现用户报告的完整漏统计触发过程**；B2 和 V1/V3/V4/V5/V7 保留未完成。
本记录不等于在完整整合包中进入世界验收，也不宣称任意模组/版本均兼容。

## 当前实例的只读核对

目标实例为 TerraFirma Rebirth 2。当时安装 LItemFinder 0.2.2，相关模组为：

| 模组 | 本地版本 |
| --- | --- |
| Curios | 9.5.1+1.21.1 |
| Sophisticated Backpacks | 3.25.74.2043 |
| Sophisticated Core | 1.4.81.2206 |
| TFC Sophisticated Backpacks Upgrade / tfcsbu | 0.3.1-1.21.1 |

对本地 Curios JAR 的 API 签名核对确认 `CuriosApi.getCuriosInventory(LivingEntity)`、
`ICuriosItemHandler.getCurios()`、`ICurioStacksHandler.getStacks()` 仍与读取桥匹配。
实例日志中未发现 LItemFinder 的 Curios/Sophisticated 读取失败提示；这不证明所有运行时刻都读取成功。

原索引通过 SQLite 只读连接查询，再备份到工作区忽略的 `build/player-index-review.db`；
用实际 Core 与 SQLite 实现回放副本，原实例数据库和存档未修改。结果：

- 玩家根快照共 45 槽；`accessorySlot.41 = curios/back:0`，另有 charm、head、linked_typewriter 槽。
- 第 41 槽的 `tfcsbu:black_steel_backpack` 子快照为 120 槽，67 条非空槽记录，物品数量合计 17,395。
- 玩家根进入实际索引后为 91 条扁平物品记录；Core 总览包含 `minecraft:flint = 4,465`、
  `tfcorewashing:pile_yellow_sand = 1,897`。

因此，本次证据不能支持“Curios 完全未读取”的判断，也不能据此排除界面刷新、同步时序或其他
触发过程。新构建尚未安装到此实例，B2 需对照总览、地图、预览及原槽位继续复测。

## 实现与回归覆盖

- 地图 2D/3D 的本地玩家指针可点击，侧栏有同一入口；精确匹配当前 UUID 的玩家根，空地图保留玩家维度。
- 内容页使用已采集布局和原槽号，包含盔甲、副手与已注册装备源/Curios 槽；可定位装备/饰品区域。
  点击带内容的绿色槽位逐层进入，不将汇总结果填回原槽位。
- Shift 提示使用 Core `ContainerPreview`，递归按精确 variant 汇总，保留完整数量；升级图标来自
  已同步的升级数据，单独展示，不加入库存。未同步、旧记录或暂不可读保留的数据有相应提示。
- 修复嵌套读取暂不可用时旧子树被移除的问题：Core 根据显式不可用标记，在相同槽位、相同载体或
  已确认的稳定身份下保留上次子快照和观测时间。确认空容器会替换，移除或更换身份不会继承旧内容。
  新观测会清理覆盖槽位的旧身份标记，防止残留信息影响后续判断。
- Curios 桥改为首次观察时解析可选 API；临时调用失败后延迟重试，避免一次错误永久停用。
- 导航桥传递原因、基础速度估时性质、来源、覆盖停靠点和检查时间；地图摘要、滚动详情和重算入口
  本地化展示。部分估时不外推全程，旧 provider 缺失的证据不编造，覆盖停靠点不解释为玩家已访问。

新增回归覆盖玩家→饰品→背包统计、大堆叠、原槽号、同身份暂不可读、确认空、更换/移除、
稳定背包 UUID 的组件变化、升级不计数、玩家点击、导航摘要/证据及过渡边界。

## 执行与结果

```powershell
.\gradlew.bat check :neoforge-1.21.1:verifyNavigationProbeClient
.\gradlew.bat :neoforge-1.21.1:verifyRouteUiProbeClient :neoforge-1.21.1:build
.\gradlew.bat check
```

最终均通过。最后一次 `check` 使用未变更任务的已有结果，并重新执行 Core 边界及两份安装 JAR 检查。
JUnit XML 汇总如下：

| 模块 | 测试 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| core | 69 | 0 | 0 | 0 |
| storage-sqlite | 4 | 0 | 0 | 0 |
| neoforge-1.21.1 | 153 | 0 | 0 | 0 |
| navigation-core | 22 | 0 | 0 | 0 |
| navigation-neoforge-1.21.1 | 17 | 0 | 0 | 0 |
| 合计 | **265** | **0** | **0** | **0** |

联装客户端日志包含 `NAVIGATION BRIDGE PROBE PASSED`、`COMPAT PROBE PASSED`、
`COMPAT PROBE MAIN MENU READY`、`COMPAT PROBE SHUTDOWN`；无世界请求按无世界原因降级，未生成虚假证据。

界面探针以 1920×1200 原生窗口渲染生产界面和字体，使用合成快照及路线：

| 夹具 | 数量与范围 | 核对 |
| --- | --- | --- |
| 地图导航摘要 | 4 张；zh_cn/en_us × GUI 3/5 倍 | 文本换行宽度、摘要区高度、侧栏裁剪、按钮可读 |
| 导航详情 | 4 张；zh_cn/en_us × GUI 5 倍的顶部/底部 | 键盘翻页、滚动位置、方式/换乘/维度和证据文本；合成船/传送门段不代表已实现这些行进方式 |
| 玩家原槽位、Shift 汇总、点击后子槽位 | 6 张；zh_cn/en_us × GUI 5 倍 | 原布局和空格、4,462/1,897 等完整预览数量、升级独立展示；实际调用鼠标点击后核对路径为第 41 槽 |

14 张截图均已检查，输出分别位于 `neoforge-1.21.1/build/route-ui-probe/` 和 `build/player-ui-probe/`。
探针检查预期截图数、成功标记、主菜单和正常退出，拒绝 ERROR/FATAL 或退出失败；未打开用户世界。
扩大玩家点击夹具时曾因测试代理没有分派 `fluidOverview` 默认方法失败，补齐代理默认方法调用后重跑通过，
该次失败不作为成功证据。探针源码独立于安装 JAR。

## 本地产物

| 产物（各模块 build/libs） | SHA-256 |
| --- | --- |
| `litemfinder-neoforge-1.21.1-0.2.3-beta.1-SNAPSHOT.jar` | `9a5b9d66b25dc0a75d27e929080145feed5766c28939c7d2cdf01adddf13a987` |
| `litemnavigation-neoforge-1.21.1-0.2.3-beta.1-SNAPSHOT.jar` | `bbc04b3af630d47ba300d53e276c99ddf45b3a1524a66287e7c81b73988fa2a5` |

库存 JAR 排除开发探针与可选导航实现；导航为独立可选安装。未生成发布包或 GitHub Release。

## 仍需验收

完整整合包需复测 B2 的触发过程、内容同步前后与缓存不可用恢复、换包/清空/重启，以及 2D/3D 玩家点击
和拖动。无原模组同步数据时不会主动请求或推测内容；只支持实际注册的装备来源，不覆盖任意界面模仿。
不推测 TFC 重量、大小或堆叠倍率。真实导航、摘要随世界变化及帧耗时仍归 V4/V5/V7。
