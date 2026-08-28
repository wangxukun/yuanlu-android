package com.wxkzd.yuanlu.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.materialIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxkzd.yuanlu.core.media.PlayerState
import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.Subtitle
import com.wxkzd.yuanlu.ui.components.ErrorBox
import com.wxkzd.yuanlu.ui.components.LoadingBox
import com.wxkzd.yuanlu.ui.components.formatMillis

/** Pause 不在 material-icons-core 核心集内，这里用标准 24dp 网格自绘 */
private val PauseIcon: ImageVector = materialIcon(name = "Filled.Pause") {
    path {
        moveTo(6.0f, 5.0f)
        horizontalLineToRelative(4.0f)
        verticalLineToRelative(14.0f)
        horizontalLineToRelative(-4.0f)
        close()
        moveTo(14.0f, 5.0f)
        horizontalLineToRelative(4.0f)
        verticalLineToRelative(14.0f)
        horizontalLineToRelative(-4.0f)
        close()
    }
}

@Composable
fun PlayerRoute(
    episodeid: String,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    LaunchedEffect(episodeid) {
        viewModel.load(episodeid)
    }
    PlayerScreen(onBack = onBack, onLogin = onLogin, viewModel = viewModel)
}

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    viewModel: PlayerViewModel
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val subtitles by viewModel.subtitles.collectAsStateWithLifecycle()
    val activeSubtitleIndex by viewModel.activeSubtitleIndex.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()

    // 当前句变化时平滑滚动对齐（M2 行为）
    LaunchedEffect(activeSubtitleIndex) {
        if (activeSubtitleIndex >= 0) {
            listState.animateScrollToItem(activeSubtitleIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        when (val state = uiState) {
            PlayerUiState.Loading -> LoadingBox()
            is PlayerUiState.Error -> ErrorBox(message = state.message, onRetry = viewModel::retry)
            is PlayerUiState.LoginRequired -> {
                PlayerHeader(onBack = onBack, episode = state.episode)
                LoginNotice(onLogin = onLogin)
                SubtitleList(
                    subtitles = subtitles,
                    activeIndex = activeSubtitleIndex,
                    listState = listState,
                    enabled = false,
                    onSubtitleClick = {}
                )
            }
            is PlayerUiState.Ready -> {
                PlayerHeader(onBack = onBack, episode = state.episode)
                PlaybackControls(
                    playerState = playerState,
                    onToggle = viewModel::togglePlayPause,
                    onSeekBy = viewModel::seekBy,
                    onSeekTo = viewModel::seekTo
                )
                SubtitleList(
                    subtitles = subtitles,
                    activeIndex = activeSubtitleIndex,
                    listState = listState,
                    enabled = true,
                    onSubtitleClick = viewModel::seekToSubtitle
                )
            }
        }
    }
}

@Composable
private fun PlayerHeader(onBack: () -> Unit, episode: Episode) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp), // Adjusted padding to compensate
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            episode.podcastTitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = Modifier.width(48.dp))
    }
}

@Composable
private fun LoginNotice(onLogin: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "登录后可收听完整内容，当前仅展示前 3 分钟字幕。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "立即登录",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onLogin() }
            )
        }
    }
}

@Composable
private fun PlaybackControls(
    playerState: PlayerState,
    onToggle: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit
) {
    val durationMs = if (playerState.duration > 0) playerState.duration else 0L
    var dragPosition by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Slider(
            value = if (isDragging) dragPosition
            else if (durationMs > 0) playerState.currentPosition.toFloat() / durationMs
            else 0f,
            onValueChange = {
                isDragging = true
                dragPosition = it
            },
            onValueChangeFinished = {
                if (durationMs > 0) {
                    onSeekTo((dragPosition * durationMs).toLong())
                }
                isDragging = false
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = durationMs > 0
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = formatMillis(if (isDragging) (dragPosition * durationMs).toLong() else playerState.currentPosition),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatMillis(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onSeekBy(-10_000L) }) { Text("-10s") }
            Spacer(modifier = Modifier.width(16.dp))
            FilledIconButton(
                onClick = onToggle,
                modifier = Modifier.size(64.dp),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (playerState.isPlaying) PauseIcon else Icons.Filled.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            TextButton(onClick = { onSeekBy(30_000L) }) { Text("+30s") }
        }
    }
}

@Composable
private fun SubtitleList(
    subtitles: List<Subtitle>,
    activeIndex: Int,
    listState: LazyListState,
    enabled: Boolean,
    onSubtitleClick: (Subtitle) -> Unit
) {
    if (subtitles.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无字幕",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(subtitles, key = { _, sub -> sub.id }) { index, subtitle ->
            val isActive = index == activeIndex
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem()
                    .clickable(enabled = enabled) { onSubtitleClick(subtitle) }
                    .background(
                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = subtitle.textEn,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onBackground
                )
                subtitle.textCn?.takeIf { it.isNotBlank() }?.let { cn ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = cn,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
