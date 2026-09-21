# M0 验收记录：NeoForge 1.21.1 构建与依赖

验收日期：2026-09-21
结果：**通过**

## 验收环境

| 项目 | 实际值 |
| --- | --- |
| 操作系统 | Windows 10 amd64 |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.251 |
| ModDevGradle | 2.0.147 |
| Gradle Wrapper | 9.2.1 |
| 游戏 Java | Eclipse Temurin 21.0.12.1 |
| SQLite JDBC | 3.53.4.0 |

## 自动验收

- 根项目 `clean check` 通过，Core、SQLite 和 NeoForge 测试无回归。
- Core 导入边界检查通过，`core` 仍不依赖任何 Minecraft 或 Loader 类型。
- NeoForge 模块的 SQLite runtime probe 测试通过。
- 安装 JAR 成功生成，内嵌以下 Jar-in-Jar 组件：
  - `dev.litemfinder:core:0.2.0-SNAPSHOT`
  - `dev.litemfinder:storage-sqlite:0.2.0-SNAPSHOT`
  - `org.xerial:sqlite-jdbc:3.53.4.0`
- Mod 元数据声明 `litemfinder`、GPL-3.0、Minecraft 1.21.1 和客户端依赖侧。

## 游戏启动验收

开发运行 `runClient`：

- NeoForge 发现 `LItem Finder 0.2.0-SNAPSHOT`。
- 客户端入口成功初始化。
- SQLite runtime probe 成功加载 JDBC/native library 并创建 schema v1 数据库。
- 客户端完成资源加载并进入主菜单；人工确认没有 Mod 加载错误。

隔离成品运行 `runPackagedClient`：

- 运行配置不加载源码 Mod，只从 `run-packaged/mods` 加载构建后的安装 JAR。
- NeoForge Jar-in-Jar locator 发现包括三个项目依赖在内的嵌套依赖。
- 客户端入口与 SQLite runtime probe 再次成功，证明 native library 可从成品 JAR 加载。
- 客户端完成资源加载，没有 Mod 加载失败。

## 验收中修复的问题

1. 最初的开发客户端未把 `storage-sqlite` 源集注册到 NeoForge Mod，导致
   `NoClassDefFoundError`。现已将 Core 与 SQLite 源集纳入开发 Mod。
2. 最初的成品 smoke 同时加载外部和内嵌 SQLite JDBC，导致重复 Java module。现已把额外
   runtime classpath 限定到普通开发客户端，成品客户端只使用 Jar-in-Jar 副本。

M0 没有遗留阻断项，可以进入 M1。
