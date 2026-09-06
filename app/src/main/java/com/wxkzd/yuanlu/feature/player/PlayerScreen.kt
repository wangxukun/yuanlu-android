package com.wxkzd.yuanlu.feature.player

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wxkzd.yuanlu.core.auth.Permissions
import com.wxkzd.yuanlu.domain.model.Comment
import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.Subtitle
import com.wxkzd.yuanlu.ui.components.BookmarkBorderIcon
import com.wxkzd.yuanlu.ui.components.BookmarkIcon
import com.wxkzd.yuanlu.ui.components.CoverImage
import com.wxkzd.yuanlu.ui.components.DescriptionIcon
import com.wxkzd.yuanlu.ui.components.DownloadIcon
import com.wxkzd.yuanlu.ui.components.EmptyBox
import com.wxkzd.yuanlu.ui.components.ErrorBox
import com.wxkzd.yuanlu.ui.components.HeadsetIcon
import com.wxkzd.yuanlu.ui.components.LanguagesIcon
import com.wxkzd.yuanlu.ui.components.LoadingBox
import com.wxkzd.yuanlu.ui.components.MicIcon
import com.wxkzd.yuanlu.ui.components.ScheduleIcon
import com.wxkzd.yuanlu.ui.components.TvIcon
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// ---------- 远路 Web 端色板（globals.css）已抽至 PlayerPalette.kt，播放器相关页面共用 ----------

// ---------- 深浅色适配：详情页中性色按外观设置取值（修复深色模式仍为浅色底/浅色字的问题） ----------
// 浅色沿用 Web 暖纸 ink 阶梯；深色取反阶梯（背景 ink-950、分隔 ink-800、文字 ink-50/300/400）。

@Composable
private fun pageBg(): Color = if (isDarkAppearance()) Ink950 else Ink50

@Composable
private fun dividerColor(): Color = if (isDarkAppearance()) Ink800 else Ink100

@Composable
private fun softLine(): Color = if (isDarkAppearance()) Ink700 else Ink200

@Composable
private fun hintColor(): Color = if (isDarkAppearance()) Ink500 else Ink400

@Composable
private fun subTextColor(): Color = if (isDarkAppearance()) Ink400 else Ink500

@Composable
private fun bodyTextColor(): Color = if (isDarkAppearance()) Ink300 else Ink600

@Composable
private fun strongTextColor(): Color = if (isDarkAppearance()) Ink300 else Ink700

@Composable
private fun titleTextColor(): Color = if (isDarkAppearance()) Ink50 else Ink900

@Composable
private fun brandPrimary(): Color = if (isDarkAppearance()) Primary400 else Primary600

@Composable
private fun transcriptSheetBg(): Color = if (isDarkAppearance()) Ink900 else Color.White


@Composable
fun PlayerRoute(
    episodeid: String,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onOpenPodcast: (String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    onOpenSpeechEval: (String) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    LaunchedEffect(episodeid) {
        viewModel.load(episodeid)
    }
    PlayerScreen(
        onBack = onBack,
        onLogin = onLogin,
        onOpenPodcast = onOpenPodcast,
        onOpenEpisode = onOpenEpisode,
        onOpenSpeechEval = onOpenSpeechEval,
        viewModel = viewModel
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onOpenPodcast: (String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    onOpenSpeechEval: (String) -> Unit,
    viewModel: PlayerViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val subtitles by viewModel.subtitles.collectAsStateWithLifecycle()
    val activeSubtitleIndex by viewModel.activeSubtitleIndex.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg())
            .statusBarsPadding()
    ) {
        when {
            uiState.isLoading -> LoadingBox()
            uiState.error != null -> ErrorBox(message = uiState.error!!, onRetry = viewModel::retry)
            uiState.episode == null -> EmptyBox("未找到该剧集")
            else -> {
                val episode = uiState.episode!!
                val isPlayingThis =
                    playerState.currentEpisode?.episodeid == episode.episodeid && playerState.isPlaying

                EpisodeDetailContent(
                    state = uiState,
                    episode = episode,
                    isPlayingThis = isPlayingThis,
                    onBack = onBack,
                    onLogin = onLogin,
                    onOpenPodcast = onOpenPodcast,
                    onOpenEpisode = onOpenEpisode,
                    onOpenSpeechEval = onOpenSpeechEval,
                    onTogglePlayback = viewModel::togglePlayback,
                    onStartListening = viewModel::startIntensiveListening,
                    onOpenTranscript = { viewModel.setTranscriptOpen(true) },
                    onTranslateTitle = viewModel::translateTitle,
                    onTranslateDescription = viewModel::translateDescription,
                    onSubmitComment = viewModel::submitComment,
                    onToggleLike = viewModel::toggleCommentLike,
                    onToggleFavorite = viewModel::toggleFavorite
                )
            }
        }
    }

    // ---- 文稿（双语字幕）弹层 ----
    if (uiState.isTranscriptOpen && uiState.episode != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setTranscriptOpen(false) },
            containerColor = transcriptSheetBg()
        ) {
            TranscriptSheet(
                subtitles = subtitles,
                activeIndex = activeSubtitleIndex,
                audioUnavailable = uiState.audioUrl.isNullOrBlank(),
                isGuest = !uiState.isLoggedIn,
                onLogin = onLogin,
                onSubtitleClick = viewModel::seekToSubtitle,
                onClose = { viewModel.setTranscriptOpen(false) }
            )
        }
    }
}

/**
 * 剧集详情页（对齐 Web 端 episode/[id] 移动端布局）：
 * Hero 封面（角标+播放键）→ 元信息/标题（翻译）/播客链接 → 开始精听/语音评测 +
 * 音频/文稿/收藏/分享 → 节目介绍（翻译+显示全部）→ 互动讨论 → 相关剧集/查看更多。
 */
@Composable
private fun EpisodeDetailContent(
    state: EpisodeDetailUiState,
    episode: Episode,
    isPlayingThis: Boolean,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onOpenPodcast: (String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    onOpenSpeechEval: (String) -> Unit,
    onTogglePlayback: () -> Unit,
    onStartListening: () -> Unit,
    onOpenTranscript: () -> Unit,
    onTranslateTitle: () -> Unit,
    onTranslateDescription: () -> Unit,
    onSubmitComment: (String, Int?) -> Unit,
    onToggleLike: (Int) -> Unit,
    onToggleFavorite: () -> Unit
) {
    val context = LocalContext.current
    // 权限口径统一走 Permissions（yuanlu guard.ts 客户端复刻）：
    // 锁图标只用于会员专属剧集 + 已登录的非会员；游客/会员显示正向图标，点击时引导
    val isLocked = Permissions.isExclusiveLockedForUser(episode.isExclusive, state.isLoggedIn, state.userRole)
    val isMember = Permissions.isMember(state.userRole)
    val audioUnavailable = state.audioUrl.isNullOrBlank()

    fun handlePlayTap(listen: Boolean) {
        when {
            // 会员专属剧集（checkExclusivePlay 口径）：游客→登录引导；已登录非会员→升级提示
            episode.isExclusive && !state.isLoggedIn -> onLogin()
            isLocked -> Toast.makeText(context, "PRO剧集仅对会员开放", Toast.LENGTH_SHORT).show()
            // 非专属剧集人人可播：游客无音频直链，直接进入 3 分钟字幕试看
            !state.isLoggedIn -> onStartListening()
            audioUnavailable -> Toast.makeText(context, "音频暂不可用，请稍后重试", Toast.LENGTH_SHORT).show()
            else -> if (listen) onStartListening() else onTogglePlayback()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // ---- 顶部导航 ----
        item(key = "topbar") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = strongTextColor())
                }
                Text(
                    text = "剧集详情",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = titleTextColor(),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(48.dp))
            }
        }

        // ---- Hero：16:9 封面 + 角标 + 播放键 ----
        item(key = "hero") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, dividerColor(), RoundedCornerShape(16.dp))
                    .clickable { handlePlayTap(listen = false) }
            ) {
                CoverImage(
                    url = episode.coverUrl,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 16.dp,
                    fallbackUrl = episode.coverFallbackUrl
                )
                // 左上：PRO + 播放数
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (episode.isExclusive) ProBadge()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color(0xCC14141E), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = HeadsetIcon,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = String.format(Locale.US, "%,d", episode.playCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
                // 右上：难度
                episode.difficulty?.takeIf { it.isNotBlank() }?.let { level ->
                    Text(
                        text = level,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = difficultyColor(level),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(Color(0xF2FFFFFF), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                // 右下：56dp 圆形播放键
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(56.dp)
                        .background(brandPrimary(), CircleShape)
                        .clickable { handlePlayTap(listen = false) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isPlayingThis -> Icons.Filled.Pause
                            isLocked -> Icons.Filled.Lock
                            else -> Icons.Filled.PlayArrow
                        },
                        contentDescription = if (isPlayingThis) "暂停" else "播放",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // ---- 元信息：标签 / 日期 / 时长 ----
        item(key = "meta") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                episode.tags.firstOrNull()?.let { tag ->
                    Text(
                        text = tag.name.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = brandPrimary(),
                        modifier = Modifier
                            .background(brandPrimary().copy(alpha = 0.05f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.DateRange, contentDescription = null, tint = subTextColor(), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = episode.publishAt?.let { formatChineseDate(it) } ?: "未知日期",
                        style = MaterialTheme.typography.bodySmall,
                        color = subTextColor()
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(ScheduleIcon, contentDescription = null, tint = subTextColor(), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${episode.duration / 60}分钟",
                        style = MaterialTheme.typography.bodySmall,
                        color = subTextColor()
                    )
                }
            }
        }

        // ---- 标题 + 翻译 ----
        item(key = "title") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = titleTextColor()
                    )
                    state.translatedTitle?.let { translated ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = translated,
                            style = MaterialTheme.typography.titleSmall,
                            color = bodyTextColor()
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                TranslateButton(
                    busy = state.isTranslatingTitle,
                    active = state.translatedTitle != null,
                    // 游客点翻译直接引导登录（对齐 Web 未登录弹登录框）
                    onClick = { if (!state.isLoggedIn) onLogin() else onTranslateTitle() }
                )
            }
        }

        // ---- 所属播客 ----
        item(key = "podcast_link") {
            episode.podcastid?.let { podcastid ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(start = 16.dp, top = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenPodcast(podcastid) }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Icon(TvIcon, contentDescription = null, tint = brandPrimary(), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = episode.podcastTitle ?: "远路英语",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = brandPrimary()
                    )
                }
            }
        }

        // ---- 操作区：开始精听 / 语音评测 + 音频 / 文稿 / 收藏 / 分享 ----
        item(key = "actions") {
            ActionSection(
                isPlayingThis = isPlayingThis,
                isLocked = isLocked,
                isFavorited = state.isFavorited,
                isFavoriteBusy = state.isFavoriteBusy,
                onListening = { handlePlayTap(listen = true) },
                // 语音评测（对齐 Web handleStartPractice）：游客→登录引导；专属+非会员→升级提示；其余进入评测页
                onPractice = {
                    when {
                        !state.isLoggedIn -> onLogin()
                        isLocked -> Toast.makeText(context, "语音评测仅对会员开放，升级会员即可解锁", Toast.LENGTH_SHORT).show()
                        else -> onOpenSpeechEval(episode.episodeid)
                    }
                },
                // 音频下载（对齐 Web handleDownloadAudio）：游客/非会员给权限提示，会员提示功能排期
                onDownloadAudio = {
                    when {
                        !state.isLoggedIn -> Toast.makeText(context, "音频下载仅对会员开放", Toast.LENGTH_SHORT).show()
                        !isMember -> Toast.makeText(context, "音频下载仅对会员开放，升级会员即可解锁", Toast.LENGTH_SHORT).show()
                        else -> Toast.makeText(context, "音频下载功能即将上线", Toast.LENGTH_SHORT).show()
                    }
                },
                // 文稿（对齐 Web）：游客→权限提示；登录用户打开双语文稿（非会员为预览口径）
                onTranscript = {
                    if (!state.isLoggedIn) {
                        Toast.makeText(context, "文稿下载仅对会员开放", Toast.LENGTH_SHORT).show()
                    } else {
                        onOpenTranscript()
                    }
                },
                // 收藏（对齐 Web handleToggleFavorite）：游客先登录，登录后走 FavoriteCenter
                onFavorite = {
                    if (!state.isLoggedIn) {
                        Toast.makeText(context, "请先登录后收藏", Toast.LENGTH_SHORT).show()
                    } else {
                        onToggleFavorite()
                    }
                },
                onShare = {
                    val text = "剧集「${episode.title}」- ${episode.podcastTitle ?: "远路英语"}"
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "分享剧集"))
                }
            )
        }

        // ---- 节目介绍（翻译 + 显示全部/收起） ----
        item(key = "show_notes") {
            ShowNotesSection(
                description = episode.description,
                translated = state.translatedDesc,
                isTranslating = state.isTranslatingDesc,
                onTranslate = { if (!state.isLoggedIn) onLogin() else onTranslateDescription() }
            )
        }

        // ---- 互动讨论 ----
        item(key = "comments_header") {
            Text(
                text = "互动讨论",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = titleTextColor(),
                modifier = Modifier.padding(start = 16.dp, top = 32.dp, bottom = 8.dp)
            )
        }
        item(key = "comment_form") {
            if (state.isLoggedIn) {
                CommentForm(
                    isSubmitting = state.isSubmittingComment,
                    onSubmit = { onSubmitComment(it, null) }
                )
            } else {
                LoginPromptBox(onLogin = onLogin)
            }
        }
        item(key = "comments_list") {
            CommentsList(
                comments = state.comments,
                isLoading = state.isLoadingComments,
                onToggleLike = onToggleLike,
                onReply = { content, parentId -> onSubmitComment(content, parentId) }
            )
        }

        // ---- 相关剧集 + 查看更多 ----
        if (state.relatedEpisodes.isNotEmpty()) {
            item(key = "related_header") {
                Text(
                    text = "相关剧集",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = titleTextColor(),
                    modifier = Modifier.padding(start = 16.dp, top = 32.dp, bottom = 8.dp)
                )
            }
            itemsIndexed(state.relatedEpisodes, key = { _, ep -> "related_${ep.episodeid}" }) { index, ep ->
                RelatedEpisodeRow(
                    episode = ep,
                    index = index,
                    podcastTitle = episode.podcastTitle,
                    onClick = { onOpenEpisode(ep.episodeid) },
                    onAdd = {
                        Toast.makeText(context, "播放队列功能即将上线", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            item(key = "related_more") {
                val podcastid = episode.podcastid
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(brandPrimary().copy(alpha = 0.05f))
                        .border(1.dp, brandPrimary().copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .clickable { podcastid?.let(onOpenPodcast) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "查看更多内容",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = brandPrimary(),
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}

/** 翻译按钮：Languages 图标 / 加载中转圈；已有译文时高亮 */
@Composable
private fun TranslateButton(busy: Boolean, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) brandPrimary().copy(alpha = 0.1f) else Color.Transparent)
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = brandPrimary())
        } else {
            Icon(LanguagesIcon, contentDescription = "翻译", tint = if (active) brandPrimary() else hintColor(), modifier = Modifier.size(20.dp))
        }
    }
}

/** PRO 专属徽章：曙光橙描边小胶囊（对齐 Web 端 ProBadge） */
@Composable
private fun ProBadge() {
    Text(
        text = "PRO",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
        color = Accent700,
        letterSpacing = 1.5.sp,
        modifier = Modifier
            .border(1.dp, Accent300, RoundedCornerShape(50))
            .background(Accent100.copy(alpha = 0.95f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 2.dp)
    )
}

/** 难度配色，对齐 Web 端 lib/difficulty.ts：A 远青 / B1 黛蓝 / B2 曙光橙 / C 陶土红 */
private fun difficultyColor(level: String): Color = when {
    level.contains("A") -> Color(0xFF1F7A5C)
    level.contains("B1") -> Color(0xFF4A7FA5)
    level.contains("B2") -> Color(0xFFB96F0F)
    level.contains("C") -> Color(0xFFD2503F)
    else -> Color(0xFF44403C)
}

/** 操作区：上排主按钮（开始精听/语音评测），下排图标按钮（音频/文稿/收藏/分享） */
@Composable
private fun ActionSection(
    isPlayingThis: Boolean,
    isLocked: Boolean,
    isFavorited: Boolean,
    isFavoriteBusy: Boolean,
    onListening: () -> Unit,
    onPractice: () -> Unit,
    onDownloadAudio: () -> Unit,
    onTranscript: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        HorizontalDivider(color = dividerColor())
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PrimaryCtaButton(
                icon = when {
                    isPlayingThis -> Icons.Filled.Pause
                    isLocked -> Icons.Filled.Lock
                    else -> Icons.Filled.PlayArrow
                },
                label = if (isPlayingThis) "暂停" else "开始精听",
                container = brandPrimary(),
                onClick = onListening,
                modifier = Modifier.weight(1f)
            )
            PrimaryCtaButton(
                // 语音评测：仅"专属剧集+已登录非会员"显示锁，其余恒为麦克风
                icon = if (isLocked) Icons.Filled.Lock else MicIcon,
                label = "语音评测",
                container = Accent500,
                onClick = onPractice,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconActionBox(icon = DownloadIcon, label = "音频", onClick = onDownloadAudio, modifier = Modifier.weight(1f))
            IconActionBox(icon = DescriptionIcon, label = "文稿", onClick = onTranscript, modifier = Modifier.weight(1f))
            IconActionBox(
                icon = if (isFavorited) BookmarkIcon else BookmarkBorderIcon,
                label = "收藏",
                onClick = onFavorite,
                enabled = !isFavoriteBusy,
                tint = if (isFavorited) brandPrimary() else subTextColor(),
                modifier = Modifier.weight(1f)
            )
            IconActionBox(icon = Icons.Filled.Share, label = "分享", onClick = onShare, modifier = Modifier.weight(1f))
        }
        HorizontalDivider(color = dividerColor())
    }
}

@Composable
private fun PrimaryCtaButton(
    icon: ImageVector,
    label: String,
    container: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun IconActionBox(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = subTextColor()
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, dividerColor(), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.5f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** 节目介绍：3 行截断 + 显示全部/收起内容 + 简介 translate 按钮 */
@Composable
private fun ShowNotesSection(
    description: String?,
    translated: String?,
    isTranslating: Boolean,
    onTranslate: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var truncated by remember { mutableStateOf(false) }
    val display = translated ?: (description?.takeIf { it.isNotBlank() } ?: return)

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "节目介绍",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = titleTextColor(),
                modifier = Modifier.weight(1f)
            )
            TranslateButton(busy = isTranslating, active = translated != null, onClick = onTranslate)
        }
        HorizontalDivider(
            color = softLine(),
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
        )
        Text(
            text = display,
            style = MaterialTheme.typography.bodyLarge,
            color = bodyTextColor(),
            lineHeight = 28.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) truncated = it.hasVisualOverflow }
        )
        if (!expanded && truncated) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(pageBg())
                        .border(1.dp, dividerColor(), RoundedCornerShape(50))
                        .clickable { expanded = true }
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "显示全部",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = brandPrimary()
                    )
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = brandPrimary(), modifier = Modifier.size(16.dp))
                }
            }
        } else if (expanded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.CenterHorizontally)
                    .clickable { expanded = false }
            ) {
                Text(
                    text = "收起内容",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = brandPrimary()
                )
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = brandPrimary(), modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** 未登录：虚线引导框（对齐 Web 端 CommentForm 未登录态） */
@Composable
private fun LoginPromptBox(onLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(1.dp, softLine(), RoundedCornerShape(16.dp))
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(dividerColor(), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = hintColor(), modifier = Modifier.size(24.dp))
        }
        Text(
            text = "登录后参与讨论，记录你的学习点滴",
            style = MaterialTheme.typography.bodyMedium,
            color = subTextColor()
        )
        Button(
            onClick = onLogin,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = brandPrimary())
        ) {
            Text(text = "立即登录", fontWeight = FontWeight.Bold)
        }
    }
}

/** 评论输入：头像 + 多行输入 + 字数 + 发布 */
@Composable
private fun CommentForm(
    isSubmitting: Boolean,
    onSubmit: (String) -> Unit
) {
    var content by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(brandPrimary().copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "我",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = brandPrimary()
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = content,
                onValueChange = { if (it.length <= 500) content = it },
                placeholder = { Text("分享你的见解或疑问...") },
                shape = RoundedCornerShape(16.dp),
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 110.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (content.isNotEmpty()) {
                    Text(
                        text = "${content.length}",
                        style = MaterialTheme.typography.labelSmall,
                        color = hintColor()
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Button(
                    onClick = {
                        onSubmit(content)
                        content = ""
                    },
                    enabled = content.isNotBlank() && !isSubmitting,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = brandPrimary())
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("发布", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentsList(
    comments: List<Comment>,
    isLoading: Boolean,
    onToggleLike: (Int) -> Unit,
    onReply: (String, Int) -> Unit
) {
    when {
        isLoading -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = brandPrimary().copy(alpha = 0.4f))
        }
        comments.isEmpty() -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "还没有人发言，来抢沙发吧！",
                style = MaterialTheme.typography.bodyMedium,
                color = hintColor()
            )
        }
        else -> Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            comments.forEachIndexed { index, comment ->
                CommentItem(
                    comment = comment,
                    onToggleLike = onToggleLike,
                    onReply = onReply
                )
                if (index < comments.lastIndex) {
                    HorizontalDivider(color = dividerColor().copy(alpha = 0.6f))
                }
            }
        }
    }
}

/** 单条评论：头像 / 昵称 / 时间 / 气泡 / 点赞 / 回复（含嵌套回复） */
@Composable
private fun CommentItem(
    comment: Comment,
    onToggleLike: (Int) -> Unit,
    onReply: (String, Int) -> Unit
) {
    var replying by remember { mutableStateOf(false) }
    var replyContent by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(url = comment.avatarUrl, name = comment.nickname, size = 40)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = comment.nickname ?: "远路学友",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = strongTextColor(),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = comment.commentAt?.let { formatCommentDate(it) } ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = hintColor()
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                // 气泡：左上直角、其余大圆角
                Text(
                    text = comment.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = strongTextColor(),
                    modifier = Modifier
                        .background(dividerColor().copy(alpha = 0.55f), RoundedCornerShape(topStart = 2.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                        .padding(12.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.ThumbUp,
                        contentDescription = "点赞",
                        tint = if (comment.isLiked) brandPrimary() else hintColor(),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onToggleLike(comment.commentid) }
                    )
                    if (comment.likesCount > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${comment.likesCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (comment.isLiked) brandPrimary() else hintColor()
                        )
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(
                        text = "回复",
                        style = MaterialTheme.typography.labelSmall,
                        color = hintColor(),
                        modifier = Modifier.clickable { replying = !replying }
                    )
                }

                // 回复输入
                if (replying) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = replyContent,
                            onValueChange = { replyContent = it },
                            placeholder = { Text("回复 ${comment.nickname ?: "TA"}...") },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                onReply(replyContent, comment.commentid)
                                replyContent = ""
                                replying = false
                            },
                            enabled = replyContent.isNotBlank()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送回复", tint = brandPrimary())
                        }
                    }
                }

                // 嵌套回复
                comment.replies.forEach { reply ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Avatar(url = reply.avatarUrl, name = reply.nickname, size = 28)
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = reply.nickname ?: "远路学友",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = strongTextColor(),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = reply.commentAt?.let { formatCommentDate(it) } ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = hintColor()
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = reply.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = strongTextColor(),
                                modifier = Modifier
                                    .background(dividerColor().copy(alpha = 0.55f), RoundedCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                                    .padding(10.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Icon(
                                imageVector = Icons.Filled.ThumbUp,
                                contentDescription = "点赞",
                                tint = if (reply.isLiked) brandPrimary() else hintColor(),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onToggleLike(reply.commentid) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 头像：有图用图，无图取昵称首字 */
@Composable
private fun Avatar(url: String?, name: String?, size: Int) {
    val shape = CircleShape
    if (!url.isNullOrBlank() && url.startsWith("http")) {
        AsyncImage(
            model = url,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(shape)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .background(softLine(), shape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name?.take(1)?.uppercase() ?: "远",
                style = if (size >= 40) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = strongTextColor()
            )
        }
    }
}

/** 相关剧集行：16:9 封面 + EPISODE n + 标题 + 播客名 + 加入列表按钮 */
@Composable
private fun RelatedEpisodeRow(
    episode: Episode,
    index: Int,
    podcastTitle: String?,
    onClick: () -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(112.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            CoverImage(
                url = episode.coverUrl,
                contentDescription = episode.title,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 12.dp,
                fallbackUrl = episode.coverFallbackUrl
            )
            if (episode.isExclusive) {
                Box(modifier = Modifier.padding(6.dp)) {
                    ProBadge()
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "EPISODE ${index + 1}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = brandPrimary(),
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = strongTextColor(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(TvIcon, contentDescription = null, tint = hintColor(), modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = podcastTitle ?: "远路英语",
                    style = MaterialTheme.typography.labelSmall,
                    color = hintColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = "加入播放列表", tint = hintColor())
        }
    }
}

/** 文稿弹层：双语字幕，当前句高亮、点击跳播；音频不可用时给出引导 */
@Composable
private fun TranscriptSheet(
    subtitles: List<Subtitle>,
    activeIndex: Int,
    audioUnavailable: Boolean,
    isGuest: Boolean,
    onLogin: () -> Unit,
    onSubtitleClick: (Subtitle) -> Unit,
    onClose: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            listState.animateScrollToItem(activeIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "文稿",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = titleTextColor(),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "收起",
                style = MaterialTheme.typography.labelLarge,
                color = brandPrimary(),
                modifier = Modifier.clickable { onClose() }
            )
        }
        if (audioUnavailable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(brandPrimary().copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isGuest) "登录后可收听完整内容，当前仅展示前 3 分钟字幕。"
                    else "PRO剧集仅对会员开放，升级会员即可解锁收听。",
                    style = MaterialTheme.typography.bodySmall,
                    color = strongTextColor(),
                    modifier = Modifier.weight(1f)
                )
                if (isGuest) {
                    Text(
                        text = "立即登录",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = brandPrimary(),
                        modifier = Modifier.clickable { onLogin() }
                    )
                }
            }
        }
        if (subtitles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无字幕",
                    style = MaterialTheme.typography.bodyMedium,
                    color = hintColor()
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(subtitles, key = { _, sub -> sub.id }) { index, subtitle ->
                    val isActive = index == activeIndex
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !audioUnavailable) { onSubtitleClick(subtitle) }
                            .background(
                                color = if (isActive) brandPrimary().copy(alpha = 0.1f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = subtitle.textEn,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color = titleTextColor()
                        )
                        subtitle.textCn?.takeIf { it.isNotBlank() }?.let { cn ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = cn,
                                style = MaterialTheme.typography.bodyMedium,
                                color = bodyTextColor()
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------- 日期/文案格式化 ----------

/** "2026-08-01T12:00:00.000Z" -> "2026年8月1日"（对齐 Web formatChineseDate） */
private fun formatChineseDate(iso: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val formatter = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
        formatter.format(parser.parse(iso) ?: return iso.take(10))
    } catch (e: Exception) {
        iso.take(10)
    }
}

/** 评论时间："M月d日 HH:mm"（对齐 Web 端 zh-CN 本地化样式） */
private fun formatCommentDate(iso: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val formatter = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
        formatter.format(parser.parse(iso) ?: return "")
    } catch (e: Exception) {
        ""
    }
}
