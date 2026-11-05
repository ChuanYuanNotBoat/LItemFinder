# LItemFinder

[![CI](https://github.com/ChuanYuanNotBoat/LItemFinder/actions/workflows/ci.yml/badge.svg)](https://github.com/ChuanYuanNotBoat/LItemFinder/actions/workflows/ci.yml)

LItemFinder 是 Minecraft 客户端模组，用于记录已观察到的容器库存，并查询物品、液体及其存放位置。

项目当前处于 **beta** 阶段，目标平台为 Minecraft 1.21.1 / NeoForge。功能与模组兼容性仍在验证中；
当前开发版本见 [gradle.properties](gradle.properties)，已发布文件见 [Releases](https://github.com/ChuanYuanNotBoat/LItemFinder/releases)。

## 主要功能

- 库存总览与搜索，查看物品、液体和容器内容。
- 容器位置地图，以及按已记录库存生成的物品获取计划。
- 按世界或服务器在本地保存索引。

索引来自玩家打开容器或观察受支持方块时，客户端已获得的数据，记录可能滞后于游戏内实际库存。
模组不扫描未访问区块，不主动请求未同步库存，不执行自动取物或搬运。
当前内置地图连接线只表示取物停靠点之间的关系，不代表已验证可通行的路径。

## 安装与使用

| 依赖 | 要求 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 最低 21.1.248，限 1.21.1 分支 |
| Java | 21 |

1. 正常退出游戏，将安装 JAR 放入客户端的 `mods` 目录，移出旧版 JAR。
2. 启动游戏，进入世界或服务器，打开需要记录的容器。
3. 按 `O`（可在按键设置中修改）或输入 `/litemfinder gui` 打开界面。

服务端无需安装。具体模组的采集条件和未接入范围见[兼容性说明](docs/compatibility.md)；
命令、数据目录与卸载方法见[使用说明](docs/usage.md)。

## 从源码构建

需要 JDK 21，使用仓库内的 Gradle Wrapper：

```bash
./gradlew check
./gradlew :neoforge-1.21.1:build
```

Windows 使用 `.\gradlew.bat` 替代 `./gradlew`。安装 JAR 输出到 `neoforge-1.21.1/build/libs/`，
文件名为 `litemfinder-neoforge-1.21.1-<版本号>.jar`；`-sources.jar` 为源码包。

日常修复和功能提交会积累在开发版本中。发布按明确要求或约定里程碑进行，beta 发布标记为
pre-release。流程见[版本与发布约定](docs/release-policy.md)。

## 文档

| 文档 | 内容 |
| --- | --- |
| [使用说明](docs/usage.md) | 库存记录、界面、命令与本地数据 |
| [兼容性说明](docs/compatibility.md) | 已实现的适配范围、采集条件与验证状态 |
| [开发指南](docs/development.md) | 模块结构、运行任务与架构入口 |
| [开发状态与 TODO](docs/development-status.md) | 当前实现、按优先级整理的待办与验收依据 |
| [版本与发布约定](docs/release-policy.md) | beta 版本与发布流程 |

## 许可证

[GNU General Public License v3.0](LICENSE)。第三方组件说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
