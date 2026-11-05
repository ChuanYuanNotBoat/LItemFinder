# 开发指南

需要 JDK 21，使用仓库内的 Gradle Wrapper，无需全局安装 Gradle。

## 模块

| 模块 | 职责 |
| --- | --- |
| `core` | 通用模型、观测合并、索引、搜索、容器/液体投影与规划 |
| `storage-sqlite` | 基于 Core 接口的 SQLite 持久化 |
| `neoforge-1.21.1` | Minecraft/NeoForge 数据采集、原生纹理与界面 |
| `navigation-core` | 纯 Java 碰撞步行、路径搜索和交通/准备任务原型；不打入库存安装 JAR |
| `navigation-neoforge-1.21.1` | 可选只读步行导航 Mod，负责世界观测、失效和库存桥接；独立/联装启动探针通过，世界内路线待验收 |

Core 不导入 Minecraft、Loader 或具体模组类。游戏对象先由适配层转换为 Core 模型；
通用库存规则留在 Core。完整约定见[Core 与适配层边界](core-adapter-boundary.md)。

## 构建与验证

Windows：

```powershell
.\gradlew.bat check
.\gradlew.bat :neoforge-1.21.1:build
```

macOS / Linux 使用 `./gradlew`。`check` 包含模块检查、自动化测试、Core 导入边界检查与安装
JAR 内容校验。安装 JAR 位于 `neoforge-1.21.1/build/libs/`，不要将 `-sources.jar` 用于安装。

`check` 同时校验库存 JAR 不含导航、导航 JAR 仅内嵌 Core/navigation-core 且清单路径存在。
CI 分别执行库存模块链路和可选导航模块链路。两种成品的构建检查与实际游戏验收分别记录。

启动 NeoForge 源码开发客户端：

```powershell
.\gradlew.bat :neoforge-1.21.1:runClient
```

在隔离目录中启动实际安装 JAR 及其 Jar-in-Jar 依赖：

```powershell
.\gradlew.bat :neoforge-1.21.1:runPackagedClient
```

这两个任务用于开发。自动化测试通过不等于游戏内视觉、交互或模组组合已经验收。
验收结果应注明日期、环境、操作与未覆盖项，见[现有验收记录](validation/)。

验证独立 addon JAR 的插件发现、依赖筛选、回滚、实际 reader、映射、SQLite 与 Core 查询：

```powershell
.\gradlew.bat :neoforge-1.21.1:verifyPluginProbeClient
```

此任务在 `neoforge-1.21.1/run-plugin-probe/` 启动真实成品客户端，加载单独构建的开发探针
addon；主菜单完成资源加载与绘制后正常退出，并检查运行和退出日志。探针故意触发一次插件
初始化失败和一次 reader 失败，期望最终为 1 loaded / 2 skipped / 1 failed。不会打开用户存档，
只在该隔离目录关闭首次辅助功能引导；探针不进入安装 JAR，不在日常 `check` 中自动启动游戏。
此验证需要可运行图形客户端的环境，范围见[跨 JAR 运行记录](validation/map-transition-plugin-runtime-2026-10-28.md)。

验证可选导航独立启动和库存/导航成品联装：

```powershell
.\gradlew.bat :navigation-neoforge-1.21.1:verifyStandaloneClient
.\gradlew.bat :neoforge-1.21.1:verifyNavigationProbeClient
```

分别在导航模块的 `run-standalone-probe/` 和库存模块的 `run-navigation-probe/` 启动隔离客户端，
主菜单就绪后正常退出。后者实际检查服务发现、共享 Core 输入和无世界请求降级；不会打开用户存档。
独立命令、构建产物、观测预算及限制见[可选导航](navigation.md)，实际结果见[本批运行记录](validation/navigation-runtime-2026-10-28.md)。

## 相关文档

- [Core v1 范围](core-v1.md)
- [库存适配接口](inventory-adapters.md)
- [兼容插件入口与开源机制参考](inventory-plugins.md)
- [可选导航、接口边界与限制](navigation.md)
- [GUI 与导航架构](gui-navigation-architecture.md)及[实施计划](gui-navigation-implementation-plan.md)
- [NeoForge 阶段计划](next-phase-neoforge-1.21.1.md)
- [开发状态、统一 TODO 与历史验收](development-status.md)
- [beta 版本与发布约定](release-policy.md)
