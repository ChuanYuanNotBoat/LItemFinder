# 项目协作规范

## 版本与发布

- 当前项目处于 **beta** 阶段，不得默认描述为 stable 或正式稳定版。
- 日常 bugfix、feat、重构和文档调整只进行实现、验证及必要的开发记录；不要为每个改动自动
  修改版本号、创建 Git tag、生成发布包或发布 GitHub Release。
- 只有用户明确要求发布，或事先约定的发布里程碑到达时，才整理并发布一批已经验证的改动。
  用户要求构建或本地安装测试，不代表要求发布。
- 日常开发版本保留 `-beta.<序号>-SNAPSHOT` 后缀；准备 beta 发布时使用 `-beta.<序号>`，
  GitHub Release 标记为 pre-release。转为 stable 需要用户明确决定。
- 既有版本、标签与 Release 属于历史记录，不擅自删除、重打标签或修改线上发布状态。
- 日常验证使用 `./gradlew check`，本地安装包使用 `./gradlew :neoforge-1.21.1:build`。
  发布流程及版本约定见 [docs/release-policy.md](docs/release-policy.md)。

## README 与文档

- README 作为项目入口，只保留项目定位、beta 状态、主要能力与限制、安装/构建方法和文档链接。
- 使用客观、可核对的描述，避免宣传性措辞、逐次修复叙述、内部类型清单和大段路线图。
- 不在 README 逐条追加模组适配。采集条件、版本范围及已知限制写入 `docs/compatibility.md`；
  操作细节写入 `docs/usage.md`，实现与待验收事项写入 `docs/development-status.md`。
- 区分“已实现”“自动化测试通过”和“游戏内验收”。不能将接口、模拟测试或单一版本的核对
  描述为对整个模组或所有版本已经兼容；计划功能不得作为现有能力展示。
- 避免在 README 重复写易过期的版本号、JAR 文件名和最新发布状态，优先链接其配置或发布入口。

## 架构

- 遵守 [Core 与适配层边界](docs/core-adapter-boundary.md)：通用库存模型和规则留在 Core，
  Minecraft/NeoForge 读取、纹理和界面适配留在 Loader。
