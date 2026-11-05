# 版本与发布约定

LItemFinder 当前处于 **beta** 阶段。功能可用性和兼容性仍在验证，不能将当前开发版本或
后续 beta 发布称为 stable。稳定版的切换由维护者明确决定。

## 日常开发

- bugfix、feat、重构、资源和文档调整可持续积累在同一个开发版本中，不逐条发布 Release。
- 不因一次提交自动升版、打标签、整理发布说明或上传产物。CI 只构建与验证。
- 使用 `0.2.3-beta.1-SNAPSHOT` 这样的版本标识开发快照；版本号以 `gradle.properties` 为准。
- 运行 `./gradlew check` 验证项目；需要安装测试时运行
  `./gradlew :neoforge-1.21.1:build`，从该模块的 `build/libs/` 取安装 JAR。
  本地构建或安装测试无需创建 Git tag 或 GitHub Release。
- 开发记录可写入 `docs/development-status.md`；`docs/releases/` 留给明确准备发布的版本。

## 准备一次 beta 发布

只有用户明确要求发布，或事先约定的发布里程碑到达时，才执行以下步骤：

1. 汇总本批改动，确认验证范围与尚未完成的游戏内验收。
2. 将版本从开发快照调整为对应的 beta 版本，例如 `0.2.3-beta.1`。
3. 编写对应的 `docs/releases/<版本号>.md`，同步安装与兼容说明。
4. 运行 `./gradlew release`，检查 `build/releases/<版本号>/` 中的安装 JAR、发布包、源码包
   和 `SHA256SUMS`。此任务在本地打包；GitHub 上传是单独的发布操作。
5. 需要上传时使用 `v<版本号>` 标签，GitHub Release 必须标记为 **pre-release**。
6. 发布后按下一批开发计划设置新的 beta 快照版本，后续普通提交继续积累改动。

`release` 打包任务会拒绝 `-SNAPSHOT` 版本或缺少对应发布说明的版本，以免将日常构建误作发布包。
历史 `0.2.0`、`0.2.1`、`0.2.2` 发布记录与标签保留；历史编号没有 beta 后缀不代表当前已进入 stable。
