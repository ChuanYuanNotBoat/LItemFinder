# 2026-10-28 Core 物品规则与兼容插件验证

## 环境与产物

- Windows 10 amd64，Eclipse Temurin 21.0.12.1。
- Minecraft 1.21.1，NeoForge 21.1.248，开发版本 `0.2.3-beta.1-SNAPSHOT`。
- 产物：`neoforge-1.21.1/build/libs/litemfinder-neoforge-1.21.1-0.2.3-beta.1-SNAPSHOT.jar`。
- SHA-256：`4285f5bf338cd36685b68df4aeaf2a7a91a4e6b455bfafac1b030dddaf9698c5`。
- 不清空索引、不替换用户整合包 JAR、不修改版本号或发布状态。

## 自动化结果

执行 `.\gradlew.bat check`，结果通过。JUnit 共 194 项，0 失败、0 错误、0 跳过：
Core 46、SQLite 4、NeoForge 132、navigation-core 12。部分任务复用构建缓存或已有输出。
Core 导入边界、安装 JAR 内嵌依赖与许可证检查通过。

| 检查 | 覆盖与结论 |
| --- | --- |
| Core 迁移 | 物品聚合、数量规则、会话草稿、精确来源分配和停靠排序及其 8 项既有测试迁入 Core；游戏端调用 Core，规则不再依赖 Loader |
| 类型分发 | 目标类/子类筛选、优先级与同优先级稳定 ID、注册/撤销后的缓存失效、重复登记拒绝及幂等关闭 |
| 观测保护 | 不支持才继续；不可用和异常不回退到默认空库存；确认成功的空值可返回；运行时/链接错误后可恢复读取 |
| 插件生命周期 | 缺失模组/错误版本在入口实例化前跳过；清单/条目/入口错误隔离；重复 ID 均拒绝；部分注册失败回滚；注册结束后拒绝追加；加载器重载清理旧登记 |
| 标准组件嵌套 | 非潜影盒物品的标准容器组件可进入快照；保留原槽 5、嵌套路径 2.5、内层 27 格潜影盒和 7 个钻石；清空组件不留下该子容器 |
| SQLite probe | 写入、关闭、重新打开、精确读取及清理自身记录；重复 probe 不删除其他快照 |

本批新增 12 项回归，旧测试迁移不增加总测试数。上述插件测试使用受控入口和菜单数据，
不构成对某个第三方模组版本的实机兼容结论。

## 当前成品启动

执行 `.\gradlew.bat :neoforge-1.21.1:runPackagedClient`，使用仓库的隔离成品运行配置。
该配置不加载源码 Mod，从隔离 `mods` 目录加载构建后的安装 JAR。

当前产物启动日志（`neoforge-1.21.1/run-packaged/logs/latest.log`，后续运行可能覆盖）表明：

- Mod 列表包含 LItem Finder beta 快照、Minecraft 1.21.1 和 NeoForge 21.1.248。
- 客户端入口初始化成功。
- SQLite runtime probe 在游戏进程中完成写入、关闭、重开、读取与清理，验证了成品驱动/native 链路。
- 客户端 setup 完成插件发现；本环境无扩展清单，结果为 0 loaded / 0 skipped / 0 failed。
- 资源、纹理与音频初始化完成；本次日志未见 Mod 加载错误或异常。

窗口捕获两次分别返回 `FrameArrived timed out: timed out waiting on channel` 和
`window capture timed out: timed out waiting on channel`，未取得可核对的游戏画面。
因此没有宣称主菜单视觉或游戏内交互验收通过。测试客户端由测试会话中断，未验证正常退出流程。

## 尚未覆盖

- 实际扩展 Mod 清单的跨 JAR 正向载入及真实菜单/方块数据；当前正向注册、回滚和条件检查为自动化测试。
- 用户整合包中的采集、显示、输入、同步时序和性能；未进入或修改用户当前游戏世界。
- Linux 成品客户端、正常退出/断线、游戏 scope 切换和游戏进程重启后的库存恢复。
- 真实导航、跨模块共享预留、具体网络存储终端适配。

统一 TODO 中 I2 和 C1 的本批实现/自动化部分完成；V1-V6 继续保持待验收，V6 已有以上 Windows
启动与数据库 probe 的部分证据。
