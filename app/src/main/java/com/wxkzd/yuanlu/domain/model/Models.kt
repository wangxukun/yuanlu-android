package com.wxkzd.yuanlu.domain.model

data class Tag(
    val id: Int,
    val name: String
)

data class SubtitleWord(
    val word: String,
    val start: Double,
    val end: Double
)

/**
 * 与后端 /api/episode/subtitles 返回的归一化字幕一一对应：
 * start/end 为秒，words 供词级高亮（M2 已消费，M6 评测复用）。
 */
data class Subtitle(
    val id: Int,
    val textEn: String,
    val textCn: String?,
    val start: Double,
    val end: Double,
    val speaker: String? = null,
    val words: List<SubtitleWord>? = null
)

data class Episode(
    val episodeid: String,
    val title: String,
    val description: String? = null,
    val coverUrl: String? = null,
    /** 封面加载/解码失败时的回退（通常为所属播客专辑封面），由详情页解析后回填 */
    val coverFallbackUrl: String? = null,
    val audioUrl: String? = null,
    val duration: Int = 0,
    val playCount: Int = 0,
    val publishAt: String? = null,
    val status: String? = null,
    val isExclusive: Boolean = false,
    val isCommentEnabled: Boolean = true,
    val difficulty: String? = null,
    val podcastid: String? = null,
    val podcastTitle: String? = null,
    val tags: List<Tag> = emptyList(),
    val isFavorited: Boolean = false,
    val progressSeconds: Int = 0,
    val isFinished: Boolean = false
)

data class Podcast(
    val podcastid: String,
    val title: String,
    val coverUrl: String? = null,
    val description: String? = null,
    val platform: String? = null,
    val isEditorPick: Boolean = false,
    val followerCount: Int = 0,
    val totalPlays: Int = 0,
    val episodeCount: Int = 0,
    val createAt: String? = null,
    val tags: List<Tag> = emptyList()
)

data class PodcastDetail(
    val podcast: Podcast,
    val isFavorited: Boolean,
    val channelPodcasts: List<Podcast>,
    /** 全量剧集（coverUrl 已签名）：详情页用当前剧集的签名封面替换未签名的 detail coverUrl */
    val episodes: List<Episode> = emptyList()
)

data class EpisodePage(
    val episodes: List<Episode>,
    val total: Int,
    val hasMore: Boolean
)

data class ChannelData(
    val platformName: String,
    val podcastCount: Int,
    val topShows: List<Podcast>,
    val topEpisodes: List<Episode>
)

data class SubtitleBundle(
    val subtitles: List<Subtitle>,
    val audioUrl: String?
)

// ---------- 词典与生词（对齐 GET /api/dict/{word} 与 /api/vocabulary/*） ----------

/** 词性与释义 */
data class DictDefinition(
    val pos: String,
    val meaningCn: String,
    val meaningEn: String? = null
)

/** 词源记忆：前缀/词根/后缀 + 拆解 + 记忆技巧 */
data class DictEtymology(
    val prefix: String? = null,
    val root: String? = null,
    val suffix: String? = null,
    val breakdown: String? = null,
    val mnemonic: String? = null
)

/** 词典条目（取精听查词弹层所需字段，其余 LLM 字段忽略） */
data class DictEntry(
    val word: String,
    val phoneticsUk: String? = null,
    val phoneticsUs: String? = null,
    val audioUk: String? = null,
    val audioUs: String? = null,
    val definitions: List<DictDefinition> = emptyList(),
    val etymology: DictEtymology? = null
)

/**
 * 生词本条目（对齐 Web VocabularyItem：列表展示与卡片复习共用）。
 * status: LEARNING | MASTERED；nextReviewAt/addedDate 为 ISO 字符串；
 * dictEntry 为 Dictionary 表合并的词典富数据（音标/发音/释义/词源）。
 */
data class VocabularyItem(
    val vocabularyid: Int,
    val word: String,
    val definition: String? = null,
    val translation: String? = null,
    val contextSentence: String? = null,
    val proficiency: Int = 0,
    val status: String = "LEARNING",
    val nextReviewAt: String? = null,
    val addedDate: String? = null,
    val speakUrl: String? = null,
    val webUrl: String? = null,
    val timestamp: Int? = null,
    val episodeid: String? = null,
    val episodeTitle: String? = null,
    val dictEntry: DictEntry? = null
)

/** 一次复习打卡的服务端结果（POST /api/vocabulary/review） */
data class VocabularyReviewOutcome(
    val vocabularyid: Int,
    val proficiency: Int,
    val nextReviewAt: String?,
    val daysAdded: Int
)

/** 剧集评论（树形：根评论带 replies，字段对齐 /api/comment/list） */
data class Comment(
    val commentid: Int,
    val userid: String,
    val text: String,
    val commentAt: String?,
    val parentId: Int? = null,
    val nickname: String?,
    val avatarUrl: String?,
    val learnLevel: String?,
    val likesCount: Int,
    val isLiked: Boolean,
    val replies: List<Comment> = emptyList()
)

/** 当前登录用户资料（GET api/user/profile，学习目标字段对齐 Web 编辑资料弹窗） */
data class UserProfile(
    val userid: String,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val avatarFileName: String? = null,
    val bio: String? = null,
    val learnLevel: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val role: String? = null,   // USER | PREMIUM | ADMIN
    val createAt: String? = null,
    // ---- 学习目标（个人中心编辑资料） ----
    val dailyStudyGoalMins: Int? = null,
    val weeklyListeningGoalHours: Int? = null,
    val weeklyWordsGoal: Int? = null
)

/** 个人中心旅程概览统计（GET api/user/stats/overview，裸对象） */
data class ProfileStats(
    val totalHours: Double = 0.0,
    val streakDays: Int = 0,
    val wordsLearned: Int = 0,
    val speechEvalCount: Int = 0,
    val speechHighScoreCount: Int = 0
)

/** 每周活动图表的每日数据项（GET api/user/stats/weekly-activity） */
data class WeeklyActivityItem(
    val day: String = "",      // 星期几中文简称（周一…周日）
    val minutes: Int = 0
)

/** 成就项（GET api/user/achievements，裸数组） */
data class AchievementItem(
    val key: String,
    val name: String,
    val description: String,
    val icon: String,          // emoji 图标，直接以文本渲染
    val unlocked: Boolean = false,
    val unlockedAt: String? = null
)

// ---------- 我的收藏（对齐 Web core/favorites/dto.ts） ----------

/** 收藏的播客系列（GET api/user/favorites 的 podcasts 项） */
data class FavoriteSeries(
    val id: String,
    val title: String,
    /** Web 端取 platform，兜底播客标题 */
    val author: String,
    val thumbnailUrl: String? = null,
    val category: List<Tag> = emptyList(),
    val episodeCount: Int = 0,
    val plays: Int = 0,
    val followers: Int = 0
)

/** 收藏的单集（GET api/user/favorites 的 episodes 项） */
data class FavoriteEpisode(
    val id: String,
    val title: String,
    /** 所属播客标题 */
    val author: String,
    /** 所属播客平台（可能为空） */
    val platform: String = "",
    val thumbnailUrl: String? = null,
    /** Web 端以播客名充当分类标签 */
    val category: String = "",
    /** 服务端已格式化的中文日期 */
    val date: String = "",
    /** 服务端已格式化的 "M:SS" 时长 */
    val duration: String = "",
    val playCount: Int = 0,
    /** 该单集的总收藏数（服务端 _count.episode_favorites 聚合） */
    val favoriteCount: Int = 0,
    /** 用于跳转所属播客 */
    val podcastId: String = ""
)

/** 一次收藏列表拉取结果 */
data class FavoritesBundle(
    val podcasts: List<FavoriteSeries> = emptyList(),
    val episodes: List<FavoriteEpisode> = emptyList()
)

// ---------- 收听历史（对齐 Web core/listening-history/dto.ts） ----------

/** 历史记录里的剧集快照（author=平台，category=所属播客名） */
data class HistoryEpisode(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val category: String = "",
    val thumbnailUrl: String? = null,
    /** 服务端已格式化的 "M:SS" */
    val duration: String = "",
    val durationSeconds: Int = 0
)

/** 一条收听历史 */
data class HistoryItem(
    val historyid: Int = 0,
    /** ISO 时间串（UTC），驱动 今天/昨天/更早 分组 */
    val listenAt: String = "",
    val progressSeconds: Int = 0,
    val isFinished: Boolean = false,
    val episode: HistoryEpisode = HistoryEpisode()
) {
    /** 收听进度比例 0..1（无时长信息时为 0） */
    val progressRatio: Float
        get() = if (episode.durationSeconds > 0) {
            (progressSeconds.toFloat() / episode.durationSeconds).coerceIn(0f, 1f)
        } else 0f
}

/** 一页历史数据（GET api/user/history） */
data class HistoryPage(
    val items: List<HistoryItem> = emptyList(),
    val total: Int = 0,
    val hasMore: Boolean = false
)

// ---------- 学习路径（对齐 Web core/learning-path，列表/详情/剧集搜索） ----------

/** 学习路径卡片摘要（「我的集合」与「发现」共用） */
data class LearningPathSummary(
    val pathid: Int,
    val pathName: String,
    val description: String? = null,
    /** 第一集封面（签名 URL）；空路径为 null，UI 回退标题首字母占位 */
    val coverUrl: String? = null,
    val isPublic: Boolean = false,
    val itemCount: Int = 0,
    val creatorName: String = "",
    val creationAt: String? = null,
    /** 已听完集数占比 0..100（服务端按 listening_history 聚合） */
    val progress: Int = 0,
    val isOfficial: Boolean = false
)

/** 路径内的一条剧集（itemId = learning_path_items.id，拥有者移除时用） */
data class LearningPathEpisode(
    val itemId: Int,
    val order: Int = 0,
    /** 剧集快照：封面/音频签名 URL + 当前用户收听态（progressSeconds/isFinished） */
    val episode: Episode
)

/** 学习路径详情 */
data class LearningPathDetail(
    val pathid: Int,
    val pathName: String,
    val description: String? = null,
    /** 第一集封面签名 URL（无剧集为 null） */
    val coverUrl: String? = null,
    val isPublic: Boolean = false,
    /** 创建者 userid（仓库层与当前登录用户比对得出 isOwner） */
    val userid: String? = null,
    val creatorName: String = "",
    val creationAt: String? = null,
    val items: List<LearningPathEpisode> = emptyList(),
    /** 当前登录用户是否拥有者：驱动 添加剧集/编辑/删除/移除剧集 的条件渲染 */
    val isOwner: Boolean = false
)

/** 添加剧集弹窗的搜索结果（对齐 Web searchEpisodesAction） */
data class PathEpisodeSearchItem(
    val episodeid: String,
    val title: String,
    val thumbnailUrl: String? = null,
    /** 所属播客名 */
    val author: String = "",
    val duration: Int = 0
)

