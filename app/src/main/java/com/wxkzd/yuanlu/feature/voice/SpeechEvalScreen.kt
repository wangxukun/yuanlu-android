package com.wxkzd.yuanlu.feature.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxkzd.yuanlu.domain.model.EvalPhase
import com.wxkzd.yuanlu.feature.vocabulary.VocabularySheet
import com.wxkzd.yuanlu.ui.components.ErrorBox

/**
 * 语音评测页（复刻 Web ImmersiveSpeechPractice 沉浸式练习）：
 * 顶部导航（收起/标题/设置）+ 单句评测卡（录音 ⇄ 结果）+ 底部上一句/下一句与进度。
 * 由剧集详情页「语音评测」按钮进入。
 */
@Composable
fun SpeechEvalRoute(
    episodeid: String,
    onBack: () -> Unit,
    viewModel: SpeechEvalViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val wordSheet by viewModel.wordSheet.collectAsStateWithLifecycle()

    // Toast 一次性消费
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.toast.collect { message ->
            if (message != null) {
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                viewModel.consumeToast()
            }
        }
    }

    // 每次进入拉取练习数据（对齐 Web 挂载即取）
    LaunchedEffect(episodeid) { viewModel.load(episodeid) }

    // 麦克风运行时权限：请求通过后再启动录音
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startRecording()
        else android.widget.Toast.makeText(context, "无法访问麦克风，请检查权限设置", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun requestMicThenRecord() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startRecording()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    var showSettings by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ---- 顶部导航：收起 + 标题 + 设置 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "收起",
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = "语音评测",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = { showSettings = true }) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "语音评测设置",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // ---- 卡片区 ----
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.loadError != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    ErrorBox(message = state.loadError ?: "加载失败", onRetry = viewModel::retry)
                }
                state.subtitles.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "当前过滤条件下没有可练习的句子\n可在设置中放宽词数或关闭「只练未掌握」",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    SpeechEvalCard(
                        state = state,
                        onToggleRecording = {
                            if (state.phase == EvalPhase.RECORDING) viewModel.stopAndEvaluate() else requestMicThenRecord()
                        },
                        onAiReading = viewModel::toggleAiReading,
                        onPlayOriginal = viewModel::playOriginal,
                        onPlayUserAudio = { viewModel.playUserAudio() },
                        onRetryRecording = viewModel::retryRecording,
                        onSelectWord = viewModel::selectWord,
                        onPlayDictVoice = viewModel::playDictVoice,
                        onPlayWordOriginal = viewModel::playWordOriginal,
                        onPlayWordMe = viewModel::playWordMe,
                        onPrefetchIpa = viewModel::prefetchIpa,
                        onToggleBlindReveal = viewModel::toggleBlindReveal,
                        onShowLatestScore = viewModel::showLatestScore,
                        highlightPositionMs = viewModel.highlightPositionMs,
                        savedWords = viewModel.savedWords,
                        onWordClick = viewModel::onWordClick
                    )
                    if (state.isTrialMode && state.index == state.subtitles.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TrialUnlockCard()
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // ---- 底部导航：上一句 | 进度 | 下一句 ----
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = viewModel::prev,
                    enabled = state.index > 0 && state.phase != EvalPhase.EVALUATING
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("上一句")
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = "${state.index + 1} / ${state.subtitles.size}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { state.progressPercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "已练 ${state.practicedCount} 句",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = viewModel::next,
                    enabled = state.index < state.subtitles.lastIndex && state.phase != EvalPhase.EVALUATING
                ) {
                    Text("下一句")
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
    }

    if (showSettings) {
        SpeechSettingsSheet(
            state = state,
            onClose = { showSettings = false },
            onUpdateSettings = viewModel::updateSettings,
            onSetThemeMode = viewModel::setThemeMode
        )
    }

    // ---- 查词弹层（点词触发，与精听页共用同一组件与保存口径） ----
    wordSheet?.let { sheet ->
        VocabularySheet(
            sheet = sheet,
            episodeTitle = state.episodeTitle,
            onSave = viewModel::saveCurrentWord,
            onClose = viewModel::closeWordSheet
        )
    }
}

/** 非会员试用模式：末句后的解锁提示卡（Web "解锁全部练习句子" PRO 卡的移动端简化） */
@Composable
private fun TrialUnlockCard() {
    Surface(
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "解锁全部练习句子",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "免费用户每集可练习前 5 句\n升级会员解锁无限句子与评测次数",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
