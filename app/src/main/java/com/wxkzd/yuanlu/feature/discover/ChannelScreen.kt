package com.wxkzd.yuanlu.feature.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxkzd.yuanlu.ui.components.EpisodeRow
import com.wxkzd.yuanlu.ui.components.EmptyBox
import com.wxkzd.yuanlu.ui.components.ErrorBox
import com.wxkzd.yuanlu.ui.components.LoadingBox
import com.wxkzd.yuanlu.ui.components.PodcastCard
import com.wxkzd.yuanlu.ui.components.SectionHeader

@Composable
fun ChannelRoute(
    name: String,
    onBack: () -> Unit,
    onOpenPodcast: (String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    viewModel: ChannelViewModel = hiltViewModel()
) {
    LaunchedEffect(name) {
        viewModel.load(name)
    }
    ChannelScreen(
        name = name,
        onBack = onBack,
        onOpenPodcast = onOpenPodcast,
        onOpenEpisode = onOpenEpisode,
        viewModel = viewModel
    )
}

@Composable
fun ChannelScreen(
    name: String,
    onBack: () -> Unit,
    onOpenPodcast: (String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    viewModel: ChannelViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                state.channel?.let {
                    Text(
                        text = "${it.podcastCount} podcasts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val channel = state.channel
        when {
            state.isLoading -> LoadingBox()
            state.error != null -> ErrorBox(message = state.error!!, onRetry = viewModel::retry)
            channel == null -> EmptyBox("Channel not found")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                item(key = "shows_header") {
                    SectionHeader("Top Shows", modifier = Modifier.padding(horizontal = 16.dp))
                }
                channel.topShows.chunked(2).forEachIndexed { rowIndex, rowItems ->
                    item(key = "shows_$rowIndex") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { podcast ->
                                Box(modifier = Modifier.weight(1f)) {
                                    PodcastCard(
                                        podcast = podcast,
                                        onClick = { onOpenPodcast(podcast.podcastid) }
                                    )
                                }
                            }
                            if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                item(key = "episodes_header") {
                    SectionHeader("Top Episodes", modifier = Modifier.padding(horizontal = 16.dp))
                }
                channel.topEpisodes.forEach { episode ->
                    item(key = "ep_${episode.episodeid}") {
                        EpisodeRow(
                            episode = episode,
                            onClick = { onOpenEpisode(episode.episodeid) }
                        )
                    }
                }
            }
        }
    }
}
