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
