# 2026-10-28 TODO 再盘点

此页保留盘点当时的失败结果；后续 B1 修复和最新验证见[导航运行记录](navigation-runtime-2026-10-28.md)。

## 范围

依据当前工作区（包含未提交改动）、构建脚本、现有测试报告和客户端日志整理待办。
本次执行日常检查并核对文档，未修改功能代码、启动图形客户端或执行发布。

环境：Windows / Java 21；目标 Minecraft 1.21.1 / NeoForge 21.1.248；开发版本保持
`0.2.3-beta.1-SNAPSHOT`。状态入口见[开发状态与 TODO](../development-status.md)。

## 当前检查结果

执行：

```powershell
.\gradlew.bat check
```

**结果失败**，失败任务为 `:navigation-neoforge-1.21.1:verifyStandaloneJar`：

```text
Jar-in-Jar metadata references missing entry:
META-INF/jarjar/dev.litemfinder.navigation-core-0.2.3-beta.1-SNAPSHOT.jar
```

直接检查当前导航 JAR，实际依赖条目为
`META-INF/jarjar/navigation-dev.litemfinder.core-0.2.3-beta.1-SNAPSHOT.jar`，
与 `META-INF/jarjar/metadata.json` 的路径不一致。构建脚本已有重命名修正尝试和路径校验，
但本次检查仍失败，不能将其视为修复完成。

核对的产物：

```text
navigation-neoforge-1.21.1/build/libs/litemnavigation-neoforge-1.21.1-0.2.3-beta.1-SNAPSHOT.jar
SHA-256: 6362a84b4c3ff36ba5f819cdd29d2da5714c1135d095cc2d29cdae2a79a00003
```

`navigation-neoforge-1.21.1/run-standalone-probe/logs/latest.log` 中现有 23:06 启动记录
同样报内嵌 `navigation-core` 加载失败。本次未重新执行启动探针，也没有导航联装成功的证据。

Core 导入边界和库存安装 JAR 校验任务本次通过。各模块测试任务均复用已有输出
（`UP-TO-DATE`），读取 `build/test-results/test/TEST-*.xml` 汇总如下：

| 模块 | 测试 | 失败 | 错误 | 跳过 |
| --- | --- | --- | --- | --- |
| `core` | 59 | 0 | 0 | 0 |
| `storage-sqlite` | 4 | 0 | 0 | 0 |
| `neoforge-1.21.1` | 142 | 0 | 0 | 0 |
| `navigation-core` | 22 | 0 | 0 | 0 |
| `navigation-neoforge-1.21.1` | 11 | 0 | 0 | 0 |
| 合计 | 238 | 0 | 0 | 0 |

这些报告证明已有自动化用例的结果，不代表本次重新运行全部用例、导航成品可启动或游戏内验收完成。
前一批 216 项及库存/兼容插件探针的成功结论仅适用于[其记录范围](map-transition-plugin-runtime-2026-10-28.md)。

## TODO 调整依据

- I1/I2/C1 保留“实现与自动化完成”，对应游戏内验收仍开放。
- I3/I4 从“尚未接入”改为“已有首批实现，待运行与边界验证收尾”；
  依据为独立 Mod 入口、只读航点服务、原生观测适配、可选库存桥接及对应测试。
- 新增 B1，优先修复导航成品的清单/条目不一致，再恢复全量检查和独立启动。
- 明确独立导航、库存单独安装、两者联装是三条不同运行链路；兼容插件探针不证明导航桥接成功。
- 新增 V7，跟踪真实世界中的步行路线、变化失效及延迟；地图显示和性能继续由 V4/V5 跟踪。
- I5 记录已有命令文本与桥接信息缺口；交通、轨迹持久化、速度校准和共享预留仍属后续任务。
