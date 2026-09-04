package com.wxkzd.yuanlu.feature.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 编辑资料弹窗（复刻 Web EditProfileModal 的 BottomSheet 形态）：
 * 双 Tab——个人资料（头像/昵称/英语水平/个性签名）+ 学习目标（三项滑杆）。
 * 头像裁剪占位逻辑：Photo Picker 选图后做程序化居中正方形裁剪 + 压缩上传
 * （Web 端 react-easy-crop 交互式裁剪的端内等价占位，接入交互裁剪时替换
 * [cropSquareJpeg] 调用处即可）。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditProfileSheet(
    state: UserProfileUiState,
    onClose: () -> Unit,
    onSwitchTab: (EditProfileTab) -> Unit,
    onNicknameChange: (String) -> Unit,
    onBioChange: (String) -> Unit,
    onLearnLevelChange: (String) -> Unit,
    onDailyGoalChange: (Int) -> Unit,
    onWeeklyHoursChange: (Int) -> Unit,
    onWeeklyWordsChange: (Int) -> Unit,
    onAvatarPicked: (ByteArray?) -> Unit,
    onSave: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState
    ) {
        Column {
            // ---- 头部：品牌渐变 + 标题 + 关闭（Web gradient header） ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "编辑资料",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "关闭")
                }
            }

            // ---- Tab 切换（Web tabs-boxed 双 Tab） ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(4.dp)
            ) {
                EditSheetTab(
                    label = "个人资料",
                    icon = Icons.Filled.Person,
                    selected = state.editTab == EditProfileTab.PROFILE,
                    onClick = { onSwitchTab(EditProfileTab.PROFILE) },
                    modifier = Modifier.weight(1f)
                )
                EditSheetTab(
                    label = "学习目标",
                    icon = Icons.Filled.Tune,
                    selected = state.editTab == EditProfileTab.GOALS,
                    onClick = { onSwitchTab(EditProfileTab.GOALS) },
                    modifier = Modifier.weight(1f)
                )
            }

            // ---- 表单内容 ----
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                if (state.editTab == EditProfileTab.PROFILE) {
                    ProfileFormSection(
                        state = state,
                        onNicknameChange = onNicknameChange,
                        onBioChange = onBioChange,
                        onLearnLevelChange = onLearnLevelChange,
                        onAvatarPicked = onAvatarPicked
                    )
                } else {
                    GoalsFormSection(
                        state = state,
                        onDailyGoalChange = onDailyGoalChange,
                        onWeeklyHoursChange = onWeeklyHoursChange,
                        onWeeklyWordsChange = onWeeklyWordsChange
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ---- 底部操作区 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClose) { Text("取消") }
                Spacer(modifier = Modifier.width(12.dp))
                Button(onClick = onSave, enabled = !state.isSaving) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("保存所有更改")
                    }
                }
            }
        }
    }
}

@Composable
private fun EditSheetTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------- Tab 1：个人资料 ----------

@Composable
private fun ProfileFormSection(
    state: UserProfileUiState,
    onNicknameChange: (String) -> Unit,
    onBioChange: (String) -> Unit,
    onLearnLevelChange: (String) -> Unit,
    onAvatarPicked: (ByteArray?) -> Unit
) {
    AvatarPicker(state = state, onAvatarPicked = onAvatarPicked)

    Spacer(modifier = Modifier.height(16.dp))

    // 昵称（长度校验 + 计数）
    OutlinedTextField(
        value = state.formNickname,
        onValueChange = onNicknameChange,
        label = { Text("昵称") },
        placeholder = { Text("你的名字") },
        singleLine = true,
        isError = state.nicknameError != null,
        supportingText = {
            Row {
                Text(
                    text = state.nicknameError ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.nicknameError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${state.formNickname.length}/${ProfileUtils.NICKNAME_MAX_LENGTH}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(14.dp))

    // 英语水平（Web select 的移动端 FilterChip 等价）
    Text(
        text = "英语水平",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium
    )
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileUtils.LEARN_LEVELS.forEach { (value, label) ->
            FilterChip(
                selected = state.formLearnLevel == value,
                onClick = { onLearnLevelChange(value) },
                label = { Text(label) }
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 个性签名
    OutlinedTextField(
        value = state.formBio,
        onValueChange = onBioChange,
        label = { Text("个性签名") },
        placeholder = { Text("展示我的独特态度") },
        minLines = 3,
        isError = state.bioError != null,
        supportingText = {
            Row {
                Text(
                    text = state.bioError ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.bioError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${state.formBio.length}/${ProfileUtils.BIO_MAX_LENGTH}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

/** 头像选择与预览：Photo Picker → 程序化居中方形裁剪 → 表单预览（待上传字节） */
@Composable
private fun AvatarPicker(state: UserProfileUiState, onAvatarPicked: (ByteArray?) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val cropped = withContext(Dispatchers.IO) {
                    runCatching {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        bytes?.let { cropSquareJpeg(it) }
                    }.getOrNull()
                }
                onAvatarPicked(cropped)
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .border(
                    2.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    CircleShape
                )
                .clickable {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val avatarBytes = state.formAvatar
            val avatarUrl = state.profile?.avatarUrl
            if (avatarBytes != null) {
                val preview = remember(state.formAvatarVersion) {
                    BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.size)?.asImageBitmap()
                }
                if (preview != null) {
                    androidx.compose.foundation.Image(
                        bitmap = preview,
                        contentDescription = "avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else if (!avatarUrl.isNullOrBlank() && avatarUrl.startsWith("http")) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.size(48.dp)
                )
            }
            // 相机角标（Web hover 蒙层的移动端常驻等价）
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(30.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = "更换头像",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "点击更换头像",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ---------- Tab 2：学习目标 ----------

@Composable
private fun GoalsFormSection(
    state: UserProfileUiState,
    onDailyGoalChange: (Int) -> Unit,
    onWeeklyHoursChange: (Int) -> Unit,
    onWeeklyWordsChange: (Int) -> Unit
) {
    // 提示横幅（Web alert + FireIcon）
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "设定合理的每日目标有助于保持连胜纪录！",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    GoalSlider(
        title = "每日学习时长目标",
        valueLabel = "${state.formDailyGoalMins} 分钟",
        value = state.formDailyGoalMins.toFloat(),
        range = 10f..120f,
        steps = 21, // 10..120 步进 5 → 23 档
        minLabel = "10m",
        midLabel = "60m",
        maxLabel = "120m",
        onValueChange = { onDailyGoalChange((it / 5f).roundToInt() * 5) }
    )

    GoalSlider(
        title = "每周收听目标",
        valueLabel = "${state.formWeeklyHours} 小时",
        value = state.formWeeklyHours.toFloat(),
        range = 1f..20f,
        steps = 18, // 1..20 步进 1
        minLabel = "1h",
        midLabel = "10h",
        maxLabel = "20h",
        onValueChange = { onWeeklyHoursChange(it.roundToInt()) }
    )

    GoalSlider(
        title = "每周单词目标",
        valueLabel = "${state.formWeeklyWords} 个",
        value = state.formWeeklyWords.toFloat(),
        range = 10f..200f,
        steps = 37, // 10..200 步进 5 → 39 档
        minLabel = "10",
        midLabel = "100",
        maxLabel = "200",
        onValueChange = { onWeeklyWordsChange((it / 5f).roundToInt() * 5) }
    )
}

@Composable
private fun GoalSlider(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    minLabel: String,
    midLabel: String,
    maxLabel: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            // 当前值胶囊（Web primary-50 pill）
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        RoundedCornerShape(50)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps
        )
        // 刻度范围标注（Web range 下方的三段 label）
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = minLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.TopStart)
            )
            Text(
                text = midLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.TopCenter)
            )
            Text(
                text = maxLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
    }
}

/**
 * 头像裁剪占位：居中正方形裁剪 + 最长边 512px 下采样 + JPEG(88) 压缩。
 * Web 用 react-easy-crop 交互裁剪（圆形取景 + 缩放），端内先以程序化居中裁剪占位；
 * 后续接入交互式裁剪时仅需替换本函数。
 */
internal fun cropSquareJpeg(bytes: ByteArray, maxDim: Int = 512): ByteArray? {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val side = minOf(bitmap.width, bitmap.height)
    val xOffset = (bitmap.width - side) / 2
    val yOffset = (bitmap.height - side) / 2
    val square = Bitmap.createBitmap(bitmap, xOffset, yOffset, side, side)
    val scaled = if (side > maxDim) {
        val ratio = maxDim.toFloat() / side
        Bitmap.createScaledBitmap(
            square,
            (side * ratio).roundToInt(),
            (side * ratio).roundToInt(),
            true
        )
    } else {
        square
    }
    val output = java.io.ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 88, output)
    return output.toByteArray()
}
