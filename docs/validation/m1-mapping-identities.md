# M1 验收记录：映射、身份与纯协调器

验收日期：2026-09-21
结果：**通过**

## 已完成映射

- `MinecraftIds` 在 `ResourceLocation` 与 Core `NamespacedId` 间显式转换。
- `MinecraftItemStackMapper` 在客户端线程读取非空 `ItemStack`，生成不可变 `ItemStackInfo` 和标签集合。
- `ComponentVariantFingerprint` 使用官方 `DataComponentPatch.CODEC` 与 registry context，仅编码持久化组件。
- 编码结果递归排序对象键、保留数组顺序，并输出 `components:v1:<sha256>`；空 patch 使用空 variant。
- `CachedItemTagResolver` 按物品 ID 缓存不可变标签，搜索线程不访问 Minecraft registry。
- 客户端资源重载和断开连接时清空标签缓存。

## 已完成身份策略

- 单人存档身份和多人服务器 host/port 在进入 scope 前规范化并使用 SHA-256 摘要。
- scope 固定使用 `scope:v1:<sha256>`，不含原始路径、服务器地址或显示名。
- 玩家背包和末影箱使用 `LOGICAL` 身份。
- 已确认方块与实体目标使用 `EXACT` 身份。
- 无法确认来源的菜单使用随机会话 ID 和 `SESSION_ONLY`，明确禁止持久化。
- Minecraft 包装 resolver 只把游戏对象转换成上述 Core 身份，不把游戏类型传出适配层。

## 已完成协调器

- `DebouncedCapture` 默认策略可表达打开后 1 tick 首次采集、变化稳定 5 tick 后采集和关闭前最终采集。
- 相同指纹不会重复发出，关闭时未稳定的最终变化不会丢失。
- `LatestValueBuffer` 对相同容器只保留最新待处理值，容量满时只拒绝新的 key，不拒绝已有 key 更新。
- 两个组件均不依赖 Minecraft 类型，可在普通 JUnit 中完整验证。

## 自动验收

- 全项目共有 46 项测试，其中原有 Core/SQLite 24 项无回归。
- NeoForge 测试通过官方 ModDevGradle unit-test 启动环境运行真实 Minecraft 类。
- variant 测试覆盖：count 隔离、对象键顺序、数组顺序、damage 和 custom data 差异。
- scope 测试覆盖：host 大小写/尾点规范化、端口隔离、单人与多人域隔离、原文不泄露。
- 身份测试覆盖：逻辑玩家容器、精确方块位置和不可持久化会话 fallback。
- 客户端完成启动与资源重载，没有 Mod 加载错误。

M1 没有遗留阻断项，可以进入 M2。
