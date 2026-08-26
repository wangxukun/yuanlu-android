package com.wxkzd.yuanlu.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playerState by viewModel.playerState.collectAsState()
    val subtitles by viewModel.subtitles.collectAsState()
    val activeSubtitleIndex by viewModel.activeSubtitleIndex.collectAsState()
    
    val listState = rememberLazyListState()

    // Auto-scroll logic
    LaunchedEffect(activeSubtitleIndex) {
        if (activeSubtitleIndex >= 0) {
            listState.animateScrollToItem(activeSubtitleIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header / Cover Placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = playerState.currentEpisode?.title ?: "No Episode Loaded",
                style = MaterialTheme.typography.titleLarge
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Playback Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { viewModel.togglePlayPause() }) {
                Text(if (playerState.isPlaying) "Pause" else "Play")
            }
        }
        
        Text(
            text = "Time: \s",
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Subtitles List
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(subtitles) { index, subtitle ->
                val isActive = index == activeSubtitleIndex
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.seekToSubtitle(subtitle) }
                        .background(
                            color = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = subtitle.textEn,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground
                    )
                    subtitle.textCn?.let { cn ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = cn,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
