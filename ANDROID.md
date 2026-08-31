# 远路播客 Android App 开发计划与上下文 (Context & Plan)

> **当前状态**: Phase 2 播放流程与精听联动闭环已完成（迷你播放条/全屏播放器/精听页），M4 待启动
> **同步日期**: 2026-08-28
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
- [x] **Phase 2 - M3.5 发现页 UI 重设计 + 品牌主题同步** (✅ 完成 2026-08-28，模拟器实测通过)
  - **品牌主题**：`theme/Color.kt` 全量对齐 Web 端（`yuanlu/app/globals.css`）——远青 primary `#1F7A5C`/深色 `#4DA989`、暖纸底 `#FAF8F3`/深色墨 `#151310`、曙光橙 secondary `#D98A17`，`Theme.kt` 增补 container/outline/error 全套映射，深色模式像素级验证通过。
  - **后端小幅扩展（向后兼容）**：`yuanlu` 仓库 `app/api/podcast/list/route.ts` 的 select 补 `totalPlays/followerCount/createAt/_count.episode`（扁平化为 `episodeCount`），支撑热门榜/新节目/频道聚合的客户端派生。
  - **发现页重写**：区块结构对齐 Web 发现页——`Trending`（横滑排名卡，金银铜墨徽章 + 收听量）→ `Editor's Picks`（分类 pill）→ `New Shows`（NEW 徽章）→ `Channels`（品牌色频道卡，platform 聚合 + 播客数）→ 分类 FilterChip → 两列播客网格；搜索框升级 28dp 胶囊（BasicTextField）；加载态改 Shimmer 骨架屏（纯 Compose 无新依赖）；空区块自动折叠。
  - **共享组件升级**（首页/频道页同步受益）：`CoverImage` 圆角参数化、`PodcastCard` 增平台眉标（大写+字距）/徽章/pill/描述槽位、`SectionHeader` 支持 action、新增 `ShimmerBox`/`EyebrowText`/`formatPlays`。
  - **ViewModel**：`DiscoverUiState` 新增 `trending`(totalPlays 降序 TOP10)/`editorPicks`/`newPodcasts`(createAt 降序前 8)/`channels`(`ChannelEntry(name, count)`) 区块字段，一次拉取客户端派生；补 `DiscoverViewModelTest` 4 例（区块派生/截断/空折叠/标签）。
  - **交互打磨（实测反馈修复）**：排名徽章金色压深 `#DAA520` + 银色调暗 + 全徽章 1dp 描边（白底封面辨识度）；搜索态系统返回键先清空回浏览态（`BackHandler`，不再直接退出应用），清空输入时即时退出搜索态不等防抖。
  - **实测环境备注**：本机模拟器（Medium_Phone, 1080x2400）+ `yuanlu` dev 服务（3000 端口，模拟器经 `10.0.2.2` 访问）联调验证；后端建有一个一次性测试账号 `android-preview@test.yuanlu.com` / `Yuanlu2026!`（不需要可删）。
- [x] **Phase 2 - M3.6 游客模式 + 登录弹层 + 我的页** (✅ 完成 2026-08-28，模拟器端到端实测通过)
  - **架构：全屏门禁 → 游客模式**（对齐 Web `routes.ts` 公开/受限路由）：未登录直接进主界面，发现/播客详情/频道公开可浏览；401 过期自动降级游客视图。底部 Tab 扩为 4 个（首页/发现/生词本/我的，对齐 Web MobileBottomNav）。
  - **「立即登录」引导页** `LoginGateScreen`（对齐 Web `PodcastAuthPrompt`）：自绘麦克风+声波 ImageVector 插画、文案「跟上您的节目」、胶囊按钮按压 scale 动画；文案参数化供首页/生词本复用（生词本场景「构建你的生词本」）。
  - **登录弹层** `LoginSheet`（ModalBottomSheet，对齐 Web `EmailCheckDialog`）：「欢迎来到远路播客/请选择登录方式」+ 双 Tab（手机号/邮箱）+ 验证码「获取验证码」按钮（`POST api/auth/sms/send` + 60s 倒计时；阿里云滑块风控降级提示改用邮箱登录）+ 协议勾选（未勾选禁用提交，已实测）+ 胶囊主按钮。登录成功由 tokenFlow 驱动自动收起，弹层全局挂载于 `AppViewModel`（对齐 Web 全局 ModalProvider）。
  - **协议全文**：用户协议/隐私政策完整复用 Web 端文本（结构化 Kotlin `AgreementContent.kt`），登录层内链接点开全屏 `AgreementDialog`；后端协议更新时需同步该文件。
  - **「我的」页重写**（对齐 Web `/auth/mine`）：用户卡（64dp 头像/昵称/角色 badge 管理员=远青·高级会员=曙光橙·普通用户=灰/email；未登录态引导卡）+「学习与记录」菜单组（弱项本/学习路径/历史/收藏，仅登录显示，点击 Snackbar「即将上线」）+「账户与系统设置」（个人中心/我的订阅/外观设置/消息通知/帮助与支持；控制台仅 ADMIN）+ 退出登录（token 清空自动回游客态）+ Shimmer 骨架。
  - **主题三态**：跟随系统/浅色/深色（`settings_prefs` DataStore + `SettingsStore` + `MainActivity` 接线），深色即时生效已实测；新增依赖 `material-icons-extended`（菜单图标，release 由 R8 裁剪）。
  - **数据层扩展**：`AuthApi` +`POST api/auth/sms/send`（业务失败也 200，`requireCaptcha` 标识风控）、+`GET api/user/profile`；`AuthRepository` +`sendSmsCode()`/`getProfile()`；`UserProfile` 领域模型。
  - **🚨 后端缺口修复（yuanlu 仓库）**：`app/api/user/profile/route.ts` GET 原用裸 `auth()` 不认移动端 Bearer Token（实测 401），已改走 `requireAuth()`（Cookie 优先、Bearer 兜底）。**教训：后端移动端 Bearer 兼容仅覆盖接入 `core/auth/guard.ts` 的端点，新增会话态接口时必须核对此点**。
  - **播放页游客引导**：`LoginRequired` 提示条中文化 +「立即登录」入口弹登录层；游客实测可看播客详情与前 3 分钟双语字幕。
  - **验证**：单测 17/17（新增 LoginViewModelTest 5 例：手机号校验/倒计时/风控降级/空值拦截/reset；ProfileViewModelTest 4 例：资料加载/游客清空/登出/错误重试）；模拟器 E2E：游客四 Tab 分流 → 引导页 → 登录弹层（含协议门禁/全文查看）→ 邮箱登录成功自动收起 → 我的页资料 → 深色切换 → 登出回游客态 → 游客播放页引导。
- [x] **Phase 2 - M3.7 全局中文化与 UI 细节对齐** (✅ 完成 2026-08-28)
  - **全局文案中文化**：全面梳理了 `HomeScreen`、`DiscoverScreen`、`PodcastDetailScreen`、`ChannelScreen`、`PlayerScreen` 以及所有通用组件和 ViewModel/Repository 的错误提示，将所有硬编码的英文文本（如 "Editor's Picks"、"No episodes yet"、"Network connection failed" 等）统一翻译为简体中文，确保与 Web 端的本地化一致。
  - **图标与插画对齐**：底部导航栏的“发现”图标从放大镜（`Icons.Filled.Search`）更改为指南针（`Icons.Filled.Explore`）；完全重写了 `LoginGateScreen` 中的 `PodcastSignal` 插画，废弃有填充渲染缺陷的 `materialIcon`，改用底层的 `ImageVector.Builder` 并显式声明 `SolidColor` 和 `PathFillType.EvenOdd`，完美 1:1 复刻 Web 端的播客信号 SVG。
  - **布局与导航优化**：
    - 去除所有二级页面（如播客详情、播放器、频道详情、协议弹窗）的顶部返回箭头按钮，统一使用系统手势或边缘返回。
    - 重构了“发现”页面的布局，去除顶部的“发现”标题和搜索模块，将推荐频道、新播客等模块改为两列三行的网格布局。
    - 在“发现”页面的推荐频道模块添加了“查看更多”功能，点击跳转至全新的“全部频道”页面。
    - 对齐 Web 端的频道卡片设计：频道卡片统一正方形比例，文本水平居中溢出截断；移除了卡片中的“播放”图标，并完美复刻了 Web 端的“频道主页”（Computer 图标）按钮。
- [x] **Phase 2 - M4 前置: 播放流程与精听联动闭环** (✅ 完成 2026-08-30，编译 + 17 单测通过)
  - **交互闭环**：剧集详情页「开始精听」→ 起播（精听模式）+ 全局底部弹出迷你播放条 → 点击迷你条平滑展开全屏播放器 → 全屏播放器「精听模式」按钮携带 `episodeId + playbackPosition` 导航至精听页（双语字幕同步高亮/自动滚动/点击跳播）。
  - **状态层**：`PlayerState` 新增 `isIntensiveMode`；`PlayerController.play(episode, startPositionMs, intensive)` 起播时置位，`setIntensiveMode()` 可后补标记；普通起播自动复位。迷你条可见性由全局共享的 `PlayerShellViewModel`（Activity 作用域，挂在 AppNavHost）从单例 `PlayerController.playerState` 派生（`currentEpisode != null`），导航切换间天然一致。
  - **新增文件**：`feature/player/PlayerShellViewModel.kt`（全局播放壳）、`MiniPlayerBar.kt`（迷你条：进度线/封面/标题/精听徽标/播放暂停）、`FullScreenPlayerScreen.kt`（沉浸式全屏播放器：封面/Slider/±10·30s/播放暂停/精听按钮）、`PlayerPalette.kt`（播放器系共用品牌色板）；`feature/intensive/IntensiveListeningScreen.kt` + `IntensiveListeningViewModel.kt`（精听页，复用全局 PlayerController，已在播则接续进度仅补精听标记，未播则按携带进度/服务端断点以精听模式起播）。
  - **路由**：新增 `IntensiveListeningNav(episodeid, positionMs)`；`Navigation.kt` 用 Box 叠加 NavDisplay，迷你条（AnimatedVisibility spring 底部滑入，Main Tab 时抬升 80dp 悬浮于底部导航之上）与全屏播放器（spring 弹入沉浸层）均为全局浮层；进入精听页/展开全屏时自动收起迷你条避免叠底；全屏播放器 BackHandler 支持系统返回收起。
  - **行为变更**：详情页「开始精听」不再自动弹文稿层（改为起播 + 迷你条联动）；游客/无音频直链时退回打开文稿弹层做 3 分钟预览（原 Web 对齐口径保留）。手动「文稿」按钮不受影响。
  - **无障碍与反馈**：精听按钮（曙光橙胶囊 + GraphicEq 图标）带 Ripple、`contentDescription="精听模式"`、`onClickLabel="进入精听模式"`；播放/暂停/±10·30s/收起等图标均有中文无障碍标签。
  - **验证**：`compileDebugKotlin` / `assembleDebug` / `testDebugUnitTest`（17/17）全部通过；单测未覆盖播放链路（`PlayerController` 依赖 ExoPlayer Android 框架类，与 `PlayerViewModel` 既有口径一致，依赖模拟器 E2E 实测）。
- [x] **Phase 2 - M4 前置 II: 迷你条/全屏播放器 Web 移动端复刻** (✅ 完成 2026-08-30，编译 + 17 单测通过)
  - **迷你播放条 1:1 复刻 Web `MobilePlayerBar.tsx`**：通栏贴边（左右 0 外边距、上缘 2dp 远青渐变进度线 + border-t）；60dp 内容行 = 40dp 封面（播放中叠加 4 柱均衡器动效，复刻 tailwind `animate-eq`）+ 标题/播客名 + 40dp 远青播放圆钮（按压 0.9 缩放）+ **关闭图标**（停止播放并隐藏浮条，对齐 Web `closePlayer`）。Main Tab 紧贴底部导航上缘（无侧边距），其余页面贴屏幕底（背景铺满手势区，内容 `navigationBarsPadding`）；深浅色随外观设置切换（浅 white/95·ink-100 边框 / 深 ink-900/95·ink-800）。
  - **全屏播放器复刻 Web `MobilePlayerSheet.tsx` 展开式播放器**：顶部窄栏（expand_more 收起 / close 关闭停止）+ 拖把；**16:9 封面**（最大宽 320dp，点击跳剧集详情，精听中带角标）+ 居中标题/播客名；自绘进度条（6dp 圆角轨道 + 16dp 白芯描边拖把，支持点按/拖动，双端显示 当前/-剩余）；控制排 = **倍速循环切换按钮（1/1.25/1.5/2/0.75x，对齐 `cyclePlaybackRate`）** + 上一集（重播本集）+ 64dp 播放大圆钮（缓冲转圈）+ 下一集（队列占位提示）+ 循环模式（不循环/列表/单曲，映射 Media3 repeatMode）；精听橙色胶囊入口保留。深浅色随外观设置切换（浅 base-100 / 深 ink-950）。
  - **状态层扩展**：`PlayerState` +`playbackRate`/`loopMode`（`LoopMode` 枚举）；`PlayerController` +`stop()`（复位并隐藏浮条）/`setPlaybackRate`/`cyclePlaybackRate`/`toggleLoopMode`；`PlayerShellViewModel` 同步透出。深浅色判定统一走 `isDarkAppearance()`（由 colorScheme.background 亮度反推，外观设置三态即时生效）。
- [x] **Phase 2 - M4 前置 III: 全屏播放器布局改版 + 定时关闭 + 详情页深色修复** (✅ 完成 2026-08-30，编译 + 17 单测通过)
  - **全屏播放器布局改版（参照用户截图）**：页面上部改为弹性留白区（将来互动讨论评论在此滚动展示），封面（16:9）/标题及作者/进度条/控制按钮群整个模块下沉；进度条正上方新增一行——居左「播放列表」图标（队列占位提示）、居右「定时关闭」闹钟图标（激活时远青高亮并显示剩余描述）；「精听模式」橙色胶囊固定页面最底部；风格保持 yuanlu Web 移动端（浅 base-100 / 深 ink-950 + 远青主色，外观设置三态即时生效）。
  - **定时关闭（截图2 复刻）**：`PlayerState` +`sleepTimer`/`lastSleepConfig`；`PlayerController` +`applySleepConfig(SleepConfig)`（按时间=协程倒计时到点暂停 / 按集数=STATE_ENDED 结算递减 / 播完本集）与 `cancelSleepTimer()`。点击闹钟弹出 ModalBottomSheet：「上次定时 + Switch（重开/取消）」→ 按时间（播完整集声音再停止 + 15/30/60/90分/自定义占位）→ 按集数（本集/2/3/5集）→ 设置定时启播（占位）。迷你条/全屏播放器闹钟图标实时显示定时状态。
  - **剧集详情页深色模式 BUG 修复**：`PlayerScreen` 原硬编码浅色 ink 阶梯，深色模式下页面仍为浅底浅字。现全部中性色经 `pageBg()/dividerColor()/titleTextColor()` 等 @Composable 取值函数按 `isDarkAppearance()` 切换（背景 ink-950、分隔 ink-800、文字 ink-50/300/400、主色 primary-400），文稿弹层底色同步适配；品牌绿/橙 CTA 与封面上角标保持不变。
- [x] **Phase 2 - M4 前置 IV: 精听页 Web 复刻（句/词级高亮 + 循环体系）** (✅ 完成 2026-08-30，编译 + 17 单测通过)
  - **三级高亮（复刻 Web InteractiveTranscript/SubtitleItem）**：当前句 = primary-50 卡片 + 左侧 3dp primary-500 竖线 + 粗体主题色文字；已读句前景色加深（ink-800 / dark ink-200）；未读句淡化（ink-400 / dark ink-500）；正在读的单词曙光橙背景高亮（`buildAnnotatedString` + `SubtitleWord.start/end` 词级时间戳，浅 accent-100 / 深 accent-900-40%），仅当前句播放中生效。
  - **循环体系**：①单句循环——当前句右下角 repeat/repeat_one 小按钮锁定该句，ViewModel 收集播放位置，锁定句越过 end 即 seek 回 start（保留 500ms 手动跳播保护，对齐 Web lastJumpTimeRef 口径）；②单集循环 ↔ 多集顺序播放——右下角浮动按钮切换（`PlayerController.setLoopMode`，REPEAT_ONE/REPEAT_OFF，Toast 提示当前模式）。
  - **右下角浮动按钮列**（对齐 Web quick actions：半透明底 + 阴影 + 激活态主题色）：翻译开关（显示/隐藏中文译文，AnimatedVisibility 折叠动画，默认隐藏）+ 循环模式切换；底部抬高 76dp 避让迷你条。
  - **底部播放器替换**：移除精听页内自绘播放控制条，由全局迷你播放条接管（Navigation 不再对 IntensiveListeningNav 隐藏迷你条），列表底部预留 88dp。
  - **深色模式适配**：整页（背景 ink-50/ink-950、顶栏、句/词高亮、浮动按钮）按 `isDarkAppearance()` 切换。
  - 顶栏对齐 Web 精听头部形态（下箭头返回 / 居中「精听模式」标题 / × 关闭）；游客 3 分钟预览横幅保留。
  - **实测反馈修复（4 项）**：①当前句内已读词补 accent 前景色高亮（accent-700/dark accent-300，对齐 Web `useWordHighlight` 已读词字体色，正在读词保留背景光斑）；②滚动逻辑复刻 Web `useTranscriptScroll`——当前句滚到视口 30% 处停住，仅越过上安全区（<120dp）或下安全区（>65% 视口高）才滚动，不再恒定顶格；③浮动按钮/列表底距改为 `navigationBars inset + 63dp 迷你条高 + 间距` 动态避让（手势/三键导航均适配）；④当前句移除左侧 3dp 竖线，仅保留 primary-50 卡片底。
- [x] **Phase 2 - M4 前置 V: 精听页查词保存生词 + 精读/听写模式** (✅ 完成 2026-08-30，编译 + 23 单测通过)
  - **点词查词与生词本（复刻 Web VocabularyModal/handleWordClick/handleSaveVocabulary）**：词级点击（annotated string + tap 偏移定位词区间）→ 暂停播放 → 底部弹层：单词 + 收藏/关闭、英美音标与发音（MediaPlayer 播 OSS 音频，弹层关闭释放）、词性释义卡、词源记忆卡（前缀/词根/后缀 chips + 拆解 + 记忆技巧，默认收缩）、底栏「来源：剧集 + 完成学习 →」。保存走 POST api/vocabulary/add（definition 由释义拼装、携带上下文句/译文/时间戳/发音 URL），已保存态由 GET api/vocabulary/words 懒加载缓存；游客保存给「请先登录」提示，403/400 映射配额与重复收藏文案。
  - **精读/听写模式 Tab（复刻 Web MobilePlayerSheet 头部）**：顶栏居中「📖 精读 | ✍️ 听写」胶囊分段。听写模式：倍速降 0.8（切回精读恢复 1.0，对齐 Web）、当前句自动循环（loopTarget 口径）、当前句渲染 DictationRow——逐词槽位（已对 primary 粗体 / 输满且错红色删除线 / 待填虚线下划槽，错 3 次显示提示，标点词自动通过）、透明 BasicTextField 覆盖捕获键入（去空格按清洗长度切块，对齐 Web inputWords 算法）、整句正确自动跳下一句（末句暂停）、键盘 Done 错误计数；非当前句淡化展示可点击跳播。
  - **数据层**：`DictEntry/DictDefinition/DictEtymology` 领域模型 + DTO（snake_case @SerialName）+ `ContentApi`（dict/{word}、vocabulary/add、vocabulary/words）+ Repository 三方法（含 401/403/429/400 文案映射）。
  - **🚨 后端 Bearer 兼容（yuanlu 仓库，M3.6 同款教训）**：`guard.ts` 新增 `authWithMobile()`（cookie 优先、Bearer 兜底、允许匿名）；`api/dict/[word]` 两处 `auth()` → `authWithMobile()`（匿名缓存查询不受影响、登录用户配额正确归属）；`api/vocabulary/words` 与 `api/vocabulary/add` 改走 `requireAuth()`。
  - **验证**：`compileDebugKotlin`/`assembleDebug`/`testDebugUnitTest` 23/23（新增 `DictationUtilsTest` 6 例：清洗/分词/切块/标点占位/截断/整句判定）。
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
