# 开发准备状态

盘点日期：2026-09-21

## 初始状态

- `E:\projects\tools\working\LItemFinder` 是新建空目录。
- 目录中没有 Git 元数据、源文件、构建脚本或未提交改动。
- 没有发现适用于该目录的 `AGENTS.md`。

## 本机环境

| 项目 | 状态 |
| --- | --- |
| Git | 可用，2.54.0.windows.1 |
| 默认 Java/Javac | Oracle JDK 17.0.12 |
| JDK 21 工具链 | 已安装，Temurin 21.0.12.1，位于 Gradle 工具链目录 |
| 全局 Gradle | 未安装 |
| Gradle Wrapper | 项目准备时生成，固定使用 Gradle 8.12 |

默认 `java` 仍指向 JDK 17，但 Gradle 构建脚本明确要求 JDK 21，并可从本机 Gradle
工具链目录发现现有的 Temurin 21。日常构建应使用仓库内的 Wrapper。

## 当前准备范围

- 初始化单仓库、多模块 Gradle 基础结构，但目前只包含 `core`。
- Core 使用 Java 21，并预留 JUnit 5 测试依赖。
- 增加轻量的 Core 导入边界检查。
- 暂不添加 SQLite 驱动，也不创建任何 Minecraft Loader 模块。

## Core Model 实现状态

- 已建立 `dev.litemfinder.core.model`，全部类型仅依赖 Java 标准库。
- 已实现物品标识和数量、容器身份/类型、世界与逻辑位置。
- 已实现不可变容器快照、嵌套容器槽位和容器路径。
- 已添加 JUnit 5 测试，覆盖标识校验、不可变性、槽位约束与嵌套快照。

## 待确认的设计决策

- 发布坐标和 Java 包名目前使用 `dev.litemfinder`，正式发布前仍可调整。
- 第一批支持的 Minecraft 版本和首个 Loader 尚未确定。
- `ItemKey` 已预留不透明 `variant`；数据组件/NBT 的规范化与匹配规则仍需在索引层前确定。
- SQLite 是直接作为 Core 的实现子包，还是拆为独立 `storage-sqlite` 模块，可在接口成形后决定。
