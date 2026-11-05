# 可选只读步行导航

当前属于 beta。`navigation-core` 提供纯 Java 碰撞步行与搜索；
`navigation-neoforge-1.21.1` 提供独立客户端 Mod（`litemnavigation`），读取已加载世界并接入库存地图。
库存安装 JAR 不包含导航。独立和联装启动已有 Windows 成品探针，真实世界路线、视觉和性能仍由
[TODO 的 V4/V5/V7](development-status.md) 验收；交通、跨维度和准备任务仅有算法原型。

## 方案评估

本次核对本地 Baritone `origin/1.21.1`，提交
`11ea66cd11c3bdf2eb7a8dfdbdec97b964fda6c7`。下列链接固定到该提交，依据是对应源码，
不推断其他分支或发行版本的行为。

| 参考源码 | 观察与本项目的选择 |
| --- | --- |
| [IPathFinder](https://github.com/cabaletta/baritone/blob/11ea66cd11c3bdf2eb7a8dfdbdec97b964fda6c7/src/api/java/baritone/api/pathing/calc/IPathFinder.java) | 路径计算与执行可以分开。采用只读请求/结果接口，不调用自动行走或输入控制 |
| [CalculationContext](https://github.com/cabaletta/baritone/blob/11ea66cd11c3bdf2eb7a8dfdbdec97b964fda6c7/src/main/java/baritone/pathing/movement/CalculationContext.java) | 代价依赖世界、玩家、工具、破坏/放置及其他移动能力；直接接入需要更多上下文和动作约束。首批采用独立碰撞步行模型，不引入 Baritone 运行依赖 |
| [MovementHelper](https://github.com/cabaletta/baritone/blob/11ea66cd11c3bdf2eb7a8dfdbdec97b964fda6c7/src/main/java/baritone/pathing/movement/MovementHelper.java) | 支撑面、身体通行和危险检查需要分别处理。读取原生碰撞形状、路径危险类型和表面因素，再转换成不含游戏对象的数值数据 |
| [GoalNear](https://github.com/cabaletta/baritone/blob/11ea66cd11c3bdf2eb7a8dfdbdec97b964fda6c7/src/api/java/baritone/api/pathing/goals/GoalNear.java) | 目标可以是一组附近位置。普通航点使用站立位置；容器使用有视线的可交互邻接位置，避免把实体容器内部当作站立目标 |
| [AStarPathFinder](https://github.com/cabaletta/baritone/blob/11ea66cd11c3bdf2eb7a8dfdbdec97b964fda6c7/src/main/java/baritone/pathing/calc/AStarPathFinder.java) | 有计算时限和中间候选路径。首批在数值图上有界搜索，未知区域/预算中断返回已验证部分或未知状态，不将部分结果标成完整路线 |

代码自行实现。路径压缩只移除同方向共线点，保留拐角、反向、高差和世界边界；不通过跨墙直线
“平滑”路径，也不宣称压缩方式来自上述项目。当前没有采用 Baritone 的完整移动模型。

## 依赖与接口

- 独立导航 JAR 内嵌 `core`、`navigation-core`；不内嵌库存 Loader 或 SQLite。
- 库存 Mod 只依赖自己的 `NavigationGateway` 契约，通过服务发现可选桥接。导航桥接编译时引用
  该契约；无库存 Mod 时不加载桥接类。联装时通过 Jar-in-Jar 共享 Core。
- `WorldScopeId` 位于 Core，库存与导航复用同一世界/服务器归一化和哈希协议，不持久化原始地址或路径。
- `LItemNavigationClient.service().route(origin, stops)` 接受普通站立航点；
  `routeToBlocks(origin, stops)` 接受容器方块目标。两者都返回 `CompletionStage<LocalNavigation.Result>`。
  输入使用 `Waypoint` 的 scope、维度和有限 double 坐标，支持半砖高度；scope 必须与当前游戏一致。
- `Result` 包含状态、已验证路径段、原因、估时、覆盖停靠点、检查时间和 revision。库存桥接投影为
  Core `NavigationSummary/NavigationReport`，地图摘要和可滚动详情展示目的地、方式、完整度、缺口、
  下一步及可用证据。覆盖停靠点表示计算已验证的范围，不表示玩家已经访问。
- 路径段可携带各段估时，基础速度估算与实测明确区分；部分结果不外推全程，旧 provider 未提供的
  时间、来源和置信度保持未知。既有 `Overlay` 三参和 `Segment` 两参构造仍可使用，不伪造证据。
- 后来的请求替换旧请求。世界或移动能力改变使请求和已发布路线失效；地图观察 revision 变化后
  清除旧路线并重新请求。关闭地图或更换世界后拒绝晚到结果。

独立客户端命令：

```text
/litemnav to <x> <y> <z>
```

坐标表示当前位置所在维度的站立航点。命令给出中英文状态、估时和原因，不控制玩家移动；
当前没有独立导航地图或跟随 HUD。库存地图仅在另外安装库存 Mod 并发现桥接时接收路线。

## 观测与限制

原生读取在客户端线程分帧执行，不创建或加载区块。未知区域、无法读取的形状及依赖未知区块的形状
不当作空气。后台线程只接收数值图快照；返回结果先复核碰撞证据，再发布，并继续分批检查变化。

默认单次观测限制为 4,096 个展开节点、32,768 个碰撞单元、200 个采样 tick，起点水平 96 格及
垂直 32 格范围；每 tick 最多展开 8 个节点并使用约 2 ms 的软时间片。发布前复核另有 100 tick
上限。单次原生 getter 可能超过时间片，这不是硬帧耗时保证，实际延迟和帧率仍需测量。
预算结束后，仅返回完成复核的路径；复核超时可返回没有路径的未知状态。

首批仅检查四向步行、有限跳跃/台阶和最多 1.25 格落差，保留真实碰撞面的分数高度。
使用当前玩家尺寸、台阶高度、跳跃和重力属性，以及方块速度/跳跃因素；危险路径类型和流体体积参与检查。
正在飞行、入水或岩浆中的玩家不使用本步行服务。没有游泳、攀爬、开门、破坏、放置、载具、
传送门或鞘翅动作；也没有轨迹持久化、设施发现或速度学习。

形状、表面属性和原生危险类型是通用适配入口，但不能覆盖所有模组自定义运动逻辑、动态实体障碍或
特殊碰撞上下文。相关模组仍需针对性验收，不能将本实现描述为任意整合包的通行保证。
步行时间使用固定基础速度与加权移动代价估算，尚未按装备、药水、服务器延迟或真实轨迹校准；
多停靠点复用既有停靠顺序，不保证按实际通行时间求得全局最优访问顺序。

## 构建与验证

```powershell
.\gradlew.bat check
.\gradlew.bat :navigation-neoforge-1.21.1:build
.\gradlew.bat :navigation-neoforge-1.21.1:verifyStandaloneClient
.\gradlew.bat :neoforge-1.21.1:verifyPluginProbeClient
.\gradlew.bat :neoforge-1.21.1:verifyNavigationProbeClient
.\gradlew.bat :neoforge-1.21.1:verifyRouteUiProbeClient
```

导航安装 JAR 输出到 `navigation-neoforge-1.21.1/build/libs/`，不要安装 `-sources.jar`。
交互式独立成品客户端使用 `:navigation-neoforge-1.21.1:runStandaloneClient`。
三个探针分别检查只装导航、只装库存加开发探针、库存与导航联装加开发探针，均使用各自隔离目录，
只关闭隔离目录的首次辅助功能引导，主菜单就绪后正常退出，不打开用户存档。

启动探针证明类加载、服务发现、无世界请求降级和正常退出；有原生方块状态的模拟测试证明相应逻辑，
两者均不替代真实世界路线验收。结果及产物见[本批验证记录](validation/navigation-runtime-2026-10-28.md)。

界面探针额外在原生客户端中渲染中英文、GUI 3/5 倍的地图摘要及滚动详情，并验证换行宽度；
另包含玩家饰品/背包的 6 个界面夹具。它使用合成快照与路线，不打开用户存档；
本批结果见[2026-10-30 验证记录](validation/player-inventory-summary-2026-10-30.md)。
