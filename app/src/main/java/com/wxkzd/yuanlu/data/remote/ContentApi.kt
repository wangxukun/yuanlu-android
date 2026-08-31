package com.wxkzd.yuanlu.data.remote

import com.wxkzd.yuanlu.core.network.ApiResponse
import com.wxkzd.yuanlu.data.remote.dto.ChannelDataDto
import com.wxkzd.yuanlu.data.remote.dto.CommentDto
import com.wxkzd.yuanlu.data.remote.dto.CreateCommentRequestDto
import com.wxkzd.yuanlu.data.remote.dto.DictResponseDto
import com.wxkzd.yuanlu.data.remote.dto.EpisodeDto
import com.wxkzd.yuanlu.data.remote.dto.EpisodePageDto
import com.wxkzd.yuanlu.data.remote.dto.LikeCommentRequestDto
import com.wxkzd.yuanlu.data.remote.dto.LikeCommentResponseDto
import com.wxkzd.yuanlu.data.remote.dto.PodcastDetailDto
import com.wxkzd.yuanlu.data.remote.dto.PodcastDto
import com.wxkzd.yuanlu.data.remote.dto.SubtitlesResponseDto
import com.wxkzd.yuanlu.data.remote.dto.TagDto
import com.wxkzd.yuanlu.data.remote.dto.TranslateRequestDto
import com.wxkzd.yuanlu.data.remote.dto.VocabularyAddRequestDto
import com.wxkzd.yuanlu.data.remote.dto.VocabularyAddResponseDto
import com.wxkzd.yuanlu.data.remote.dto.VocabularyWordsResponseDto
import com.wxkzd.yuanlu.data.remote.dto.YoudaoResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 内容浏览相关端点（M3）。
 * 裸数组/信封的差异按端点固定建模，均以后端 yuanlu 仓库路由源码为准。
 */
interface ContentApi {

    /** 裸数组；page>=1 且 pageSize>=1 时按 publishAt 倒序分页 */
    @GET("api/episode/list")
    suspend fun listEpisodes(
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null
    ): List<EpisodeDto>

    /** 裸对象，含 podcast/tags 与 userState（登录时） */
    @GET("api/episode/detail")
    suspend fun episodeDetail(@Query("id") id: String): EpisodeDto

    /** 信封变体：{ success, data: Subtitle[], audioUrl }，audioUrl 仅登录后签发 */
    @GET("api/episode/subtitles")
    suspend fun episodeSubtitles(@Query("id") id: String): SubtitlesResponseDto

    /** 信封：data = { episodes, total, hasMore } */
    @GET("api/episode/list-by-podcastid")
    suspend fun episodesByPodcast(
        @Query("podcastId") podcastId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("sort") sort: String = "desc"
    ): ApiResponse<EpisodePageDto>

    /** 裸数组，coverUrl 已签名 */
    @GET("api/podcast/list")
    suspend fun podcastList(): List<PodcastDto>

    /** 裸对象：播客字段 + episode[] + channelPodcasts[] */
    @GET("api/podcast/detail")
    suspend fun podcastDetail(@Query("id") id: String): PodcastDetailDto

    /** 信封：{ success, data: PodcastDto[], query, total } */
    @GET("api/podcast/search")
    suspend fun searchPodcasts(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): ApiResponse<List<PodcastDto>>

    /** 裸数组 */
    @GET("api/tag/list")
    suspend fun tagList(@Query("query") query: String? = null): List<TagDto>

    /** 信封：data = { platformName, podcastCount, topShows, topEpisodes } */
    @GET("api/channel/{name}")
    suspend fun channel(@Path(value = "name", encoded = true) name: String): ApiResponse<ChannelDataDto>

    /** 裸数组：按剧集倒序的评论（含用户信息/点赞数/当前用户点赞态） */
    @GET("api/comment/list")
    suspend fun commentList(@Query("episodeid") episodeid: String): List<CommentDto>

    /** 裸对象：创建评论（或回复，parentId 非空），需登录 */
    @POST("api/comment/create")
    suspend fun createComment(@Body body: CreateCommentRequestDto): CommentDto

    /** { liked }：切换点赞，需登录 */
    @POST("api/comment/like")
    suspend fun likeComment(@Body body: LikeCommentRequestDto): LikeCommentResponseDto

    /** 裸对象：有道文本翻译（definition 为译文），需登录且有每日配额 */
    @POST("api/dictionary/youdao")
    suspend fun translate(@Body body: TranslateRequestDto): YoudaoResponseDto

    // ---------- 词典与生词（精听查词） ----------

    /** 信封：data = DictEntryDTO（缓存命中匿名可查；LLM 生成计入登录用户配额） */
    @GET("api/dict/{word}")
    suspend fun lookupWord(@Path(value = "word", encoded = true) word: String): DictResponseDto

    /** { success }；400=已在生词本、403=免费配额、401=未登录 */
    @POST("api/vocabulary/add")
    suspend fun addVocabulary(@Body body: VocabularyAddRequestDto): VocabularyAddResponseDto

    /** 信封：data = 已保存单词小写列表（用于查询弹层已保存态） */
    @GET("api/vocabulary/words")
    suspend fun vocabularyWords(): VocabularyWordsResponseDto
}
