package com.wxkzd.yuanlu

import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.ChannelData
import com.wxkzd.yuanlu.domain.model.Comment
import com.wxkzd.yuanlu.domain.model.DictEntry
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
import com.wxkzd.yuanlu.domain.repository.ContentRepository

/**
 * 测试用 ContentRepository 可复用替身：默认全部返回空成功，
 * 收藏相关调用记录进公开列表供断言；其余行为由子类/属性按需覆盖。
 */
open class FakeContentRepository : ContentRepository {

    // ---- 收藏行为的可编程返回 ----
    var favorites: FavoritesBundle = FavoritesBundle()
    var favoritesError: Result.Error? = null
    var podcastFavoriteCheck: Boolean = false
    var episodeFavoriteCheck: Boolean = false
    var favoriteMutationError: Result.Error? = null

    val addedPodcastFavorites = mutableListOf<String>()
    val removedPodcastFavorites = mutableListOf<String>()
    val addedEpisodeFavorites = mutableListOf<String>()
    val removedEpisodeFavorites = mutableListOf<String>()

    /** 成功的删除（模拟服务端事实：删除后不再出现在收藏列表里） */
    private val succeededRemovedPodcasts = mutableSetOf<String>()
    private val succeededRemovedEpisodes = mutableSetOf<String>()

    override suspend fun getLatestEpisodes(page: Int, pageSize: Int): Result<List<Episode>> =
        Result.Success(emptyList())

    override suspend fun getEpisode(episodeid: String): Result<Episode> =
        Result.Success(Episode(episodeid = episodeid, title = ""))

    override suspend fun getSubtitles(episodeid: String): Result<SubtitleBundle> =
        Result.Success(SubtitleBundle(emptyList(), null))

    override suspend fun getPodcastEpisodes(
        podcastid: String,
        page: Int,
        limit: Int,
        ascending: Boolean
    ): Result<EpisodePage> = Result.Success(EpisodePage(emptyList(), 0, false))

    override suspend fun getPodcasts(): Result<List<Podcast>> = Result.Success(emptyList())

    override suspend fun getPodcastDetail(podcastid: String): Result<PodcastDetail> =
        Result.Success(PodcastDetail(Podcast(podcastid = podcastid, title = ""), false, emptyList()))

    override suspend fun searchPodcasts(query: String): Result<List<Podcast>> =
        Result.Success(emptyList())

    override suspend fun getTags(query: String?): Result<List<Tag>> =
        Result.Success(emptyList())

    override suspend fun getChannel(name: String): Result<ChannelData> =
        Result.Success(ChannelData(name, 0, emptyList(), emptyList()))

    override suspend fun getComments(episodeid: String): Result<List<Comment>> =
        Result.Success(emptyList())

    override suspend fun createComment(
        episodeid: String,
        content: String,
        parentId: Int?
    ): Result<Comment> = Result.Error(600, "not implemented in fake")

    override suspend fun toggleCommentLike(commentid: Int): Result<Boolean> =
        Result.Success(false)

    override suspend fun translate(text: String): Result<String> =
        Result.Error(600, "not implemented in fake")

    override suspend fun fetchTtsAudioUrl(text: String): Result<String> =
        Result.Error(600, "not implemented in fake")

    override suspend fun lookupWord(word: String): Result<DictEntry> =
        Result.Error(600, "not implemented in fake")

    override suspend fun addVocabulary(
        word: String,
        definition: String,
        contextSentence: String,
        translation: String,
        episodeid: String,
        timestampSec: Int,
        speakUrl: String
    ): Result<Unit> = Result.Error(600, "not implemented in fake")

    override suspend fun getVocabularyWords(): Result<Set<String>> =
        Result.Success(emptySet())

    override suspend fun getAllVocabulary(): Result<List<VocabularyItem>> =
        Result.Success(emptyList())

    override suspend fun deleteVocabulary(vocabularyid: Int): Result<Unit> =
        Result.Error(600, "not implemented in fake")

    override suspend fun submitVocabularyReview(
        vocabularyid: Int,
        quality: Int
    ): Result<VocabularyReviewOutcome> = Result.Error(600, "not implemented in fake")

    override suspend fun updateVocabularyStatus(vocabularyid: Int, mastered: Boolean): Result<Unit> =
        Result.Error(600, "not implemented in fake")

    override suspend fun updateEpisodeProgress(
        episodeid: String,
        progressSeconds: Float,
        isFinished: Boolean
    ): Result<Unit> = Result.Success(Unit)

    // ---- 收听历史行为的可编程返回 ----
    var historyPages: Map<Int, HistoryPage> = emptyMap()
    val historyCalls = mutableListOf<Triple<Int, Int, String>>()

    override suspend fun getListeningHistory(
        page: Int,
        pageSize: Int,
        status: String
    ): Result<HistoryPage> {
        historyCalls += Triple(page, pageSize, status)
        return Result.Success(historyPages[page] ?: HistoryPage())
    }

    override suspend fun getFavorites(): Result<FavoritesBundle> =
        favoritesError ?: Result.Success(
            favorites.copy(
                podcasts = favorites.podcasts.filterNot { it.id in succeededRemovedPodcasts },
                episodes = favorites.episodes.filterNot { it.id in succeededRemovedEpisodes }
            )
        )

    override suspend fun checkPodcastFavorite(podcastid: String): Result<Boolean> =
        Result.Success(podcastFavoriteCheck)

    override suspend fun addPodcastFavorite(podcastid: String): Result<Unit> {
        addedPodcastFavorites += podcastid
        return favoriteMutationError ?: Result.Success(Unit)
    }

    override suspend fun removePodcastFavorite(podcastid: String): Result<Unit> {
        removedPodcastFavorites += podcastid
        return favoriteMutationError ?: Result.Success(Unit).also {
            succeededRemovedPodcasts += podcastid
        }
    }

    override suspend fun checkEpisodeFavorite(episodeid: String): Result<Boolean> =
        Result.Success(episodeFavoriteCheck)

    override suspend fun addEpisodeFavorite(episodeid: String): Result<Unit> {
        addedEpisodeFavorites += episodeid
        return favoriteMutationError ?: Result.Success(Unit)
    }

    override suspend fun removeEpisodeFavorite(episodeid: String): Result<Unit> {
        removedEpisodeFavorites += episodeid
        return favoriteMutationError ?: Result.Success(Unit).also {
            succeededRemovedEpisodes += episodeid
        }
    }

    // ---- 学习路径行为的可编程返回 ----
    var myLearningPaths: Result<List<LearningPathSummary>> = Result.Success(emptyList())
    var publicLearningPaths: Result<List<LearningPathSummary>> = Result.Success(emptyList())
    var learningPathDetail: Result<LearningPathDetail> =
        Result.Success(LearningPathDetail(pathid = 0, pathName = ""))
    /** 创建/编辑/删除/添加/移除 的可编程失败（默认成功） */
    var learningPathMutationError: Result.Error? = null
    var pathSearchResults: Result<List<PathEpisodeSearchItem>> = Result.Success(emptyList())

    val myLearningPathCalls = mutableListOf<Unit>()
    val createdLearningPaths = mutableListOf<Triple<String, String?, Boolean>>()
    val updatedLearningPaths = mutableListOf<Triple<Int, String, Pair<String?, Boolean>>>()
    val deletedLearningPaths = mutableListOf<Int>()
    val learningPathDetailCalls = mutableListOf<Int>()
    val addedPathEpisodes = mutableListOf<Pair<Int, String>>()
    val removedPathEpisodes = mutableListOf<Pair<Int, Int>>()
    val pathSearchCalls = mutableListOf<String>()

    override suspend fun getMyLearningPaths(): Result<List<LearningPathSummary>> {
        myLearningPathCalls += Unit
        return myLearningPaths
    }

    override suspend fun getPublicLearningPaths(): Result<List<LearningPathSummary>> =
        publicLearningPaths

    override suspend fun createLearningPath(
        pathName: String,
        description: String?,
        isPublic: Boolean
    ): Result<Unit> {
        createdLearningPaths += Triple(pathName, description, isPublic)
        return learningPathMutationError ?: Result.Success(Unit)
    }

    override suspend fun getLearningPath(pathid: Int): Result<LearningPathDetail> {
        learningPathDetailCalls += pathid
        return learningPathDetail
    }

    override suspend fun updateLearningPath(
        pathid: Int,
        pathName: String,
        description: String?,
        isPublic: Boolean
    ): Result<Unit> {
        updatedLearningPaths += Triple(pathid, pathName, description to isPublic)
        return learningPathMutationError ?: Result.Success(Unit)
    }

    override suspend fun deleteLearningPath(pathid: Int): Result<Unit> {
        deletedLearningPaths += pathid
        return learningPathMutationError ?: Result.Success(Unit)
    }

    override suspend fun addEpisodeToLearningPath(pathid: Int, episodeid: String): Result<Unit> {
        addedPathEpisodes += pathid to episodeid
        return learningPathMutationError ?: Result.Success(Unit)
    }

    override suspend fun removeEpisodeFromLearningPath(pathid: Int, itemId: Int): Result<Unit> {
        removedPathEpisodes += pathid to itemId
        return learningPathMutationError ?: Result.Success(Unit)
    }

    override suspend fun searchEpisodesForPath(query: String): Result<List<PathEpisodeSearchItem>> {
        pathSearchCalls += query
        return pathSearchResults
    }
}
