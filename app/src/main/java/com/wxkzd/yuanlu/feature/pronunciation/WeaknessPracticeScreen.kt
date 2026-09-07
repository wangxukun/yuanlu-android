package com.wxkzd.yuanlu.feature.pronunciation

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxkzd.yuanlu.domain.model.EvalPhase
import com.wxkzd.yuanlu.feature.voice.SpeechEvalCard
import com.wxkzd.yuanlu.ui.components.ErrorBox

/**
 * 发音闯关复习页（复刻 Web /library/pronunciation/practice，参照设计稿图2）：
 * 顶栏（返回 + 标题携带进度 (1/N) + 已达标徽章）→ 进度条 →
 * 复用单句评测卡（AI朗读/原声/慢速 + 原句/翻译 + 点击录音 + 结果诊断）→
 * 底部上一题/下一题（末题为"完成复习"返回弱项本）。
 * PRO 锁定态与非会员 403 同屏处理；空弱项集展示"没有待复习的弱项"完成态。
 */
@Composable
fun WeaknessPracticeRoute(
    onExit: () -> Unit,
    viewModel: WeaknessPracticeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Toast 一次性消费
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.toast.collect { message ->
            if (message != null) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                viewModel.consumeToast()
            }
        }
    }

    // 麦克风运行时权限：请求通过后再启动录音
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startRecording()
        else Toast.makeText(context, "无法访问麦克风，请检查权限设置", Toast.LENGTH_SHORT).show()
    }

    fun requestMicThenRecord() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startRecording()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            state.isLocked -> LockedPane(onExit = onExit)
            state.loadError != null -> Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                ErrorBox(message = state.loadError ?: "加载失败", onRetry = viewModel::retry)
            }
            state.records.isEmpty() -> EmptyPane(onExit = onExit)
            else -> {
                // ---- 顶部导航：返回 + 发音闯关复习 (1/N) + 已达标徽章 ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(48.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                    Text(
                        text = "发音闯关复习 (${state.index + 1}/${state.records.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.isCompleted) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "已达标",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // ---- 闯关进度条 ----
                LinearProgressIndicator(
                    progress = { state.progressPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(6.dp),
                    strokeCap = StrokeCap.Round
                )

                // ---- 评测卡 ----
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    SpeechEvalCard(
                        state = state.card,
                        onToggleRecording = {
                            if (state.card.phase == EvalPhase.RECORDING) viewModel.stopAndEvaluate()
                            else requestMicThenRecord()
                        },
                        onAiReading = viewModel::toggleAiReading,
                        onPlayOriginal = viewModel::playOriginal,
                        onPlayUserAudio = { viewModel.playUserAudio() },
                        onRetryRecording = viewModel::retryRecording,
                        onSelectWord = viewModel::selectWord,
                        onPlayDictVoice = viewModel::playDictVoice,
                        onPlayWordOriginal = viewModel::playWordOriginal,
                        onPlayWordMe = viewModel::playWordMe,
                        onPrefetchIpa = { viewModel.prefetchIpa() },
                        onToggleBlindReveal = viewModel::toggleBlindReveal,
                        onShowLatestScore = viewModel::showLatestScore
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // ---- 底部导航：上一题 | 下一题/完成复习 ----
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = viewModel::prev,
                            enabled = state.index > 0 && state.card.phase != EvalPhase.EVALUATING
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("上一题")
                        }
                        Button(
                            onClick = {
                                if (state.isLast) onExit() else viewModel.next()
                            },
                            enabled = state.card.phase != EvalPhase.EVALUATING
                        ) {
                            Text(if (state.isLast) "完成复习" else "下一题")
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

/** PRO 会员锁定态（Web locked 页复刻：锁图标 + 说明 + 解锁按钮） */
@Composable
private fun LockedPane(onExit: () -> Unit, onUnlock: () -> Unit = onExit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "弱项练习是 PRO 会员功能",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "升级会员解锁发音诊断、弱项句子收录与针对性循环练习。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                Toast.makeText(context, "订阅功能即将上线", Toast.LENGTH_SHORT).show()
                onUnlock()
            },
            shape = RoundedCornerShape(50)
        ) {
            Text("解锁 PRO 会员", modifier = Modifier.padding(horizontal = 12.dp))
        }
        TextButton(onClick = onExit, modifier = Modifier.padding(top = 8.dp)) {
            Text("返回")
        }
    }
}

/** 空弱项集完成态（Web "没有待复习的弱项" 复刻） */
@Composable
private fun EmptyPane(onExit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "🎉", style = MaterialTheme.typography.displayMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "没有待复习的弱项",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "您的发音记录非常完美，继续保持！",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedButton(onClick = onExit, shape = RoundedCornerShape(50)) {
            Text("返回")
        }
    }
}
