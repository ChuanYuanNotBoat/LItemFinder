# 2026-10-28 地图路线过渡与跨 JAR 插件验证

## 环境与产物

- Windows 10 amd64 / Temurin 21.0.12.1 / Minecraft 1.21.1 / NeoForge 21.1.248。
- 开发版本保持 `0.2.3-beta.1-SNAPSHOT`，未发布或更改用户整合包。
- 安装 JAR：`neoforge-1.21.1/build/libs/litemfinder-neoforge-1.21.1-0.2.3-beta.1-SNAPSHOT.jar`。
- 安装 JAR SHA-256：`ddcc2b6f44ff78724217014d2d4ff91dfea4867e4a287e29778eb9a301ede9b8`。
- 独立开发探针：`neoforge-1.21.1/build/compat-probe/litemfinder-compat-probe.jar`。
- 探针 SHA-256：`f4b3d37056d7c31d5ff96d6faea8c1f9c1c121e558ab73e9d0361c6bfad616ef`。
- 安装 JAR 校验确认不包含探针类；探针不输出到安装包目录。

## 执行与结果

最终执行：

```powershell
.\gradlew.bat check :neoforge-1.21.1:verifyPluginProbeClient
```

结果通过。JUnit 共 **216 项，0 失败、0 错误、0 跳过**：
Core 59、SQLite 4、NeoForge 141、navigation-core 12。本批新增 22 项回归。
Core 导入边界及安装 JAR 依赖/许可证/探针排除检查通过；部分任务复用已有输出。

## I1 路线过渡

| 场景 | 实现与自动化证据 |
| --- | --- |
| 起点与局部前缀变化 | Core `RouteTransition` 在 240 ms 内平滑插值；只允许共同锚点前、起点附近 8 格内的短前缀变化，远处锚点保持不动 |
| 路线形状/停靠顺序/顶点数或来源类型变化 | 旧折线淡出后新折线淡入，不把不相干的中间点直接拉伸；示意线和导航线保持各自颜色与语义 |
| 频繁更新 | 从已经显示的位置/透明度继续；进入淡入阶段的局部起点漂移不重启动画；只保留当前、前一份几何，不积累叠加层 |
| 世界/起始维度变化或清空需求 | 立即清除旧世界几何；世界坐标来源不跨 scope 连线 |
| 玩家移动 | 示意线首点使用原版渲染插值；跳跃式远距离变化保留旧路径位置并淡出；provider 路径不被强行接到未验证的玩家位置 |
| 2D/3D 接线 | 共用同一帧的 Core 路线和透明度；原有屏幕裁剪与世界/维度筛选保留，3D 也可显示停靠示意线 |
| 可选 provider 重算 | 同一计划重算时保留旧路线并提示正在更新；失败或无有效路径时回退示意线；请求代次、Screen 移除及世界实例变化使晚到结果失效 |
| 非法 provider 数据 | 跨世界、起始维度错误、未知/不可达状态不会发布为实际导航路径；可选 API 链接错误不会导致页面崩溃 |

Core 13 项新增测试验证插值、淡出淡入、频繁重定向、上下文隔离和共享几何；
Loader 4 项投影测试与 5 项菜单/异步测试验证实际接线。初次适配层测试发现测试字体替身缺少
字符串裁剪返回值，已修正替身，并确保构造失败会释放静态替身；最终全量测试通过。

以上证明实现与自动化行为，未进入真实世界录制地图动画，不代替 V4/V5 的视觉、操作和帧率验收。

## 跨 JAR 插件与成品运行

`verifyPluginProbeClient` 在 `neoforge-1.21.1/run-plugin-probe/` 中运行真实客户端，禁用源码
Mod 加载，仅从该目录的 `mods` 加载安装 JAR 和独立探针 addon。只在该隔离目录关闭首次运行的
辅助功能引导，避免等待人工输入；不修改用户游戏配置、存档或服务器。

探针包含四份插件定义，并使用实际清单发现和类加载器，未注入入口工厂：

- 初始化中途故意失败的插件先登记 reader，再抛异常；登记被回滚。
- 正常插件随后复用同一 reader ID，以 addon 中的方块实体子类匹配，实际读取 7 个钻石。
- 缺少依赖和 Minecraft 版本不符的两份定义被跳过，其入口类未初始化。
- 正常插件验证读取不可用、读取异常及恢复，确认空库存仍可成功读取。
- 实际读取经 `MenuSnapshotMapper` 转为 Core 快照，写 SQLite，关闭/重开恢复，进入 Core 索引与
  精确搜索后仍为 7 个钻石；最后清除探针自身记录。

最终运行日志（`run-plugin-probe/logs/latest.log`，后续运行会覆盖）包含：

```text
Inventory plugins: 1 loaded, 2 skipped, 1 failed
COMPAT PROBE PASSED: addon discovery, version gating, rollback, reader, mapping, SQLite, Core search
COMPAT PROBE MAIN MENU READY: title screen rendered after resource loading
COMPAT PROBE SHUTDOWN: normal client lifecycle
```

其中一次插件失败和一次 reader 警告均由探针故意触发，是隔离与回滚测试的预期结果。
主菜单的已渲染状态来自 `RenderFrameEvent.Post`、`TitleScreen` 及资源 overlay 已消失的检查，
不是截图视觉审查。探针通过 Minecraft 的正常停止流程退出，验证任务确认正常退出事件及无
`Shutdown failure!`/`FATAL`。SQLite native probe 同样通过。

首次探针在资源加载中提前退出，曾触发原版纹理清理的并发修改异常；修复为等待主菜单资源加载
与绘制完成再退出，最终运行没有该异常。新的验证任务会拒绝缺少通过/正常退出标记或出现关闭错误
的结果，不能仅依赖游戏进程返回码。

## 剩余验证

- I1 实现与自动化完成，整合包中的动画、不同 GUI 缩放、视觉与性能仍由 V4/V5 跟踪。
- 此探针证明独立 addon 的接口/类加载链路，不等于 Jade、JEI、WTHIT 或任意实际库存模组已兼容。
- V6 已补当前 Windows 成品启动、跨 JAR 插件、数据库关闭/重开和正常退出的证据；真实世界
  scope 切换、游戏进程重启恢复与 Linux 成品客户端仍未验收。
- 真实世界导航、交通与共享预留、具体网络终端适配等仍未完成；整体 TODO 目标保持进行中。
