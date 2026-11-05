# 库存兼容插件

本接口面向 Minecraft 1.21.1 / NeoForge 的客户端扩展，当前属于 beta API。它负责将已经同步的
原生库存交给现有采集链路；快照合并、索引、汇总与持久化仍由 Core 和 SQLite 实现。

## 设计依据与边界

参考的是扩展机制，而不是照搬其他模组的数据获取方式：

| 参考 | 可借鉴的机制 | 本项目的处理 |
| --- | --- | --- |
| [Jade 1.21 NeoForge PriorityStore](https://github.com/Snownee/Jade/blob/6701b1eea41143b2e4713ed9c6e7d57e2595a820/src/main/java/snownee/jade/impl/PriorityStore.java)及[开发文档](https://jademc.readthedocs.io/en/1.x/plugins20/getting-started/) | 独立 provider、类型匹配、标识与优先级；同步和展示分开 | 按 Java 目标类型缓存候选 reader，明确读取状态；不增加服务端 provider 或同步请求 |
| [JEI 1.21.1 PluginCaller](https://github.com/mezz/JustEnoughItems/blob/4a9042f579f4602ff21827dd873cffe57329473d/Library/src/main/java/mezz/jei/library/load/PluginCaller.java) | 统一插件调用边界、隔离可选插件错误 | 插件初始化失败时撤销已登记的 reader，其他插件继续；读取错误保留旧库存 |
| [WTHIT 1.21.1 PluginLoader](https://github.com/badasintended/wthit/blob/4403649a3e0491696d300add7e9d9edab8a9ba41/src/main/java/mcp/mobius/waila/plugin/PluginLoader.java) | 清单声明入口与模组/版本条件，在加载类之前筛选 | 从已安装 Mod 文件发现清单，先检查条件再初始化可选类 |

上述链接固定到本次核对的提交，避免把其他 Minecraft 版本的 API 当作当前依据。此实现自行编写，
不引入 Jade、JEI 或 WTHIT 的运行时依赖。插件机制本身不代表新增了某个模组的专用适配。

纯客户端无法保证任意方块能力都包含已同步的真实库存。适配器必须确认同步来源、实际物理库存
身份以及槽位边界，不能以能力存在或 getter 返回空值作为“已读到空容器”的证据。

## 自动发现

扩展 Mod 在 JAR 根目录放置 `litemfinder.plugins.json`，例如：

```json
{
  "example:storage": {
    "entrypoint": "com.example.compat.ExampleInventoryPlugin",
    "required": {
      "example": "[1.0,2.0)"
    }
  }
}
```

- 每个插件 ID 使用 `namespace:path`，同一 ID 出现于多个清单时均拒绝，避免加载顺序决定结果。
- `entrypoint` 实现 `InventoryPlugin`，提供公开无参构造函数。
- 可选 `required` 使用显式 Maven 版本范围；`[1.0]` 表示精确版本，`[1.0,)` 表示最低版本。
  不接受含义容易误解的裸版本号。所有条件满足后才加载入口类，因此可在该类中引用条件指定的 API。
- 清单只从 NeoForge 已发现的 Mod 文件读取，不从服务器资源包或普通资源包读取。
- 在客户端 setup 的主线程队列中注册。构造/注册错误会隔离并撤销该插件已完成的登记；
  注册回调结束后不能继续添加 reader。重新加载器测试和正常关闭也会移除其登记。
- 当前不提供运行时插件重载界面；更改扩展 JAR 后应重启客户端。

## 注册 reader

入口接收 `InventoryPluginRegistration`。类型和 DTO 均位于
`dev.litemfinder.neoforge.api.inventory`；它提供以下登记方式：

| 方法 | 用途 |
| --- | --- |
| `blockItems(id, type, priority, reader)` | 无菜单的、已同步方块物品库存 |
| `blockFluids(id, type, priority, reader)` | 带确认方块身份与位置的液体库存 |
| `menu(id, type, priority, reader)` | 已打开菜单的实际 backing storage 和位置 |
| `fluids(id, type, priority, reader)` | 已确认同步的方块液体视图，供菜单等链路使用 |
| `equipment(source)` | 复用现有装备来源协议与不可用数据保留 |
| `terminal(adapter)` | 复用显式终端、原根身份和可见范围协议 |

前四种 reader 的类型参数是具体菜单/方块实体类，也匹配它的子类。优先级较大者先运行，同优先级
按完整 ID 字典序；同一个登记通道内 ID 必须唯一。候选列表按运行时类缓存，注册或撤销时失效。
显式类型 reader 先于历史无类型 reader 与通用反射回退运行。

reader 返回 `InventoryReadResult<T>`，不能用同一个空值兼任以下三种含义：

| 返回值 | 含义与行为 |
| --- | --- |
| `unsupported()` | 该对象不属于此适配器；继续寻找下一 reader |
| `unavailable()` | 属于此适配器，但同步未完成或当前不可读取；停止回退，不提交清空 |
| `available(inventory)` | 已确认读取成功；真正空库存也需提供结构合法的空 inventory |

例如一个宽泛的目标类可能同时包含普通库存和网络代理；reader 应先排除不支持的代理，再判断同步
是否可用，最后构造实际 DTO。不要先读取可能修改活跃物品/流体状态的 getter 再检查创造无限视图。

运行时异常或可选类链接错误等同于 `unavailable()`，不会跌落到低优先级的默认空库存。每个 reader
首次失败会记录 ID 和异常类型，后续读取继续尝试，允许同步到达后恢复；不会逐 tick 输出堆栈。
历史直接注册的 `Optional` 接口保持原行为，但无法区分不支持和不可用，新扩展优先使用类型 reader。

所有原生读取均在客户端线程完成，不保留活跃菜单/方块/物品给后台线程，不点击、不发包，不修改
库存。物品槽、空槽、液体容量、原根身份和分页覆盖的 DTO 约束见[库存适配接口](inventory-adapters.md)。

## 标准组件容器

嵌套读取支持物品上的标准 `minecraft:container` Data Component，不要求该物品一定是潜影盒。
按组件保留原槽号，继续使用既有递归深度、variant、路径和索引规则；标准组件未记录的尾部空槽
不推测容量。原版潜影盒仍保留 27 格布局。Sophisticated 背包仍使用其已经同步的专用缓存。

此路径不读取任意 custom data/NBT，也不把没有同步保证的通用方块能力当作真实库存。

## 诊断与验证

`/litemfinder compat` 显示清单插件的载入、跳过和失败情况（最多展示十条），内置适配器不在此清单
计数中。依赖缺失或版本范围不匹配是跳过；解析、构造、注册失败和重复插件 ID 是失败。
读取期错误见日志中对应 reader ID。加载成功只说明登记完成，不等于实际游戏兼容验收通过。

本次自动回归覆盖类型/优先级筛选与缓存失效、重复登记与幂等撤销、不支持/不可用/空值语义、
运行时及链接错误后的恢复、依赖/版本筛选、清单与入口错误隔离、重复插件 ID、初始化回滚、
注册作用域，以及标准组件的槽位、递归路径、数量和清空。完整整合包与第三方扩展仍需独立实机记录。

另已用独立 addon JAR 在实际 Windows 成品客户端验证清单发现、可选类/版本筛选、初始化回滚，
以及 reader → 原生映射 → SQLite 关闭/重开 → Core 查询链路。可通过
`./gradlew :neoforge-1.21.1:verifyPluginProbeClient` 重复运行，详见
[运行记录与范围](validation/map-transition-plugin-runtime-2026-10-28.md)。该探针是受控扩展，不能据此
宣称任何实际库存模组的完整兼容性。
