package com.wxkzd.yuanlu.domain.repository

import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.ChannelData
import com.wxkzd.yuanlu.domain.model.Comment
import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.EpisodePage
import com.wxkzd.yuanlu.domain.model.Podcast
import com.wxkzd.yuanlu.domain.model.PodcastDetail
import com.wxkzd.yuanlu.domain.model.SubtitleBundle
import com.wxkzd.yuanlu.domain.model.Tag

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
}
