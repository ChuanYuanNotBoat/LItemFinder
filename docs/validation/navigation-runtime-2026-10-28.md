# 2026-10-28 独立导航运行与边界回归

## 环境与产物

- Windows 10 amd64 / Temurin 21.0.12.1 / Minecraft 1.21.1 / NeoForge 21.1.248。
- 版本保持 `0.2.3-beta.1-SNAPSHOT`；仅本地构建与隔离运行，没有发布或修改用户整合包。
- 库存 JAR：`neoforge-1.21.1/build/libs/litemfinder-neoforge-1.21.1-0.2.3-beta.1-SNAPSHOT.jar`。
  SHA-256：`e35ec6a52d568b81b29aa7711f01a7345f7f99b6f9c70b80efaeb4d02f12279e`。
- 导航 JAR：`navigation-neoforge-1.21.1/build/libs/litemnavigation-neoforge-1.21.1-0.2.3-beta.1-SNAPSHOT.jar`。
  SHA-256：`534279fe6a86500ab08b4bee67264ea905fe8e3035a94a99da6ae5c1974750ae`。
- 开发探针：`neoforge-1.21.1/build/compat-probe/litemfinder-compat-probe.jar`。
  SHA-256：`947b041ecf7b358020dcc6bf822945a24f832f780f79ea492760030015fe525f`；不进入安装 JAR。

## B1 修复与全量检查

此前导航成品在[盘点](todo-review-2026-10-28.md)中失败：ModDevGradle 2.0.147 的 Jar-in-Jar
复制重命名将 `core` 文件名匹配到 `navigation-core` 后缀，生成 `navigation-dev.litemfinder.core-…jar`，
而清单要求 `dev.litemfinder.navigation-core-…jar`。

修复在最终 JAR 组装时精确匹配已生成的错误文件名，再改为清单规定的路径；没有修改依赖身份或
只放宽校验。重新构建 JAR 后验证清单每个路径都存在，恰好嵌入 Core/navigation-core，包含许可证和
第三方说明。库存 JAR 校验新增导航排除要求。首次修复验证显式重建 JAR，避免沿用旧产物。

最终执行成功：

```powershell
.\gradlew.bat check :navigation-neoforge-1.21.1:verifyStandaloneClient
.\gradlew.bat :neoforge-1.21.1:verifyNavigationProbeClient
```

库存单独安装还执行了 `:neoforge-1.21.1:verifyPluginProbeClient`，探针及正常退出校验通过。
同次运行的新增预算测试首次失败，随后将采样预算作为可缩短的构造参数，以有限的已观测前缀测试
采样中断，避免把大范围复核超时误当作必然应有部分路线；再次执行导航测试及最终全量检查通过。
生产默认仍为 200 个采样 tick 和 100 个复核 tick，复核超时返回未知状态，不发布未复核路径。

最终 JUnit 报告：

| 模块 | 测试数 |
| --- | --- |
| `core` | 59 |
| `storage-sqlite` | 4 |
| `neoforge-1.21.1` | 142 |
| `navigation-core` | 22 |
| `navigation-neoforge-1.21.1` | 17 |
| 合计 | 244 |

0 失败、0 错误、0 跳过。导航 Loader 的 17 项在最终全量检查中执行，其余测试复用已有输出；
Core 导入边界及两种安装 JAR 校验本次通过。

## 三种安装组合的实际客户端证据

| 组合 / 隔离目录 | 实际核对 | 最终日志时间与结果 |
| --- | --- | --- |
| 仅导航 / `navigation-neoforge-1.21.1/run-standalone-probe/` | 不含库存 Mod，独立航点 API 可调用，无世界安全降级，主菜单就绪并正常关闭 | 23:27:26–23:27:29，`NAVIGATION STANDALONE PROBE PASSED` / `MAIN MENU READY` / `SHUTDOWN` |
| 库存与兼容插件开发探针，无导航 / `neoforge-1.21.1/run-plugin-probe/` | `NavigationGateway.discover()` 为空；库存、插件 reader、SQLite 与 Core 查询继续可用 | 23:23:26–23:23:29，`NAVIGATION ABSENT PROBE PASSED` 及兼容探针/主菜单/退出标记 |
| 库存、导航与开发探针联装 / `neoforge-1.21.1/run-navigation-probe/` | 实际发现独立 JAR 中的桥接类；共享 Core 坐标输入完成请求；无世界结果为 UNKNOWN、无路径、零估时；revision 可读取 | 23:27:59–23:28:02，`NAVIGATION BRIDGE PROBE PASSED` 及兼容探针/主菜单/退出标记 |

三个 `latest.log` 均未出现 ERROR/FATAL、线程未捕获异常或 `Shutdown failure!`。
兼容探针预期仍为 1 loaded / 2 skipped / 1 failed；故意制造的初始化/reader 故障为 WARN，
验证其错误隔离，不代表第三方 Mod 故障。无用户存档被打开。

## 步行实现与新增回归

- 普通航点可到达地板上方的空地；容器目标采用邻接交互位置与视线检查，避免要求站进实心容器。
- 搜索线程不读取原生世界；方块在搜索返回后、发布前变化时，丢弃未通过复核的路径。
- 预算中断只返回完成复核的前缀；后续停靠点在另一维度时保留已验证首段并说明缺口，不伪造传送边。
- 飞行状态使待处理步行请求失效；跳跃能力变化使已发布路线 revision 改变；关闭服务完成待处理请求，
  后续请求不读取世界。
- 保留已有半砖/台阶、危险/含水形状、未知区块、区块卸载、方块变化、世界替换及请求替换测试。
- CI 配置分别执行库存链路与可选导航链路检查；这里只证明本地对应任务通过，没有远程 CI 结果。

方案评估、原生读取边界、预算、估时和不支持动作详见[可选导航](../navigation.md)。
B1 完成；I3/I4 的本批实现与自动化完成。V7 真实世界路线、V4/V5 地图视觉和性能、I5 完整摘要、
交通与轨迹/共享预留仍未完成，整体 TODO 目标继续进行。
