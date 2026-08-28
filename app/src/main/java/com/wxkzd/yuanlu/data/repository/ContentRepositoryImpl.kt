package com.wxkzd.yuanlu.data.repository

import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.data.remote.ContentApi
import com.wxkzd.yuanlu.data.remote.dto.toBundle
import com.wxkzd.yuanlu.domain.model.ChannelData
import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.EpisodePage
import com.wxkzd.yuanlu.domain.model.Podcast
import com.wxkzd.yuanlu.domain.model.PodcastDetail
import com.wxkzd.yuanlu.domain.model.SubtitleBundle
import com.wxkzd.yuanlu.domain.model.Tag
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    private suspend fun <T> call(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.Success(block())
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "Unexpected error")
            }
        }
}
