package com.wxkzd.yuanlu.feature.learningpath

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxkzd.yuanlu.domain.model.LearningPathDetail
import com.wxkzd.yuanlu.domain.model.LearningPathEpisode
import com.wxkzd.yuanlu.ui.components.CoverImage
import com.wxkzd.yuanlu.ui.components.ErrorBox
import com.wxkzd.yuanlu.ui.components.LoadingBox
import com.wxkzd.yuanlu.ui.components.formatMillis

/**
 * 「某一路径」详情页（复刻 Web /library/learning-paths/[id]）：
 * 沉浸式绿色头部（封面/公开标签/标题/描述/创建者/集数）+ 悬浮操作栏
 * （播放全部/随机播放；拥有者额外有 添加剧集 与 编辑/删除/分享 菜单）+
 * 剧集列表（序号/封面/标题/播客名；拥有者可移除）。
 * 权限条件渲染：detail.isOwner == true 才出现管理入口（对齐 Web userid === currentUserId）。
 */
@Composable
fun LearningPathDetailRoute(
    pathId: Int,
    onBack: () -> Unit,
    onOpenEpisode: (String) -> Unit,
    viewModel: LearningPathDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val navigateBack by viewModel.navigateBack.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var isEditDialogOpen by remember { mutableStateOf(false) }
    var isDeleteConfirmOpen by remember { mutableStateOf(false) }
    var pendingRemoveItem by remember { mutableStateOf<LearningPathEpisode?>(null) }

    LaunchedEffect(pathId) { viewModel.load(pathId) }
    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }
    LaunchedEffect(navigateBack) {
        if (navigateBack) {
            viewModel.consumeNavigateBack()
            onBack()
        }
    }

    val detail = state.detail
    when {
        state.isLoading -> Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            DetailBackBar(onBack = onBack)
            LoadingBox()
        }
        state.error != null -> Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            DetailBackBar(onBack = onBack)
            ErrorBox(message = state.error!!, onRetry = viewModel::retry)
        }
        detail != null -> Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            PathDetailContent(
                detail = detail,
                onBack = onBack,
                onPlayAll = { viewModel.playAll(shuffle = false) },
                onShuffle = { viewModel.playAll(shuffle = true) },
                onOpenEpisode = onOpenEpisode,
                onAddEpisode = { viewModel.setAddDialogOpen(true) },
                onEdit = { isEditDialogOpen = true },
                onDelete = { isDeleteConfirmOpen = true },
                onRemoveEpisode = { pendingRemoveItem = it },
                onShare = { sharePathLink(context, detail.pathid) }
            )
        }
    }

    // ---- 添加剧集弹窗（仅拥有者入口可见） ----
    if (state.isAddDialogOpen && detail != null) {
        AddEpisodeDialog(
            state = state,
            addedEpisodeIds = detail.items.map { it.episode.episodeid }.toSet(),
            onQueryChange = viewModel::updateSearchQuery,
            onAdd = viewModel::addEpisode,
            onDismiss = { viewModel.setAddDialogOpen(false) }
        )
    }

    // ---- 编辑路径弹窗 ----
    if (isEditDialogOpen && detail != null) {
        LearningPathFormDialog(
            title = "编辑路径",
            submitLabel = "保存",
            isSubmitting = state.isSaving,
            initialName = detail.pathName,
            initialDescription = detail.description.orEmpty(),
            initialPublic = detail.isPublic,
            publicHint = "允许其他用户查看这条学习路径",
            onDismiss = { isEditDialogOpen = false },
            onSubmit = { name, description, isPublic ->
                viewModel.updatePath(name, description, isPublic) { isEditDialogOpen = false }
            }
        )
    }

    // ---- 删除路径确认 ----
    if (isDeleteConfirmOpen && detail != null) {
        AlertDialog(
            onDismissRequest = { isDeleteConfirmOpen = false },
            title = { Text("删除学习路径", fontWeight = FontWeight.Bold) },
            text = { Text("确定删除「${detail.pathName}」吗？该操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDeleteConfirmOpen = false
                        viewModel.deletePath()
                    },
                    enabled = !state.isMutating
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isDeleteConfirmOpen = false }) { Text("取消") }
            }
        )
    }

    // ---- 移除剧集确认 ----
    pendingRemoveItem?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingRemoveItem = null },
            title = { Text("移除剧集", fontWeight = FontWeight.Bold) },
            text = { Text("确定从播放列表中移除「${item.episode.title}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeEpisode(item.itemId)
                        pendingRemoveItem = null
                    },
                    enabled = !state.isMutating
                ) {
                    Text("移除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveItem = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun DetailBackBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
    }
}

@Composable
private fun PathDetailContent(
    detail: LearningPathDetail,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onOpenEpisode: (String) -> Unit,
    onAddEpisode: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRemoveEpisode: (LearningPathEpisode) -> Unit,
    onShare: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "header") {
            PathHeader(detail = detail, onBack = onBack)
        }
        item(key = "action_bar") {
            PathActionBar(
                detail = detail,
                onPlayAll = onPlayAll,
                onShuffle = onShuffle,
                onAddEpisode = onAddEpisode,
                onEdit = onEdit,
                onDelete = onDelete,
                onShare = onShare
            )
        }
        if (detail.items.isEmpty()) {
            item(key = "empty_episodes") { PathEmptyEpisodes(isOwner = detail.isOwner, onAddEpisode = onAddEpisode) }
        } else {
            itemsIndexed(detail.items, key = { _, item -> "item_${item.itemId}" }) { index, item ->
                PathEpisodeRow(
                    index = index,
                    item = item,
                    isOwner = detail.isOwner,
                    onClick = { onOpenEpisode(item.episode.episodeid) },
                    onRemove = { onRemoveEpisode(item) }
                )
            }
        }
    }
}

/** 加载/错误态的返回栏（沉浸态的返回按钮在绿色头部内，白色） */
@Composable
private fun PathHeader(detail: LearningPathDetail, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
    ) {
        // 装饰性大图标水印（对齐 Web 右上角 10% 透明度 Map 巨标）
        Icon(
            imageVector = Icons.Filled.Map,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.06f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 56.dp, y = (-56).dp)
                .size(280.dp)
        )
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 封面：第一集封面（无剧集走字母占位），白色细描边
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                ) {
                    CoverImage(
                        url = detail.coverUrl,
                        contentDescription = detail.pathName,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 16.dp
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    // 公开/私有标签（对齐 Web 白底 10% 胶囊）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = if (detail.isPublic) Icons.Filled.Public else Icons.Filled.Lock,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = if (detail.isPublic) "公开" else "私有",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = detail.pathName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!detail.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = detail.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "创建者 ${detail.creatorName.ifBlank { "未知用户" }} · ${detail.items.size} 剧集",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // 给上叠的操作栏（-12dp 负偏移）留出空隙
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * 悬浮操作栏（对齐 Web -mt-6 上叠卡片）：
 * 播放全部（主按钮）+ 随机播放；拥有者 = 添加剧集 + 更多菜单（编辑/删除/分享）；
 * 非拥有者仅保留分享（对齐 Web：非拥有者不渲染管理入口）。
 */
@Composable
private fun PathActionBar(
    detail: LearningPathDetail,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onAddEpisode: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .offset(y = (-12).dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = onPlayAll,
                enabled = detail.items.isNotEmpty(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "播放全部",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onShuffle, enabled = detail.items.isNotEmpty()) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "随机播放",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---- 权限条件渲染：拥有者才有 添加剧集 + 更多菜单 ----
            if (detail.isOwner) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .clickable { onAddEpisode() }
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlaylistAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "添加剧集",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Filled.MoreHoriz,
                            contentDescription = "更多操作",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("编辑路径", style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "删除路径",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 2.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        DropdownMenuItem(
                            text = { Text("分享路径", style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            }
                        )
                    }
                }
            } else {
                // 非拥有者：仅保留分享
                IconButton(onClick = onShare) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = "分享路径",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 剧集列表项：序号 / 16:9 封面（PRO 角标 + 进度条）/ 标题 / 播客名·时长 / 移除按钮（仅拥有者） */
@Composable
private fun PathEpisodeRow(
    index: Int,
    item: LearningPathEpisode,
    isOwner: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val episode = item.episode
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(20.dp)
        )
        Box(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
        ) {
            CoverImage(
                url = episode.coverUrl,
                contentDescription = episode.title,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 8.dp
            )
            if (episode.isExclusive) {
                Text(
                    text = "PRO",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(Color(0x99000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
            // 收听进度条（对齐 Web 移动端封面底部细条）
            val progressPercent = episodeProgressPercent(episode)
            if (progressPercent > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressPercent / 100f)
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = listOfNotNull(
                        episode.podcastTitle?.takeIf { it.isNotBlank() },
                        formatMillis(episode.duration * 1000L).takeIf { episode.duration > 0 }
                    ).joinToString(" · ").ifBlank { "未知播客" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (episode.isFinished) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "已听完",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        // ---- 权限条件渲染：仅拥有者可移除剧集 ----
        if (isOwner) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "移除剧集",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** 路径空态：拥有者给「添加剧集」引导 */
@Composable
private fun PathEmptyEpisodes(isOwner: Boolean, onAddEpisode: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(vertical = 8.dp)
            .border(
                2.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(16.dp)
            )
            .padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.PlaylistAdd,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "这条路径还是空的",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "添加剧集，开始你的学习之旅。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (isOwner) {
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onAddEpisode,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "添加剧集")
            }
        }
    }
}

/** 添加剧集弹窗：防抖搜索 + 结果列表（已在路径中的置灰） */
@Composable
private fun AddEpisodeDialog(
    state: LearningPathDetailUiState,
    addedEpisodeIds: Set<String>,
    onQueryChange: (String) -> Unit,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // 头部
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "添加剧集",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }
            // 搜索框
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = state.searchQuery,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (state.searchQuery.isEmpty()) {
                            Text(
                                text = "搜索...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        inner()
                    }
                )
            }
            // 结果列表
            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.isSearching -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                    state.searchResults.isNotEmpty() -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        items(state.searchResults, key = { it.episodeid }) { result ->
                            val isAdded = result.episodeid in addedEpisodeIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .let { if (isAdded) it else it.clickable { onAdd(result.episodeid) } }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CoverImage(
                                    url = result.thumbnailUrl,
                                    contentDescription = result.title,
                                    modifier = Modifier.size(48.dp),
                                    cornerRadius = 8.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = result.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = result.author.ifBlank { "未知播客" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = if (isAdded) Icons.Filled.CheckCircle else Icons.Filled.Add,
                                    contentDescription = if (isAdded) "已添加" else "添加",
                                    tint = if (isAdded) {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    else -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (state.searchQuery.isBlank()) "输入剧集标题进行搜索..." else "没有剧集被找到",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** 已听完或按进度换算的百分比（对齐 Web 移动端封面进度条口径） */
private fun episodeProgressPercent(episode: com.wxkzd.yuanlu.domain.model.Episode): Int {
    if (episode.isFinished) return 100
    if (episode.duration <= 0) return 0
    return ((episode.progressSeconds.toFloat() / episode.duration) * 100).toInt().coerceIn(0, 100)
}

/** 分享路径链接（Web 分享路径 = 复制链接；Android 走系统分享面板） */
private fun sharePathLink(context: android.content.Context, pathid: Int) {
    val text = "https://www.wxkzd.com/library/learning-paths/$pathid"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "分享学习路径"))
}
