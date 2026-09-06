package com.wxkzd.yuanlu.data.repository

import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.data.remote.ContentApi
import com.wxkzd.yuanlu.data.remote.dto.CommentDto
import com.wxkzd.yuanlu.data.remote.dto.CreateCommentRequestDto
import com.wxkzd.yuanlu.data.remote.dto.VocabularyAddRequestDto
import com.wxkzd.yuanlu.data.remote.dto.VocabularyDeleteRequestDto
import com.wxkzd.yuanlu.data.remote.dto.VocabularyReviewRequestDto
import com.wxkzd.yuanlu.data.remote.dto.VocabularyStatusRequestDto
import com.wxkzd.yuanlu.data.remote.dto.LikeCommentRequestDto
import com.wxkzd.yuanlu.data.remote.dto.ProgressUpdateRequestDto
import com.wxkzd.yuanlu.data.remote.dto.TranslateRequestDto
import com.wxkzd.yuanlu.data.remote.dto.toBundle
import com.wxkzd.yuanlu.domain.model.ChannelData
import com.wxkzd.yuanlu.domain.model.DictEntry
import com.wxkzd.yuanlu.domain.model.Comment
import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.EpisodePage
import com.wxkzd.yuanlu.domain.model.Podcast
import com.wxkzd.yuanlu.domain.model.PodcastDetail
import com.wxkzd.yuanlu.domain.model.SubtitleBundle
import com.wxkzd.yuanlu.domain.model.Tag
import com.wxkzd.yuanlu.domain.model.VocabularyItem
import com.wxkzd.yuanlu.domain.model.VocabularyReviewOutcome
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepositoryImpl @Inject constructor(
    private val api: ContentApi
) : ContentRepository {

    override suspend fun getLatestEpisodes(page: Int, pageSize: Int): Result<List<Episode>> =
        call { api.listEpisodes(page, pageSize).map { it.toDomain() } }

    override suspend fun getEpisode(episodeid: String): Result<Episode> =
        call { api.episodeDetail(episodeid).toDomain() }

    override suspend fun getSubtitles(episodeid: String): Result<SubtitleBundle> =
        call { api.episodeSubtitles(episodeid).toBundle() }

    override suspend fun getPodcastEpisodes(
        podcastid: String,
        page: Int,
        limit: Int,
        ascending: Boolean
    ): Result<EpisodePage> = call {
        val response = api.episodesByPodcast(
            podcastId = podcastid,
            page = page,
            limit = limit,
            sort = if (ascending) "asc" else "desc"
        )
        val data = response.data
        if (!response.success || data == null) {
            throw IOException(response.error ?: "加载单集失败")
        }
        data.toDomain()
    }

    override suspend fun getPodcasts(): Result<List<Podcast>> =
        call { api.podcastList().map { it.toDomain() } }

    override suspend fun getPodcastDetail(podcastid: String): Result<PodcastDetail> =
        call { api.podcastDetail(podcastid).toDomain() }

    override suspend fun searchPodcasts(query: String): Result<List<Podcast>> = call {
        val response = api.searchPodcasts(query)
        val data = response.data
        if (!response.success || data == null) {
            throw IOException(response.error ?: "搜索失败")
        }
        data.map { it.toDomain() }
    }

    override suspend fun getTags(query: String?): Result<List<Tag>> =
        call { api.tagList(query).map { it.toDomain() } }

    override suspend fun getChannel(name: String): Result<ChannelData> = call {
        val response = api.channel(name)
        val data = response.data
        if (!response.success || data == null) {
            throw IOException(response.error ?: "加载频道失败")
        }
        data.toDomain()
    }

    override suspend fun getComments(episodeid: String): Result<List<Comment>> = call {
        api.commentList(episodeid).map { it.toDomain() }.buildCommentTree()
    }

    override suspend fun createComment(
        episodeid: String,
        content: String,
        parentId: Int?
    ): Result<Comment> = call {
        api.createComment(CreateCommentRequestDto(episodeid, content, parentId)).toDomain()
    }

    override suspend fun toggleCommentLike(commentid: Int): Result<Boolean> = call {
        api.likeComment(LikeCommentRequestDto(commentid)).liked
    }

    override suspend fun translate(text: String): Result<String> = call {
        val response = api.translate(TranslateRequestDto(text))
        response.definition?.takeIf { it.isNotBlank() }
            ?: throw IOException("翻译失败，请稍后重试")
    }

    override suspend fun fetchTtsAudioUrl(text: String): Result<String> = call {
        val response = api.translate(TranslateRequestDto(text))
        response.speakUrl?.takeIf { it.isNotBlank() }
            ?: throw IOException("朗读音频获取失败，请稍后重试")
    }

    /** 平铺评论 → 根评论（倒序）+ replies 挂载，对齐 Web 端 buildCommentTree */
    private fun List<Comment>.buildCommentTree(): List<Comment> {
        val byId = mutableMapOf<Int, Comment>()
        val roots = mutableListOf<Comment>()
        // commentid 自增，正序遍历保证父评论先入树
        sortedBy { it.commentid }.forEach { comment ->
            val parent = comment.parentId?.let { byId[it] }
            if (parent != null) {
                byId[parent.commentid] = parent.copy(replies = parent.replies + comment)
            } else {
                roots += comment
            }
        }
        // 根评论保持接口的时间倒序（最新在前）
        return roots.sortedByDescending { it.commentid }
    }

    private fun CommentDto.toDomain() = Comment(
        commentid = commentid,
        userid = userid,
        text = commentText,
        commentAt = commentAt,
        parentId = parentId,
        nickname = User?.profile?.nickname ?: User?.email?.substringBefore("@"),
        avatarUrl = User?.profile?.avatarUrl,
        learnLevel = User?.profile?.learnLevel,
        likesCount = likesCount,
        isLiked = isLiked
    )

    // ---------- 词典与生词（精听查词） ----------

    override suspend fun lookupWord(word: String): Result<DictEntry> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.lookupWord(word)
                val data = response.data
                if (response.success && data != null) {
                    Result.Success(data.toDomain())
                } else {
                    Result.Error(600, response.message ?: response.error ?: "暂无词典数据")
                }
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: HttpException) {
                val message = when (e.code()) {
                    401 -> "请先登录"
                    403 -> "今日 30 次免费词典查询已用完，升级会员解锁无限查询！"
                    429 -> "查询过于频繁，请明天再试"
                    else -> "词典查询失败（${e.code()}）"
                }
                Result.Error(e.code(), message)
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "Unexpected error")
            }
        }

    override suspend fun addVocabulary(
        word: String,
        definition: String,
        contextSentence: String,
        translation: String,
        episodeid: String,
        timestampSec: Int,
        speakUrl: String
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.addVocabulary(
                    VocabularyAddRequestDto(
                        word = word,
                        definition = definition,
                        contextSentence = contextSentence,
                        translation = translation,
                        episodeid = episodeid,
                        timestamp = timestampSec,
                        speakUrl = speakUrl
                    )
                )
                if (response.success) {
                    Result.Success(Unit)
                } else {
                    Result.Error(600, response.message ?: "保存失败")
                }
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: HttpException) {
                val message = when (e.code()) {
                    400 -> "该单词已在生词本中"
                    401 -> "请先登录后再保存生词"
                    403 -> "生词本配额已满，升级会员解锁无限生词本"
                    else -> "保存失败（${e.code()}）"
                }
                Result.Error(e.code(), message)
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "Unexpected error")
            }
        }

    override suspend fun getVocabularyWords(): Result<Set<String>> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.vocabularyWords()
                if (response.success) {
                    Result.Success(response.data.map { it.lowercase() }.toSet())
                } else {
                    Result.Success(emptySet())
                }
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: HttpException) {
                Result.Error(e.code(), if (e.code() == 401) "请先登录" else "生词本加载失败")
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "Unexpected error")
            }
        }

    // ---------- 生词本（列表管理与卡片复习） ----------

    override suspend fun getAllVocabulary(): Result<List<VocabularyItem>> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.vocabularyAll()
                if (response.success) {
                    Result.Success(response.data.map { it.toDomain() })
                } else {
                    Result.Error(600, response.message ?: "生词本加载失败")
                }
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: HttpException) {
                val message = when (e.code()) {
                    401 -> "请先登录"
                    else -> "生词本加载失败（${e.code()}）"
                }
                Result.Error(e.code(), message)
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "Unexpected error")
            }
        }

    override suspend fun deleteVocabulary(vocabularyid: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.deleteVocabulary(VocabularyDeleteRequestDto(vocabularyid))
                if (response.success) {
                    Result.Success(Unit)
                } else {
                    Result.Error(600, response.message ?: "删除失败")
                }
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: HttpException) {
                val message = when (e.code()) {
                    404 -> "未找到该生词记录"
                    403 -> "无权删除该记录"
                    401 -> "请先登录"
                    else -> "删除失败（${e.code()}）"
                }
                Result.Error(e.code(), message)
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "Unexpected error")
            }
        }

    override suspend fun submitVocabularyReview(
        vocabularyid: Int,
        quality: Int
    ): Result<VocabularyReviewOutcome> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.submitVocabularyReview(
                    VocabularyReviewRequestDto(vocabularyid, quality)
                )
                val data = response.data
                if (response.success && data != null) {
                    Result.Success(
                        VocabularyReviewOutcome(
                            vocabularyid = data.vocabularyid,
                            proficiency = data.proficiency,
                            nextReviewAt = data.nextReviewAt,
                            daysAdded = data.daysAdded
                        )
                    )
                } else {
                    Result.Error(600, response.message ?: "复习进度保存失败")
                }
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: HttpException) {
                val message = when (e.code()) {
                    404 -> "未找到该生词记录"
                    403 -> "无权操作该生词"
                    401 -> "请先登录"
                    else -> "复习进度保存失败（${e.code()}）"
                }
                Result.Error(e.code(), message)
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "Unexpected error")
            }
        }

    override suspend fun updateVocabularyStatus(
        vocabularyid: Int,
        mastered: Boolean
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val status = if (mastered) "MASTERED" else "LEARNING"
                val response = api.updateVocabularyStatus(
                    VocabularyStatusRequestDto(vocabularyid, status)
                )
                if (response.success) {
                    Result.Success(Unit)
                } else {
                    Result.Error(600, response.message ?: "状态更新失败")
                }
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: HttpException) {
                val message = when (e.code()) {
                    404 -> "未找到该生词记录"
                    403 -> "无权操作该生词"
                    401 -> "请先登录"
                    else -> "状态更新失败（${e.code()}）"
                }
                Result.Error(e.code(), message)
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "Unexpected error")
            }
        }

    override suspend fun updateEpisodeProgress(
        episodeid: String,
        progressSeconds: Float,
        isFinished: Boolean
    ): Result<Unit> = call {
        val response = api.updateProgress(
            episodeid,
            ProgressUpdateRequestDto(progressSeconds, isFinished)
        )
        if (!response.success) {
            throw IOException(response.error ?: "进度保存失败")
        }
    }

    private suspend fun <T> call(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.Success(block())
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: HttpException) {
                val message = when (e.code()) {
                    401 -> "请先登录"
                    403 -> "今日免费翻译次数已用完，升级会员解锁无限查询"
                    else -> "请求失败（${e.code()}）"
                }
                Result.Error(e.code(), message)
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "Unexpected error")
            }
        }
}
