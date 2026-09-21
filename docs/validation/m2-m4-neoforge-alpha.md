# NeoForge 1.21.1 M2-M4 验收记录

验收日期：2026-09-22
环境：Windows 10、Java 21、Minecraft 1.21.1、NeoForge 21.1.251

## 自动化验证

- `VanillaMenuSlotPartitionerTest` 覆盖箱类、潜影盒、生存物品栏，以及熔炉/高炉/烟熏炉、漏斗、
  酿造台、发射器/投掷器和合成器的固定持久槽位边界。
- `MenuFingerprintCalculatorTest` 覆盖内容未变不重复采集、数量或组件变化会改变指纹。
- `MenuSnapshotMapperTest` 覆盖箱子不混入玩家背包，以及两层潜影盒递归和标签缓存。
- `SnapshotStorageCoordinatorTest` 覆盖持久化恢复、会话级快照不入库、scope 隔离、清空当前 scope，
  异步删除，以及关闭后再次收到登出事件时保持幂等。
- `CaptureDiagnosticsTest` 覆盖采集、跳过和会话级降级计数。
- `BlockContainerRemovalMonitorTest` 覆盖带方块 ID 的替换检测，以及旧快照对空气/非方块实体的安全
  删除策略。

## 游戏内采集

真实开发客户端中完成了以下操作：

- 打开并多次修改普通箱/陷阱箱；每个菜单只记录 27 个容器槽，未记录玩家背包槽。
- 打开并修改潜影盒；记录 27 个槽和稳定的方块容器身份。
- 打开末影箱；使用玩家逻辑身份，在重新打开后替换同一根记录。
- 将带内容的潜影盒放入末影箱；离线读取数据库确认该根快照含嵌套容器。
- 打开熔炉并修改输入、燃料和产物；数据库记录为 `minecraft:furnace`、`slotCount=3`，未混入
  玩家背包。
- 创造模式打开物品栏；适配器读取真实 41 个玩家槽，而不是创造物品目录，诊断不再将
  `ItemPickerMenu` 记为误保存或未处理容器。
- 不打开物品栏直接拾取、丢弃和改变玩家携带物；文本查询汇总随 5 tick 稳定后的玩家物品栏
  快照自动变化。
- 破坏已索引方块容器；加载区块检查确认原方块消失后，内存索引和 SQLite 同步删除对应根记录，
  `stats` 的 `removed` 计数增加，离线读取数据库确认被删除 ID 未回写。

内容变化经过 5 tick 稳定窗口后替换同一容器快照；关闭前的最终变化通过 `CLOSE_FINAL` 提交。

## 持久化与恢复

第一轮退出后离线打开 scope 数据库，确认：

- 共有 5 个根快照（测试继续后增加）。
- 包含箱类、潜影盒和末影箱根记录。
- 末影箱中的潜影盒以嵌套快照保存。
- 没有快照映射或 SQLite 写入错误。

重启客户端、再次捕获前，后台存储日志报告恢复 5 个既有根快照；后续加入熔炉和玩家物品栏后，
索引继续在同一 scope 中恢复和扩展。

首次关闭测试发现 `GameShuttingDownEvent` 可能先于客户端登出事件，导致登出处理再次向已停止的
执行器提交任务。协调器改为幂等关闭并增加回归测试；修复后的真实客户端正常退出，没有再次出现
`RejectedExecutionException`。

## 查询与诊断

真实客户端中验证：

- `/litemfinder stats` 返回根容器、条目、variant、标签缓存和诊断计数。
- `/litemfinder search diamond` 对大小写不敏感，返回多个匹配物品和汇总数量。
- `/litemfinder exact <namespace:item>` 校验完整资源 ID 并汇总该物品的所有 variant。
- `/litemfinder clear` 只显示警告；`/litemfinder clear confirm` 才执行当前 scope 删除。
- 未匹配查询返回明确提示；普通采集明细只写 debug 日志。

## 结论

M2-M4 的退出条件已满足。当前功能是只读索引和开发查询入口，不包含正式 HUD、自动点击、搬运或
未访问区域扫描。
