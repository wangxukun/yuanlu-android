package com.wxkzd.yuanlu.data.remote

import com.wxkzd.yuanlu.core.network.ApiResponse
import com.wxkzd.yuanlu.data.remote.dto.ChannelDataDto
import com.wxkzd.yuanlu.data.remote.dto.CommentDto
import com.wxkzd.yuanlu.data.remote.dto.CreateCommentRequestDto
import com.wxkzd.yuanlu.data.remote.dto.DictResponseDto
import com.wxkzd.yuanlu.data.remote.dto.EpisodeDto
import com.wxkzd.yuanlu.data.remote.dto.EpisodePageDto
import com.wxkzd.yuanlu.data.remote.dto.FavoriteMutationDto
import com.wxkzd.yuanlu.data.remote.dto.FavoritesResponseDto
import com.wxkzd.yuanlu.data.remote.dto.HistoryResponseDto
import com.wxkzd.yuanlu.data.remote.dto.AddEpisodeToPathRequestDto
import com.wxkzd.yuanlu.data.remote.dto.LearningPathDetailResponseDto
import com.wxkzd.yuanlu.data.remote.dto.LearningPathMutationResponseDto
import com.wxkzd.yuanlu.data.remote.dto.LearningPathsResponseDto
import com.wxkzd.yuanlu.data.remote.dto.LearningPathUpsertRequestDto
import com.wxkzd.yuanlu.data.remote.dto.PathSearchResponseDto
import com.wxkzd.yuanlu.data.remote.dto.LikeCommentRequestDto
import com.wxkzd.yuanlu.data.remote.dto.LikeCommentResponseDto
import com.wxkzd.yuanlu.data.remote.dto.PodcastDetailDto
import com.wxkzd.yuanlu.data.remote.dto.PodcastDto
import com.wxkzd.yuanlu.data.remote.dto.ProgressUpdateRequestDto
import com.wxkzd.yuanlu.data.remote.dto.SubtitlesResponseDto
import com.wxkzd.yuanlu.data.remote.dto.TagDto
import com.wxkzd.yuanlu.data.remote.dto.TranslateRequestDto
import com.wxkzd.yuanlu.data.remote.dto.VocabularyAddRequestDto
import com.wxkzd.yuanlu.data.remote.dto.VocabularyAddResponseDto
import com.wxkzd.yuanlu.data.remote.dto.VocabularyAllResponseDto
import com.wxkzd.yuanlu.data.remote.dto.VocabularyDeleteRequestDto
import com.wxkzd.yuanlu.data.remote.dto.VocabularyDeleteResponseDto
import com.wxkzd.yuanlu.data.remote.dto.VocabularyReviewRequestDto
import com.wxkzd.yuanlu.data.remote.dto.VocabularyReviewResponseDto
import com.wxkzd.yuanlu.data.remote.dto.VocabularyStatusRequestDto
import com.wxkzd.yuanlu.data.remote.dto.VocabularyStatusResponseDto
import com.wxkzd.yuanlu.data.remote.dto.VocabularyWordsResponseDto
import com.wxkzd.yuanlu.data.remote.dto.YoudaoResponseDto
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
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

    // ---------- 生词本（列表管理与卡片复习） ----------

    /** 信封：data = 全量生词（含词典富数据与剧集名），需登录 */
    @GET("api/vocabulary/all")
    suspend fun vocabularyAll(): VocabularyAllResponseDto

    /** 裸 { success }；404=不存在 403=无权，需登录 */
    @POST("api/vocabulary/delete")
    suspend fun deleteVocabulary(@Body body: VocabularyDeleteRequestDto): VocabularyDeleteResponseDto

    /** 信封：data = { vocabularyid, nextReviewAt, proficiency, daysAdded }（SRS 复习打卡） */
    @POST("api/vocabulary/review")
    suspend fun submitVocabularyReview(@Body body: VocabularyReviewRequestDto): VocabularyReviewResponseDto

    /** 信封：data = { vocabularyid, status }（LEARNING <-> MASTERED） */
    @POST("api/vocabulary/status")
    suspend fun updateVocabularyStatus(@Body body: VocabularyStatusRequestDto): VocabularyStatusResponseDto

    // ---------- 播放进度上报 ----------

    /** 信封 { success, message, data: listening_history 行 }；data 仅作确认用 */
    @PATCH("api/episode/{episodeid}/progress")
    suspend fun updateProgress(
        @Path("episodeid") episodeid: String,
        @Body body: ProgressUpdateRequestDto
    ): ApiResponse<JsonElement>

    // ---------- 收藏（对齐 Web /api/{podcast,episode}/favorite/* 与 /api/user/favorites） ----------

    /** 信封：data = { podcasts, episodes }，需登录；封面/日期/时长服务端已处理 */
    @GET("api/user/favorites")
    suspend fun userFavorites(): FavoritesResponseDto

    /** 裸 { success }：success=true 表示已收藏（Web 端 find-unique 口径） */
    @GET("api/podcast/favorite/find-unique")
    suspend fun checkPodcastFavorite(
        @Query("podcastid") podcastid: String,
        @Query("userid") userid: String
    ): FavoriteMutationDto

    /** 裸 { success }；后端读 FormData，urlencoded 表单同构；需登录 */
    @FormUrlEncoded
    @POST("api/podcast/favorite/insert")
    suspend fun addPodcastFavorite(
        @Field("podcastid") podcastid: String,
        @Field("userid") userid: String
    ): FavoriteMutationDto

    /**
     * 裸 { success }；后端固定读 DELETE 方法 + FormData 体。
     * 注意不能写 @FormUrlEncoded + @DELETE：Retrofit 校验 DELETE 无请求体会直接抛
     * IllegalArgumentException（请求发不出），必须用 @HTTP(hasBody = true) 声明带体 DELETE。
     */
    @FormUrlEncoded
    @HTTP(method = "DELETE", path = "api/podcast/favorite/delete", hasBody = true)
    suspend fun removePodcastFavorite(
        @Field("podcastid") podcastid: String,
        @Field("userid") userid: String
    ): FavoriteMutationDto

    /** 裸 { success }：success=true 表示已收藏 */
    @GET("api/episode/favorite/find-unique")
    suspend fun checkEpisodeFavorite(
        @Query("episodeid") episodeid: String,
        @Query("userid") userid: String
    ): FavoriteMutationDto

    /** 裸 { success }；需登录 */
    @FormUrlEncoded
    @POST("api/episode/favorite/insert")
    suspend fun addEpisodeFavorite(
        @Field("episodeid") episodeid: String,
        @Field("userid") userid: String
    ): FavoriteMutationDto

    /** 裸 { success }；带体 DELETE（同 removePodcastFavorite 的 @HTTP 说明） */
    @FormUrlEncoded
    @HTTP(method = "DELETE", path = "api/episode/favorite/delete", hasBody = true)
    suspend fun removeEpisodeFavorite(
        @Field("episodeid") episodeid: String,
        @Field("userid") userid: String
    ): FavoriteMutationDto

    // ---------- 收听历史（对齐 Web /api/user/history，需登录） ----------

    /** 信封：data = { items, total, hasMore }；status: all | in-progress | finished */
    @GET("api/user/history")
    suspend fun userHistory(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("status") status: String = "all"
    ): HistoryResponseDto

    // ---------- 学习路径（对齐 Web core/learning-path，需登录；REST 契约见 LearningPathDtos） ----------

    /** 信封：data = 我的路径卡片摘要（含进度/封面/创建者），需登录 */
    @GET("api/learning-paths/mine")
    suspend fun myLearningPaths(): LearningPathsResponseDto

    /** 信封：data = 公开路径（发现 Tab，服务端排除当前用户），需登录 */
    @GET("api/learning-paths/public")
    suspend fun publicLearningPaths(): LearningPathsResponseDto

    /** 信封；400=名称为空，需登录 */
    @POST("api/learning-paths")
    suspend fun createLearningPath(@Body body: LearningPathUpsertRequestDto): LearningPathMutationResponseDto

    /** 信封：data = 路径详情（剧集清单 + 创建者 + 当前用户收听态） */
    @GET("api/learning-paths/{pathid}")
    suspend fun learningPathDetail(@Path("pathid") pathid: Int): LearningPathDetailResponseDto

    /** 信封；403=非拥有者，需登录 */
    @PATCH("api/learning-paths/{pathid}")
    suspend fun updateLearningPath(
        @Path("pathid") pathid: Int,
        @Body body: LearningPathUpsertRequestDto
    ): LearningPathMutationResponseDto

    /** 信封；403=非拥有者，需登录 */
    @DELETE("api/learning-paths/{pathid}")
    suspend fun deleteLearningPath(@Path("pathid") pathid: Int): LearningPathMutationResponseDto

    /** 信封；success=false + message=剧集已在列表中；403=非拥有者 */
    @POST("api/learning-paths/{pathid}/episodes")
    suspend fun addEpisodeToPath(
        @Path("pathid") pathid: Int,
        @Body body: AddEpisodeToPathRequestDto
    ): LearningPathMutationResponseDto

    /** 信封；403=非拥有者，需登录 */
    @DELETE("api/learning-paths/{pathid}/episodes/{itemId}")
    suspend fun removeEpisodeFromPath(
        @Path("pathid") pathid: Int,
        @Path("itemId") itemId: Int
    ): LearningPathMutationResponseDto

    /** 信封：data = 添加剧集弹窗搜索结果（published 剧集，最多 20 条） */
    @GET("api/episode/search-for-path")
    suspend fun searchEpisodesForPath(@Query("query") query: String): PathSearchResponseDto
}
