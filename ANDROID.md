# 远路播客 Android App 开发计划与上下文 (Context & Plan)

> **当前状态**: Phase 2 (M3) 已完成，M4 待启动
> **同步日期**: 2026-08-27
> **说明**: 本文档汇总了项目的背景上下文、最新开发进度，以及完整的 Android 开发计划，方便在独立仓库中为 AI 助手提供全局 Context。

---

## 1. 项目背景与上下文 (Context)

`yuanlu-android` 是「远路播客（Yuanlu）」的官方原生 Android 客户端。
- **业务定位**: 英语学习播客平台，对标现有的 Web 端全量用户侧功能。
- **技术栈**: Kotlin + Jetpack Compose + Media3 (后台播放) + Clean Architecture (单模块分层) + Hilt + Retrofit + Room。
- **关联后端**: Web 端基于 Next.js 16 (App Router), Prisma, PostgreSQL, 阿里云 OSS, 有道智云 ISE。

### 🚨 后端前置改造已完成 (Phase 1)
为了支持移动端，后端（`yuanlu` 仓库）已完成针对 Android App 的专属改造。在后续开发网络层时，**请直接使用以下机制，无需要求后端再改代码**：

1. **移动端认证 (Bearer Token)**：
   - 所有现有的后端 `/api/*` 端点（原本只支持 NextAuth Cookie）已全量兼容 `Authorization: Bearer <token>`。
   - 新增端点 `POST /api/auth/mobile/token`：支持手机验证码和邮箱密码双模登录，成功后签发有效期 30 天的 JWT Token。
   - 新增端点 `POST /api/auth/sms/login`：已支持未注册手机号自动注册，并返回移动端 Token。
2. **语音评测 (REST)**：
   - 新增端点 `POST /api/speech/evaluate`：接受 JSON `{ episodeId, subtitleId, targetText, audioBase64, rate }`。
   - 已打通有道 ISE 验签、OSS 保存及用户发音弱项 (`phonemeStats`) 更新。Android 端只需录制 16kHz mono PCM 转 base64 提交即可。
3. **OSS 大文件直传**：
   - 新增端点 `POST /api/oss/presign`：获取上传音频/头像的临时 PUT URL。

---

## 2. 当前开发进度 (Progress)

- [x] **Phase 1: 后端前置改造** (✅ 完成)
- [x] **Phase 2 - M0 启动准备: 工程初始化与基建配置** (✅ 完成)
  - 已使用 Android CLI 在 `yuanlu-android` 目录下生成 Jetpack Compose 空工程。
  - 引入了 Hilt, Media3, Retrofit, Room, Coil, DataStore 全家桶依赖，配置了 DaisyUI 主题。
- [x] **Phase 2 - M1 认证闭环** (✅ 完成)
  - 实现了基于 DataStore 的 TokenStore 持久化。
  - 封装了 OkHttp `AuthInterceptor`，拦截注入 Bearer 并在 `401` 时触发登出清理。
  - 画了双模的 Compose LoginUI。
- [x] **Phase 2 - M2 播放与字幕核心** (✅ 完成)
  - 接入了 Media3 `PlaybackService` 实现了系统级后台播放与单例管控。
  - 在 `PlayerViewModel` 中实现了 200ms 级的高频播放进度拉取。
  - 完成了双语字幕数组的时间戳二分对齐，并用 Compose `LazyList` 实现了当前句的高亮自动平滑滚动。
- [x] **Phase 2 - M3 发现与内容浏览** (✅ 完成 2026-08-27)
  - **数据层**：新增 `ContentApi`/`ContentRepository`，端点契约以后端 `yuanlu` 仓库路由源码为准——`episode/list`(裸数组+page/pageSize 分页)、`episode/detail`(含 userState)、`episode/subtitles`(字幕+签名 audioUrl)、`episode/list-by-podcastid`(信封+hasMore 分页)、`podcast/list`、`podcast/detail`(含 channelPodcasts)、`podcast/search`、`tag/list`、`channel/[name]`。`Subtitle` 模型字段对齐真实线格式 `start/end/word`（原计划文档中的 `startSeconds` 系笔误）。
  - **首页**：编辑精选横滑 + 最新剧集分页列表（滚动到底自动翻页，不足一页判定 endReached）。
  - **发现**：播客搜索（400ms 防抖）、分类标签筛选、频道入口（platform 聚合）、两列播客网格。
  - **播客详情**：头部信息 + 剧集分页列表（最新/最早排序切换）+ 同频道播客推荐；剧集行含收听进度条与 PRO 徽标。
  - **频道页**：Top Shows 网格 + Top Episodes 列表。
  - **播放器接真实数据**：`episode/subtitles` 一次性取归一化字幕与签名音频直链；未登录/无权限展示 3 分钟预览 + 登录引导；断点续播（跳过 <30s、接近结尾重播）；UI 升级为封面头部 + Slider 进度 + ±10/30s + 播放/暂停。
  - **导航与壳**：Navigation3 类型化路由（`PodcastDetailNav`/`ChannelNav`/`PlayerNav`），`rememberViewModelStoreNavEntryDecorator` 提供 entry 级 ViewModel 作用域；登录门禁由 `TokenStore.tokenFlow` 三态驱动（未决/已登录/未登录），冷启动不闪登录页；底部 Tab（Home/Discover/Me），Me 页含登出入口（M9 完整实现）。
  - **构建链修复**：工作区曾以 `android.builtInKotlin=false` 绕开 AGP 9 与 kapt 的冲突，导致 Hilt 注入失败。本次完成正式迁移：移除 KGP `kotlin-android` 插件（用 AGP 9 内置 Kotlin 2.2.10）、**kapt → KSP**（`2.2.10-2.0.2`）、Hilt 升至 `2.59.2`、补 `material-icons-core` 显式依赖（新版 material3 不再传递）。`gradle.properties` 新增 `android.disallowKotlinSourceSets=false`（KSP 以旧 DSL 注册生成源码，官方豁免开关）。
- [ ] **Phase 2 - M4及以后**: 见下方详细开发计划。

---

## 3. 原始开发计划 (Original ANDROID.md)

以下为最原始的 Android 全量功能规划与技术选型：

## 0. 执行摘要 (TL;DR)

本计划交付一个**全功能对标 Web** 的 Android 应用，采用 **Kotlin + Jetpack Compose + Media3 + 单模块 Clean 架构**。核心难点不在 UI，而在 **移动端认证改造、音频后台播放与字幕同步、16kHz PCM 录音与有道 ISE 评测链路、OSS 大文件上传** 四个方向。

预计周期：**约 12–14 周**（详见 §10 里程碑），单人全职；含后端协同改造。

---

## 1. 目标与原则

### 1.1 产品目标
- **功能对标**：覆盖 Web 端用户侧（USER/PREMIUM）全部核心场景；管理后台（ADMIN）暂**不在** App 范围（维持 Web 端）。
- **体验超越**：发挥原生优势——后台播放、系统媒体控件（锁屏/通知/车载/蓝牙）、离线缓存、流畅字幕滚动、PCM 录音波形。
- **单一后端**：App 与 Web 共用同一套 Next.js API + 数据库 + OSS，**不重建后端**，仅做必要的移动端适配改造。

### 1.2 设计原则
- **渐进增强**：先跑通「登录 → 播放 → 字幕」核心闭环，再叠加评测、词汇、统计。
- **后端契约优先**：凡 Web 端用 Server Action / cookie 实现的链路，先与后端约定 REST 契约，再写客户端。
- **类型安全**：Kotlin data class 与后端 Prisma 模型一一映射；网络响应严格建模（见 §6.3 应对后端响应格式不统一）。
- **关注点分离**：UI（Compose）→ ViewModel（状态）→ Repository（数据编排）→ DataSource（Retrofit/Room/OSS）。

---

## 2. 技术选型

| 层 | 技术 | 版本（建议） | 选型理由 |
|---|---|---|---|
| 语言 | Kotlin | 2.0+ | 协程、密封类、空安全；Compose 编译器首选 |
| UI | Jetpack Compose + Material 3 | Compose BOM 2024.x | 声明式 UI，对齐 Web 的 Tailwind/DaisyUI 视觉；动画用 `AnimatedVisibility` |
| 最低 SDK | **minSdk 26** (Android 8.0) | — | 覆盖 95%+ 设备；Media3 / 前台服务 / 通知通道 API 稳定 |
| 目标 SDK | targetSdk 34 / 35 | — | 合规前沿；适配 13+ 通知权限与 14+ 前台服务类型 |
| 架构 | Clean Architecture (MVVM) | — | `presentation / domain / data` 三层；单 Gradle 模块起步 |
| DI | Hilt (Dagger) | 2.51+ | 官方推荐，与 ViewModel/Compose 集成完善 |
| 异步 | Coroutines + Flow | 1.8+ | 全链路异步；`StateFlow` 驱动 Compose |
| 网络 | Retrofit 2 + OkHttp + kotlinx.serialization | 2.11 / 4.12 | 比 Gson 更安全；与 Kotlin 协程契合 |
| 认证 | DataStore (Proto) + EncryptedSharedPreferences | — | Token 加密持久化；`OkHttp Interceptor` 注入 Bearer |
| 数据库 | Room | 2.6+ | 离线缓存（字幕、历史、词汇、下载音频索引） |
| 媒体播放 | **Media3 ExoPlayer + MediaSessionService** | 1.4+ | 系统级后台播放、锁屏控件、蓝牙/车载、队列管理 |
| 音频录制 | AudioRecord (16kHz mono PCM) + 自写 WAV header | — | 复刻 Web 端 `encodeWAV`，保真度匹配 |
| 图片 | Coil 3 (Compose) | 3.0+ | 自动处理 OSS 签名 URL 过期 |
| 导航 | Navigation Compose (Type-Safe) | 2.7+ | 类型安全路由 |
| 图表 | Vico 或 Compose 自绘 | — | 对标 Web 的 recharts（学习统计、发音雷达图） |
| 测试 | JUnit4 + Turbine + MockK + Compose UI Test | — | Flow/ViewModel/Composable 分层测试 |
| 构建 | Gradle KTS + Version Catalog (libs.versions.toml) | Gradle 8.x | 依赖集中管理 |

---

## 4. 应用架构

### 4.1 分层结构（单 Gradle 模块，包内分层）

```text
com.wxkzd.yuanlu
├── app/                        # Application, Hilt 入口, MainActivity
├── core/
│   ├── network/                # Retrofit, OkHttp, Interceptor, 网络错误模型
│   ├── auth/                   # Token 存储, AuthInterceptor, 401 处理
│   ├── database/               # Room DB, DAO
│   ├── datastore/              # 用户偏好 (主题/播放设置/语言)
│   ├── media/                  # Media3 Player 单例, MediaSessionService, 队列管理
│   ├── recorder/               # AudioRecord + WAV 编码器 (16kHz mono)
│   ├── oss/                    # OSS 直传客户端 (presigned PUT)
│   └── designsystem/           # 主题 (对齐 DaisyUI), 复用 Composable
├── data/
│   ├── remote/                 # Retrofit ApiService 接口 + DTO
│   ├── repository/             # Repository 实现 (编排 remote/room/oss)
│   └── mapper/                 # DTO <-> Domain Entity
├── domain/
│   ├── model/                  # 纯领域模型 (Episode, Podcast, Subtitle, Vocab...)
│   ├── repository/             # Repository 接口
│   └── usecase/                # 用例 (PlayEpisode, EvaluateSpeech, ToggleFavorite...)
└── feature/
    ├── auth/                   # 登录/注册/验证码
    ├── home/                   # 首页/推荐
    ├── discover/               # 发现/频道/热门/分类
    ├── podcast/                # 播客详情 + 剧集列表
    ├── episode/                # 剧集详情 + 播放页 + 字幕
    ├── player/                 # 全屏播放器 + 字幕 + 练习面板 (Compose)
    ├── library/                # 收藏/历史/学习路径/词汇/发音弱项本
    ├── voice/                  # 语音评测 UI + 音素可视化 + 波形
    ├── vocabulary/             # 词汇查词 (有道词典) + 复习
    ├── comment/                # 评论/点赞/举报
    ├── notification/           # 通知中心
    ├── stats/                  # 统计/成就/发音诊断雷达图
    ├── profile/                # 个人中心/设置/订阅状态
    └── components/             # 跨 feature 复用组件
```

### 4.2 数据流向（单向）

```text
Compose UI ──事件──▶ ViewModel (StateFlow<UiState>)
                         │ 调用
                         ▼
                     UseCase
                         │
                         ▼
                  Repository (接口, domain 层)
                         │ 实现 (data 层)
              ┌──────────┼──────────┐
              ▼          ▼          ▼
          Retrofit     Room       OSS Direct
          (远程)      (缓存)      (大文件)
```

### 4.3 全局状态
- **播放状态**：单一 `MediaController`（绑定 `MediaSessionService`），用 `Flow` 暴露 `currentEpisode / isPlaying / position / duration / playbackRate / repeatMode`。
- **认证状态**：`SessionState`（登录态、用户信息、角色），驱动 `PREMIUM` 权限门控与付费提示。
- **主题**：DataStore 持久化 dark/light，对齐 Web 的 `data-theme`。

---

## 5. 数据模型映射（Prisma → Kotlin）

> 仅列核心模型字段。所有 `DateTime` → `Instant`/`Long(ms)`，`String?` → `String?`，`Json` → 对应嵌套 data class。

### 5.1 用户与认证
```kotlin
data class User(
    val userid: String,
    val email: String,
    val phone: String?,
    val role: String,          // USER | PREMIUM | ADMIN
    val isLoginAllowed: Boolean,
    val loginCount: Int,
)
data class UserProfile(
    val userid: String,
    val nickname: String?,
    val avatarUrl: String?,
    val avatarFileName: String?,
    val bio: String?,
    val learnLevel: String?,
    val dailyStudyGoalMins: Int = 30,
    val phonemeStats: PhonemeStats? = null,   // 发音诊断聚合
    val weakScoreThreshold: Int = 80,
)
```

### 5.2 播客与剧集
```kotlin
data class Podcast(
    val podcastid: String,
    val title: String,
    val coverUrl: String,
    val description: String?,
    val followerCount: Int,
    val totalPlays: Int,
    val isEditorPick: Boolean?,
    val tags: List<Tag>?,
)
data class Episode(
    val episodeid: String,
    val title: String,
    val coverUrl: String,
    val description: String?,
    val audioUrl: String,          // OSS 签名 GET URL (1-3h)
    val audioFileName: String?,
    val duration: Int,
    val playCount: Int,
    val difficulty: String?,      // General / ...
    val status: String?,          // unpublished / published
    val isExclusive: Boolean?,    // Premium 门槛
    val isCommentEnabled: Boolean?,
    val podcastid: String?,
    val publishAt: Instant,
    val userState: EpisodeUserState? = null,
)
data class EpisodeUserState(       // 对标 /api/episode/[id] 的 userState
    val progressSeconds: Float,
    val isFinished: Boolean,
    val lastListenAt: Instant?,
    val isFavorited: Boolean,
)
```

### 5.3 字幕（核心）
```kotlin
// 双语 JSON (最丰富，含词级时间戳)
data class Subtitle(
    val id: Int,
    val textEn: String,
    val textCn: String?,           // 可能为空 (纯英文)
    val startSeconds: Double,
    val endSeconds: Double,
    val speaker: String? = null,
    val words: List<SubtitleWord>? = null,   // 词级高亮
)
data class SubtitleWord(
    val text: String,
    val start: Double,
    val end: Double,
)
```
> ⚠️ 移动端**无需关心合并逻辑**——统一消费 `/api/episode/subtitles?id=` 返回的归一化 `Subtitle[]`。

### 5.4 语音评测
```kotlin
data class SpeechResult(
    val recognitionid: Int,
    val episodeid: String?,
    val subtitleId: Int?,
    val targetText: String,
    val speechText: String?,
    val accuracyScore: Float?, val fluencyScore: Float?,
    val integrityScore: Float?, val overallScore: Float?,
    val speed: Float?,
    val userAudioUrl: String?,     // 回放用户录音
    val detailUrl: String?,        // 音素级 detail JSON
    val recognitionDate: Instant,
)
data class PhonemeDetail(          // 解析 detailUrl JSON: words[].phonemes[]
    val word: String,
    val phonemes: List<Phoneme>,
)
data class Phoneme(val ipa: String, val score: Float)
```

---

## 6. 网络层设计

### 6.1 基础设施
- `OkHttpClient`：连接池、超时（默认 30s；评测/上传接口配置 60-120s）、自定义 `AuthInterceptor`。
- `Retrofit`：单一实例 + 多 `ApiService` 接口；`kotlinx.serialization` 转换器。

### 6.2 认证拦截器
```kotlin
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request().newBuilder()
            .header("X-Client", "android")
            .apply { tokenStore.accessToken?.let { header("Authorization", "Bearer $it") } }
            .build()
        val resp = chain.proceed(req)
        // 401 -> 清除登录态, 发出全局登出事件, 跳登录页
        if (resp.code == 401) tokenStore.clearAndEmitUnauthorized()
        return resp
    }
}
```

### 6.3 响应解析策略（应对后端格式不统一）
由于部分后端接口返回裸数据，部分返回 `{ success, data }` 信封：

```kotlin
// 通用扩展: 自动嗅探 success/data 信封 vs 裸数据
suspend inline fun <reified T> ApiResponse<T>.unwrap(): Result<T> {
    return if (body.has("success") && body.has("data"))
        Result.success(body["data"]!!.decode())   // 信封
    else
        Result.success(body.decode<T>())           // 裸数据
}
```

### 6.4 错误模型
```kotlin
sealed class ApiError {
    data class Http(val code: Int, val msg: String)  // 400/401/403/500
    object Network : ApiError()                       // 无网络
    data class Parse(val t: Throwable) : ApiError()
    // 业务语义: 403 -> Premium required (弹订阅引导)
    val isPremiumRequired get() = this is Http && code == 403
}
```

---

## 7. 核心技术难点与方案

### 7.1 全局音频播放器
**选型**：Media3 `MediaSessionService` + 单一 `ExoPlayer` 实例（Application 范围单例）。
**音频源决策（重要）**：
- **直接用 OSS 签名 GET URL 作为 `MediaItem`**，**不走** `/api/episode/audio-proxy`，避免代理流式 body 与 Range seek 的冲突。

### 7.2 字幕同步与交互
**同步算法**（纯客户端，与 Web 一致）：
- 拉取一次 `GET /api/episode/subtitles?id=`。
- 用 `player.position` Flow（200ms 节流）线性扫描定位 `activeIndex`。
- **词级高亮**：在 `Subtitle.words` 内定位 `word.start <= posSec <= word.end`。

### 7.3 图片加载与签名 URL（Coil）
- 封面/头像是 OSS 签名 URL（1-3h 过期）。Coil 默认按 URL 作 cache key 会导致过期后命中失败。
- 方案：自定义 Coil `keyer`，以 `fileName` 而非完整 URL 作缓存 key。

### 7.4 语音评测链路
- **录音**：`AudioRecord`（`16000`, `CHANNEL_IN_MONO`, `ENCODING_PCM_16BIT`）。停止后拼接 44 字节 WAV header，转 Base64 上传。
- **评测请求**：调用 `POST /api/speech/evaluate` 新接口。

---

## 8. 开发里程碑（约 12–14 周）

### M0 — 启动准备（~3 天）✅
- 仓库初始化、包名设定 (`com.wxkzd.yuanlu`)。
- Gradle/Version Catalog、Hilt/Compose/Media3 接入、主题对齐 DaisyUI、导航骨架。

### M1 — 认证闭环 ★（~1 周）✅
- 客户端：登录（手机/邮箱）、Token 拦截器、持久化、401 全局处理、登出。
- **交付**：可登录、保持登录态、调通需要鉴权的接口。

### M2 — 播放与字幕核心闭环 ★（~2 周）·**核心** ✅
- Media3 Service + 前台通知 + 队列/循环/倍速。
- 剧集详情、OSS 签名 URL 播放、**字幕同步 + 词级高亮 + 点击跳转**、断点续传。

### M3 — 发现与内容浏览（~1.5 周）✅
- 首页、发现、频道、分类、搜索、播客详情、剧集列表、分页。

### M4 — 收藏 / 历史 / 学习路径（~1 周）
- 收藏/取消、历史、学习路径 CRUD，与播放联动。

### M5 — 词汇与查词（~1 周）
- 词汇增删查、有道词典展示、间隔重复复习。

### M6 — 语音评测 ★（~2 周）
- 16kHz PCM 录音 + WAV 编码、`/api/speech/evaluate` 调用、多维分数卡、音素级纠错、历史对比。

### M7 — 发音弱项本 + 诊断报告（~1 周）
- `/api/speech/errors`、`/api/speech/practice-data`、雷达图诊断。

### M8 — 评论 + 通知 + 统计 + 成就（~1.5 周）
- 评论楼中楼、通知中心、统计图表、成就墙。

### M9 — 个人中心 + 订阅 + 头像上传（~1 周）
- 资料/设置/订阅状态/头像直传 (OSS presign)/注销。

### M10 — 离线下载 + 性能打磨（~1 周）
- WorkManager 下载、内存/启动优化、深色模式、无障碍。

### M11 — 测试 + 灰度 + 上架（~1 周）
- 全量回归、内测分发、应用商店素材、隐私政策。
