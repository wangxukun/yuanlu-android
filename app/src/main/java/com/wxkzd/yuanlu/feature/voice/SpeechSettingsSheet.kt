package com.wxkzd.yuanlu.feature.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wxkzd.yuanlu.domain.model.PracticeSettings
import com.wxkzd.yuanlu.domain.model.PracticeStrictness
import com.wxkzd.yuanlu.domain.model.PracticeTextMode
import com.wxkzd.yuanlu.theme.ThemeMode

/**
 * 语音评测设置面板（复刻 Web PracticeSettingsPanel 的移动端下拉面板）：
 * 界面与显示 / 评测与弱项 / 声音与跟读 三组；写入 PracticeSettingsStore 即时生效。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeechSettingsSheet(
    state: SpeechEvalUiState,
    onClose: () -> Unit,
    onUpdateSettings: ((PracticeSettings) -> PracticeSettings) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settings = state.settings

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // ---- 标题行 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "语音评测设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }

            // ========== 组 1：界面与显示 ==========
            SettingsGroupTitle("界面与显示")

            SettingsRow(label = "深浅色切换", sublabel = "跟随应用主题即时切换") {
                Segmented(
                    options = listOf("跟随" to ThemeMode.SYSTEM, "浅色" to ThemeMode.LIGHT, "深色" to ThemeMode.DARK),
                    selected = state.themeMode,
                    onSelect = onSetThemeMode
                )
            }

            SettingsRow(label = "字幕字号", sublabel = "英文原句显示大小") {
                Stepper(
                    value = when (settings.fontSizeLevel) {
                        0 -> "小"
                        2 -> "大"
                        else -> "中"
                    },
                    onDecrement = { onUpdateSettings { it.copy(fontSizeLevel = (it.fontSizeLevel - 1).coerceAtLeast(0)) } },
                    onIncrement = { onUpdateSettings { it.copy(fontSizeLevel = (it.fontSizeLevel + 1).coerceAtMost(2)) } }
                )
            }

            SettingsRow(label = "显示中文翻译") {
                Switch(
                    checked = settings.showTranslation,
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(showTranslation = checked) } }
                )
            }

            SettingsRow(label = "显示音标 IPA", sublabel = "结果区的音素标注") {
                Switch(
                    checked = settings.showIpa,
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(showIpa = checked) } }
                )
            }

            SettingsRow(label = "文本模式") {
                Segmented(
                    options = PracticeTextMode.entries.map { it.label to it },
                    selected = settings.textMode,
                    onSelect = { mode -> onUpdateSettings { it.copy(textMode = mode) } }
                )
            }

            SettingsDivider()

            // ========== 组 2：评测与弱项 ==========
            SettingsGroupTitle("评测与弱项")

            SettingsRow(label = "过关分数线", sublabel = "生效线 ${settings.effectivePassThreshold} 分（含严格度偏移）") {
                Stepper(
                    value = "${settings.passThreshold}",
                    onDecrement = { onUpdateSettings { it.copy(passThreshold = (it.passThreshold - 5).coerceAtLeast(60)) } },
                    onIncrement = { onUpdateSettings { it.copy(passThreshold = (it.passThreshold + 5).coerceAtMost(95)) } }
                )
            }

            SettingsRow(label = "评测严格度") {
                Segmented(
                    options = PracticeStrictness.entries.map { it.label to it },
                    selected = settings.strictness,
                    onSelect = { s -> onUpdateSettings { it.copy(strictness = s) } }
                )
            }

            SettingsRow(label = "弱项本分数线", sublabel = "低于该线的句子进入弱项本") {
                Stepper(
                    value = "${settings.weakThreshold}",
                    onDecrement = { onUpdateSettings { it.copy(weakThreshold = (it.weakThreshold - 5).coerceAtLeast(60)) } },
                    onIncrement = { onUpdateSettings { it.copy(weakThreshold = (it.weakThreshold + 5).coerceAtMost(95)) } }
                )
            }

            SettingsRow(label = "句子最小词数") {
                Stepper(
                    value = if (settings.minWords == 0) "不限" else "${settings.minWords}",
                    onDecrement = { onUpdateSettings { it.copy(minWords = (it.minWords - 5).coerceAtLeast(0)) } },
                    onIncrement = { onUpdateSettings { it.copy(minWords = (it.minWords + 5).coerceAtMost(50)) } }
                )
            }

            SettingsRow(label = "句子最大词数") {
                Stepper(
                    value = if (settings.maxWords >= 50) "不限" else "${settings.maxWords}",
                    onDecrement = { onUpdateSettings { it.copy(maxWords = (it.maxWords - 5).coerceAtLeast(0)) } },
                    onIncrement = { onUpdateSettings { it.copy(maxWords = (it.maxWords + 5).coerceAtMost(50)) } }
                )
            }

            SettingsRow(label = "只练未掌握", sublabel = "过滤掉最新得分已达生效线的句子") {
                Switch(
                    checked = settings.onlyUnmastered,
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(onlyUnmastered = checked) } }
                )
            }

            SettingsDivider()

            // ========== 组 3：声音与跟读 ==========
            SettingsGroupTitle("声音与跟读")

            SettingsRow(label = "自动跳下一句", sublabel = "得分达到生效线后 1.5 秒自动切换") {
                Switch(
                    checked = settings.autoAdvance,
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(autoAdvance = checked) } }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "设置即时保存，影响本集句子过滤与过关判定",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ---------- 设置面板原子控件（对齐 Web SettingsControls） ----------

@Composable
private fun SettingsGroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    )
}

@Composable
private fun SettingsRow(
    label: String,
    sublabel: String? = null,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            if (sublabel != null) {
                Text(
                    text = sublabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        trailing()
    }
}

/** - 值 + 分段选择（选中项 primary 底白字） */
@Composable
private fun <T> Segmented(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(3.dp)
    ) {
        options.forEach { (label, value) ->
            val isSelected = value == selected
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                    .clickable { onSelect(value) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun Stepper(
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircleIconButton(onClick = onDecrement) { Icon(Icons.Filled.Remove, contentDescription = "减少", modifier = Modifier.size(15.dp)) }
        Box(
            modifier = Modifier
                .width(52.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        CircleIconButton(onClick = onIncrement) { Icon(Icons.Filled.Add, contentDescription = "增加", modifier = Modifier.size(15.dp)) }
    }
}

@Composable
private fun CircleIconButton(onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { icon() }
}
