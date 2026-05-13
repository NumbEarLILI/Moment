# Moment 安卓 App 架构设计文档

## 1. 背景与目标

Moment 是一款用于记录日常生活碎片，并自动整理成日记手帐的安卓 App。用户可以在一天中快速记录文字、图片、心情、标签等碎片信息；App 在本地按时间线聚合这些素材，并在用户触发或每日结束时生成一篇结构化、可编辑、可保存的日记手帐。

本设计文档定义首个可实现版本的架构。当前仓库只有 README，尚未存在安卓工程，因此建议从一个原生 Android 项目开始，优先交付可离线使用、数据安全、结构清晰、便于后续接入 AI 能力的 MVP。

## 2. 产品范围

### 2.1 MVP 目标

MVP 聚焦三个核心闭环：

1. 快速记录：用户能在几秒内添加生活碎片。
2. 自动生成：系统能把某一天的碎片整理成一篇日记手帐草稿。
3. 可编辑留存：用户能编辑、保存、查看历史日记。

### 2.2 MVP 功能

- 碎片记录
  - 文字记录。
  - 图片记录。
  - 心情选择。
  - 标签。
  - 创建时间自动记录。
- 今日时间线
  - 按时间倒序展示当天碎片。
  - 支持查看碎片详情。
  - 支持删除碎片。
- 日记生成
  - 对指定日期的碎片进行聚合。
  - 生成标题、正文、亮点、心情总结。
  - 首版使用本地规则生成，避免首个版本依赖云端模型。
- 日记编辑与归档
  - 用户可编辑生成结果。
  - 保存为当天日记。
  - 查看历史日记列表和详情。
- 本地数据持久化
  - 碎片、日记、图片引用均保存在本地。

### 2.3 非 MVP 范围

以下能力不进入首版，但架构保留扩展点：

- 云同步与多端登录。
- 在线大模型生成。
- 语音转文字。
- OCR 图片识别。
- 社交分享社区。
- 复杂手帐模板市场。
- 端到端加密备份。

## 3. 关键设计原则

1. 离线优先：核心记录和生成能力不依赖网络。
2. 本地隐私：用户生活记录默认只保存在设备本地。
3. 生成可控：自动生成结果只是草稿，用户拥有最终编辑权。
4. 可替换生成器：首版用规则生成，后续可替换为云端或本地 AI。
5. 小模块边界：UI、业务逻辑、数据访问、生成逻辑分层，降低后续迭代成本。

## 4. 技术栈

### 4.1 推荐技术栈

- 语言：Kotlin
- UI：Jetpack Compose
- 架构：MVVM + Repository + Use Case
- 异步：Kotlin Coroutines + Flow
- 本地数据库：Room
- 依赖注入：Hilt
- 图片加载：Coil
- 导航：Navigation Compose
- 测试：
  - JUnit 用于领域逻辑和生成器测试。
  - Room in-memory database 用于数据层测试。
  - Compose UI Test 用于关键界面流程测试。

### 4.2 选择理由

原生 Kotlin 与 Jetpack Compose 适合从空仓库搭建长期维护的 Android 应用。Room、Flow、Hilt、Navigation Compose 都是稳定的 Android 生态组件，便于形成清晰的数据流和测试边界。自动生成日记的核心逻辑会放在独立领域模块中，避免与 UI 或数据库强耦合。

## 5. 总体架构

应用采用单模块起步、包内分层的结构。MVP 阶段不急于拆成多个 Gradle module，以降低工程复杂度；当功能扩展到云同步、AI 生成、多媒体处理后，再考虑拆分为 `core:data`、`core:domain`、`feature:timeline`、`feature:diary` 等模块。

```text
app/
  src/main/java/com/example/moment/
    MomentApplication.kt
    data/
      local/
        MomentDatabase.kt
        FragmentDao.kt
        DiaryDao.kt
        entity/
      repository/
        FragmentRepositoryImpl.kt
        DiaryRepositoryImpl.kt
    domain/
      model/
      repository/
      usecase/
      generator/
    ui/
      navigation/
      theme/
      home/
      capture/
      diary/
      history/
```

### 5.1 分层职责

#### UI 层

UI 层使用 Jetpack Compose 实现界面，并通过 ViewModel 暴露的状态渲染页面。UI 不直接访问 Room，也不直接拼装日记内容。

主要页面：

- HomeScreen：今日概览和碎片时间线。
- CaptureScreen：新增碎片。
- DiaryPreviewScreen：生成结果预览和编辑。
- DiaryHistoryScreen：历史日记列表。
- DiaryDetailScreen：日记详情。

#### Presentation 层

每个主要页面拥有对应 ViewModel。ViewModel 负责：

- 调用 Use Case。
- 维护 UI state。
- 处理用户事件。
- 暴露错误、加载状态、成功状态。

#### Domain 层

Domain 层包含业务模型、仓库接口、用例和日记生成器接口。它不依赖 Android Framework 和 Room，方便做 JVM 单元测试。

核心对象：

- LifeFragment：生活碎片。
- DiaryEntry：日记。
- Mood：心情枚举。
- FragmentRepository：碎片仓库接口。
- DiaryRepository：日记仓库接口。
- GenerateDiaryUseCase：生成日记用例。
- DiaryGenerator：日记生成器接口。
- RuleBasedDiaryGenerator：本地规则生成实现。

#### Data 层

Data 层负责 Room 数据库、DAO、Entity 与 Domain Model 转换、Repository 实现。

首版数据全部存在本地。图片不直接写入数据库，而是保存 URI 或本地文件路径，数据库只保存引用和元数据。

## 6. 数据模型

### 6.1 LifeFragment

```kotlin
data class LifeFragment(
    val id: Long,
    val content: String,
    val imageUris: List<String>,
    val mood: Mood?,
    val tags: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

说明：

- `content` 可为空字符串，用于支持纯图片碎片。
- `imageUris` 保存图片引用。
- `mood` 可为空，用户可以不选择心情。
- `tags` 用于后续检索和生成聚类。

### 6.2 DiaryEntry

```kotlin
data class DiaryEntry(
    val id: Long,
    val date: LocalDate,
    val title: String,
    val body: String,
    val highlights: List<String>,
    val moodSummary: String?,
    val sourceFragmentIds: List<Long>,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

说明：

- 一天默认对应一篇日记。
- `sourceFragmentIds` 记录生成来源，便于以后重新生成或展示引用。
- `body` 是用户最终可编辑文本。

### 6.3 Room Entity

Room 不直接存储复杂对象。`imageUris`、`tags`、`highlights`、`sourceFragmentIds` 使用 TypeConverter 转换为 JSON 字符串。首版可用 Kotlin serialization 或 Moshi；若希望减少依赖，也可用简单分隔符，但 JSON 更安全，能处理特殊字符。

## 7. 日记生成设计

### 7.1 Generator 接口

```kotlin
interface DiaryGenerator {
    fun generate(date: LocalDate, fragments: List<LifeFragment>): DiaryDraft
}
```

`DiaryDraft` 是生成草稿，不直接等同于最终 `DiaryEntry`。

```kotlin
data class DiaryDraft(
    val title: String,
    val body: String,
    val highlights: List<String>,
    val moodSummary: String?
)
```

### 7.2 首版规则生成策略

首版 `RuleBasedDiaryGenerator` 使用确定性规则：

1. 按时间正序排列当天碎片。
2. 根据碎片数量和心情生成标题。
3. 将文字碎片按时间段归类：
   - 清晨：05:00 - 09:59
   - 上午：10:00 - 11:59
   - 午后：12:00 - 17:59
   - 夜晚：18:00 - 23:59
   - 深夜：00:00 - 04:59
4. 每个时间段生成一段自然语言摘要。
5. 选出最多 3 条较长或带标签的碎片作为 highlights。
6. 根据心情频次生成心情总结。

示例输出：

```text
标题：平静而充实的一天

今天的清晨从一杯咖啡开始，你记录下了出门前的轻松心情。
午后，你完成了几件重要的小事，也留下了一些关于工作和生活的片段。
夜晚的记录更安静，像是在给这一天收尾。

今日亮点：
- 和朋友散步聊天
- 完成了拖延很久的小任务
- 晚上读了几页书

心情总结：整体偏平静，中间夹杂着一点开心。
```

### 7.3 后续 AI 生成扩展

AI 生成不直接替换 Use Case，而是新增实现：

```kotlin
class AiDiaryGenerator(
    private val client: DiaryGenerationClient
) : DiaryGenerator
```

这样 UI 和 Use Case 不需要关心生成来源。未来可按设置切换本地规则、云端模型或端侧模型。

## 8. 主要用户流程

### 8.1 添加碎片

1. 用户在首页点击添加。
2. 进入 CaptureScreen。
3. 输入文字，选择图片、心情、标签。
4. 点击保存。
5. ViewModel 调用 `AddFragmentUseCase`。
6. Repository 写入 Room。
7. 首页时间线自动刷新。

### 8.2 生成日记

1. 用户在首页点击“生成今日手帐”。
2. ViewModel 调用 `GenerateDiaryUseCase(date)`。
3. Use Case 从 FragmentRepository 读取当天碎片。
4. Use Case 调用 DiaryGenerator 生成草稿。
5. 页面跳转到 DiaryPreviewScreen。
6. 用户编辑标题和正文。
7. 点击保存。
8. DiaryRepository 持久化 DiaryEntry。

### 8.3 查看历史

1. 用户进入历史页面。
2. DiaryHistoryViewModel 订阅 DiaryRepository。
3. 按日期倒序展示日记列表。
4. 点击某篇进入详情。

## 9. 导航结构

```text
Home
  -> Capture
  -> DiaryPreview(date)
  -> DiaryHistory
       -> DiaryDetail(diaryId)
```

底部导航首版可以只保留两个入口：

- 今日
- 日记

Capture 使用浮动按钮或顶部操作进入，减少底部导航复杂度。

## 10. 状态与错误处理

### 10.1 UI State

每个页面使用不可变 UI state：

```kotlin
data class HomeUiState(
    val isLoading: Boolean = true,
    val fragments: List<LifeFragment> = emptyList(),
    val errorMessage: String? = null
)
```

ViewModel 通过 `StateFlow<HomeUiState>` 暴露状态。

### 10.2 错误处理

MVP 错误类型主要包括：

- 数据库读写失败。
- 图片 URI 不可访问。
- 当天没有碎片时生成日记。

处理策略：

- 数据库错误：显示通用失败提示，并保留当前页面。
- 图片错误：允许保存文字部分，同时提示图片未能加入。
- 无碎片生成：提示“今天还没有记录，先写下一点什么吧”。

## 11. 隐私与数据安全

首版默认本地存储，不上传用户内容。需要在产品文案中明确：

- 记录默认保存在本机。
- 卸载 App 可能导致数据丢失。
- 后续如接入云端 AI 或同步，需要单独获得用户授权。

数据库首版可不加密，以降低复杂度；如果用户内容敏感度要求较高，后续可引入 SQLCipher 或 Android Keystore 保护本地密钥。

## 12. 测试策略

### 12.1 单元测试

优先覆盖 Domain 层：

- `RuleBasedDiaryGenerator`：
  - 无碎片时返回空状态或明确错误。
  - 单条碎片能生成包含内容的日记。
  - 多时间段碎片按时间顺序出现在正文。
  - 多个心情能生成主导心情总结。
- `GenerateDiaryUseCase`：
  - 正确读取指定日期碎片。
  - 生成草稿保留来源碎片 ID。

### 12.2 数据层测试

- DAO 插入、查询、删除。
- 指定日期查询边界。
- TypeConverter 能正确保存和还原列表字段。

### 12.3 UI 测试

首版只覆盖关键路径：

- 添加文字碎片后首页出现该碎片。
- 无碎片点击生成时出现提示。
- 有碎片生成日记后可以保存。

## 13. 工程落地顺序

推荐按以下顺序实现：

1. 创建 Android Gradle 工程骨架。
2. 配置 Kotlin、Compose、Room、Hilt、测试依赖。
3. 建立 Domain 模型与 `RuleBasedDiaryGenerator`，先写单元测试。
4. 建立 Room 数据层与 Repository。
5. 实现添加碎片和首页时间线。
6. 实现生成日记预览与保存。
7. 实现历史列表和详情。
8. 补充 UI 测试和 README 使用说明。

## 14. 方案取舍

### 14.1 方案 A：原生 Android + 本地规则生成

优点：

- 隐私友好，离线可用。
- 工程边界清晰。
- 测试稳定，首版风险低。
- 后续可平滑接入 AI 生成。

缺点：

- 生成内容不如大模型自然。
- 需要后续迭代模板和文案规则。

### 14.2 方案 B：原生 Android + 云端 AI 生成

优点：

- 生成质量更接近真实日记。
- 可快速支持多风格、多语气。

缺点：

- 需要后端、账号、隐私授权、费用控制。
- 首版复杂度明显增加。
- 无网或服务异常时体验受影响。

### 14.3 方案 C：跨平台 App

优点：

- 后续可复用到 iOS。
- 产品探索期可能更快覆盖多端。

缺点：

- 当前需求明确是安卓 App。
- 原生能力、图片权限、后台能力和系统集成会更复杂。
- 仓库从零开始时，原生 Android 更直接。

### 14.4 推荐

推荐采用方案 A：原生 Android + 本地规则生成。它能最快形成可运行闭环，同时为后续 AI 能力留下清晰扩展点。

## 15. 待确认的产品决策

这些决策不会阻塞 MVP 架构，但会影响后续界面和生成体验：

1. 手帐风格更偏文字日记、图文卡片，还是贴纸拼贴感。
2. 是否需要日历视图作为历史入口。
3. 图片是否需要复制到 App 私有目录，还是只保存系统相册 URI。
4. 是否需要导出为图片或 Markdown。
5. 是否要求应用启动时本地密码或生物识别解锁。

## 16. 验收标准

MVP 完成后应满足：

1. 用户可以新增、查看、删除当天碎片。
2. 用户可以基于当天碎片生成一篇日记草稿。
3. 生成草稿包含标题、正文、亮点和心情总结。
4. 用户可以编辑并保存日记。
5. 用户可以查看历史日记。
6. 关闭并重启 App 后，碎片和日记仍然存在。
7. 核心生成逻辑和数据访问逻辑有自动化测试覆盖。

