package com.wxkzd.yuanlu.domain.repository

import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.ChannelData
import com.wxkzd.yuanlu.domain.model.DictEntry
import com.wxkzd.yuanlu.domain.model.Comment
import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.EpisodePage
import com.wxkzd.yuanlu.domain.model.FavoritesBundle
import com.wxkzd.yuanlu.domain.model.HistoryPage
import com.wxkzd.yuanlu.domain.model.LearningPathDetail
import com.wxkzd.yuanlu.domain.model.LearningPathSummary
import com.wxkzd.yuanlu.domain.model.PathEpisodeSearchItem
import com.wxkzd.yuanlu.domain.model.Podcast
import com.wxkzd.yuanlu.domain.model.PodcastDetail
import com.wxkzd.yuanlu.domain.model.SubtitleBundle
import com.wxkzd.yuanlu.domain.model.Tag
import com.wxkzd.yuanlu.domain.model.VocabularyItem
import com.wxkzd.yuanlu.domain.model.VocabularyReviewOutcome

interface ContentRepository {

    /** 最新发布剧集（分页，publishAt 倒序） */
    suspend fun getLatestEpisodes(page: Int, pageSize: Int): Result<List<Episode>>

    /** 剧集详情（含所属播客与用户态：进度/收藏） */
    suspend fun getEpisode(episodeid: String): Result<Episode>

    /** 归一化双语字幕 + 签名音频直链（未登录时 audioUrl 为空、字幕仅 3 分钟预览） */
    suspend fun getSubtitles(episodeid: String): Result<SubtitleBundle>

    /** 某播客下的剧集分页列表（签名音频/封面 + 用户态） */
    suspend fun getPodcastEpisodes(
        podcastid: String,
        page: Int,
        limit: Int,
        ascending: Boolean
    ): Result<EpisodePage>

    /** 全部播客（封面已签名，含 tags/isEditorPick/platform） */
    suspend fun getPodcasts(): Result<List<Podcast>>

    /** 播客详情（头部信息 + 同频道推荐） */
    suspend fun getPodcastDetail(podcastid: String): Result<PodcastDetail>

    /** 按标题/描述/标签搜索播客 */
    suspend fun searchPodcasts(query: String): Result<List<Podcast>>

    /** 分类标签 */
    suspend fun getTags(query: String? = null): Result<List<Tag>>

    /** 频道（平台）聚合页数据 */
    suspend fun getChannel(name: String): Result<ChannelData>

    /** 某剧集下的评论（根评论倒序平铺，回复已挂到 replies） */
    suspend fun getComments(episodeid: String): Result<List<Comment>>

    /** 发布评论（parentId 非空为回复）；返回可直接入列的完整评论 */
    suspend fun createComment(episodeid: String, content: String, parentId: Int?): Result<Comment>

    /** 切换评论点赞，返回切换后的 liked 状态 */
    suspend fun toggleCommentLike(commentid: Int): Result<Boolean>

    /** 有道文本翻译（需登录，有每日配额） */
    suspend fun translate(text: String): Result<String>

    /** 有道 TTS 朗读音频地址（api/dictionary/youdao 的 speakUrl；语音评测 AI 朗读用） */
    suspend fun fetchTtsAudioUrl(text: String): Result<String>

    // ---------- 词典与生词（精听查词） ----------

    /** 查词典（GET api/dict/{word}）；配额用尽/未登录等以 Result.Error 返回 */
    suspend fun lookupWord(word: String): Result<DictEntry>

    /** 保存生词（POST api/vocabulary/add）；400=已在生词本、403=免费配额 */
    suspend fun addVocabulary(
        word: String,
        definition: String,
        contextSentence: String,
        translation: String,
        episodeid: String,
        timestampSec: Int,
        speakUrl: String
    ): Result<Unit>

    /** 已保存单词集合（小写；未登录返回空集语义由调用方处理） */
    suspend fun getVocabularyWords(): Result<Set<String>>

    // ---------- 生词本（列表管理与卡片复习） ----------

    /** 全量生词（GET api/vocabulary/all，含词典富数据/剧集名/SRS 状态），需登录 */
    suspend fun getAllVocabulary(): Result<List<VocabularyItem>>

    /** 彻底删除生词（POST api/vocabulary/delete）；404=不存在 403=无权 */
    suspend fun deleteVocabulary(vocabularyid: Int): Result<Unit>

    /**
     * 提交一次复习打卡（POST api/vocabulary/review）。
     * quality: 0=忘记 1=模糊 2=认识 3=简单；返回 SRS 更新后的熟练度与下次复习时间。
     */
    suspend fun submitVocabularyReview(vocabularyid: Int, quality: Int): Result<VocabularyReviewOutcome>

    /** 切换生词状态（POST api/vocabulary/status）：mastered=true 标记已掌握，false 放回学习 */
    suspend fun updateVocabularyStatus(vocabularyid: Int, mastered: Boolean): Result<Unit>

    // ---------- 播放进度上报 ----------

    /**
     * 上报收听进度（PATCH api/episode/{id}/progress，需登录）。
     * 服务端写入 listening_history，驱动历史页 / 断点续播 / 跨端进度条。
     */
    suspend fun updateEpisodeProgress(
        episodeid: String,
        progressSeconds: Float,
        isFinished: Boolean
    ): Result<Unit>

    // ---------- 收藏（我的收藏列表 + 详情页收藏交互，对齐 Web） ----------

    /** 收藏的播客系列与单集（GET api/user/favorites，需登录） */
    suspend fun getFavorites(): Result<FavoritesBundle>

    /** 查询播客收藏态（GET api/podcast/favorite/find-unique）；未登录返回 Error */
    suspend fun checkPodcastFavorite(podcastid: String): Result<Boolean>

    /** 收藏播客（POST api/podcast/favorite/insert，写 followerCount+1） */
    suspend fun addPodcastFavorite(podcastid: String): Result<Unit>

    /** 取消收藏播客（DELETE api/podcast/favorite/delete） */
    suspend fun removePodcastFavorite(podcastid: String): Result<Unit>

    /** 查询单集收藏态（GET api/episode/favorite/find-unique）；未登录返回 Error */
    suspend fun checkEpisodeFavorite(episodeid: String): Result<Boolean>

    /** 收藏单集（POST api/episode/favorite/insert） */
    suspend fun addEpisodeFavorite(episodeid: String): Result<Unit>

    /** 取消收藏单集（DELETE api/episode/favorite/delete） */
    suspend fun removeEpisodeFavorite(episodeid: String): Result<Unit>

    // ---------- 收听历史（我的收藏页同款入口，需登录） ----------

    /**
     * 分页拉取收听历史（GET api/user/history）。
     * status: all | in-progress | finished（服务端过滤）；返回 { items, total, hasMore }。
     */
    suspend fun getListeningHistory(
        page: Int,
        pageSize: Int,
        status: String
    ): Result<HistoryPage>

    // ---------- 学习路径（复刻 Web /library/learning-paths，需登录） ----------

    /** 我的路径卡片摘要（含进度/封面/创建者），GET api/learning-paths/mine */
    suspend fun getMyLearningPaths(): Result<List<LearningPathSummary>>

    /** 公开路径（发现 Tab，服务端排除当前用户），GET api/learning-paths/public */
    suspend fun getPublicLearningPaths(): Result<List<LearningPathSummary>>

    /** 创建路径（POST api/learning-paths）；401=未登录 */
    suspend fun createLearningPath(
        pathName: String,
        description: String?,
        isPublic: Boolean
    ): Result<Unit>

    /** 路径详情（GET api/learning-paths/{pathid}）；isOwner 由创建者与当前用户比对得出 */
    suspend fun getLearningPath(pathid: Int): Result<LearningPathDetail>

    /** 编辑路径元数据（PATCH api/learning-paths/{pathid}）；403=非拥有者 */
    suspend fun updateLearningPath(
        pathid: Int,
        pathName: String,
        description: String?,
        isPublic: Boolean
    ): Result<Unit>

    /** 删除路径（DELETE api/learning-paths/{pathid}）；403=非拥有者 */
    suspend fun deleteLearningPath(pathid: Int): Result<Unit>

    /** 添加剧集到路径末尾（POST api/learning-paths/{pathid}/episodes）；已在列表中→Error */
    suspend fun addEpisodeToLearningPath(pathid: Int, episodeid: String): Result<Unit>

    /** 从路径移除剧集（DELETE api/learning-paths/{pathid}/episodes/{itemId}）；403=非拥有者 */
    suspend fun removeEpisodeFromLearningPath(pathid: Int, itemId: Int): Result<Unit>

    /** 添加剧集弹窗的剧集搜索（GET api/episode/search-for-path，published 最多 20 条） */
    suspend fun searchEpisodesForPath(query: String): Result<List<PathEpisodeSearchItem>>
}
