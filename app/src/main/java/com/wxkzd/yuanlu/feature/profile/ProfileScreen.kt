package com.wxkzd.yuanlu.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wxkzd.yuanlu.domain.model.UserProfile
import com.wxkzd.yuanlu.theme.ThemeMode
import com.wxkzd.yuanlu.ui.components.ShimmerBox
import kotlinx.coroutines.launch

/**
 * 「我的」页，对齐 Web /auth/mine：用户卡（头像/昵称/角色badge/email）+
 * 「学习与记录」「账户与系统设置」两组菜单 + 退出登录；未登录显示引导卡。
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    isLoggedIn: Boolean,
    onLogin: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    // 个人中心 VM（Activity 作用域）：编辑资料保存后借 profileRevision 刷新本页用户卡
    userProfileViewModel: UserProfileViewModel,
    onOpenPersonalCenter: () -> Unit,
    onOpenFavorites: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userCenterState by userProfileViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showThemeDialog by remember { mutableStateOf(false) }

    fun comingSoon(label: String) {
        scope.launch { snackbarHostState.showSnackbar("$label 即将上线") }
    }

    LaunchedEffect(isLoggedIn) {
        viewModel.onAuthStateChanged(isLoggedIn)
    }

    // 个人中心保存资料后（revision 自增）刷新本页用户卡（对齐 Web updateSession 后 fetchProfile）
    LaunchedEffect(userCenterState.profileRevision) {
        if (userCenterState.profileRevision > 0 && isLoggedIn) viewModel.load()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ---- 头部用户卡 ----
            if (!isLoggedIn) {
                GuestCard(onClick = onLogin)
            } else {
                UserCard(
                    uiState = uiState,
                    onRetry = viewModel::load
                )
            }

            // ---- 学习与记录（仅登录） ----
            if (isLoggedIn) {
                MenuCard(title = "学习与记录") {
                    MenuRow("发音弱项本", Icons.Filled.Mic, MaterialTheme.colorScheme.tertiary) { comingSoon("发音弱项本") }
                    MenuRow("学习路径", Icons.Filled.School, MaterialTheme.colorScheme.primary) { comingSoon("学习路径") }
                    MenuRow("收听历史", Icons.Filled.History, MaterialTheme.colorScheme.secondary) { comingSoon("收听历史") }
                    MenuRow("我的收藏", Icons.Filled.Bookmark, Color(0xFFB96F0F)) { onOpenFavorites() }
                }
            }

            // ---- 账户与系统设置 ----
            MenuCard(title = "账户与系统设置") {
                if (isLoggedIn) {
                    MenuRow("个人中心", Icons.Filled.Person, MaterialTheme.colorScheme.primary) { onOpenPersonalCenter() }
                    MenuRow("我的订阅", Icons.Filled.CreditCard, MaterialTheme.colorScheme.secondary) { comingSoon("我的订阅") }
                    if (uiState.profile?.role == "ADMIN") {
                        MenuRow("控制台", Icons.Filled.Computer, MaterialTheme.colorScheme.error) { comingSoon("控制台") }
                    }
                }
                MenuRow(
                    "外观设置",
                    Icons.Filled.Contrast,
                    MaterialTheme.colorScheme.tertiary,
                    trailing = {
                        Text(
                            text = when (themeMode) {
                                ThemeMode.SYSTEM -> "跟随系统"
                                ThemeMode.LIGHT -> "浅色"
                                ThemeMode.DARK -> "深色"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                ) { showThemeDialog = true }
                if (isLoggedIn) {
                    MenuRow("消息通知", Icons.Filled.Notifications, MaterialTheme.colorScheme.primary) { comingSoon("消息通知") }
                }
                MenuRow("帮助与支持", Icons.AutoMirrored.Filled.HelpOutline, MaterialTheme.colorScheme.tertiary) { comingSoon("帮助与支持") }
            }

            // ---- 退出登录（仅登录） ----
            if (isLoggedIn) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "退出登录",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .clickable { viewModel.logout() }
                            .padding(vertical = 16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showThemeDialog) {
        ThemeModeDialog(
            current = themeMode,
            onSelect = {
                onThemeModeChange(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}

// ---------- 私有组件 ----------

@Composable
private fun GuestCard(onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "未登录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "点击登录或注册，开启学习之旅",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun UserCard(uiState: ProfileUiState, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                uiState.isLoading -> ShimmerBox(
                    modifier = Modifier.size(64.dp),
                    cornerRadius = 32.dp
                )
                else -> {
                    val avatarUrl = uiState.profile?.avatarUrl
                    if (!avatarUrl.isNullOrBlank() && avatarUrl.startsWith("http")) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "avatar",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = uiState.profile?.displayName()?.take(1)?.uppercase() ?: "Y",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (uiState.isLoading) {
                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.5f).height(18.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.35f).height(13.dp))
                } else if (uiState.profile != null) {
                    val profile = uiState.profile
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.displayName(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        RoleBadge(profile.role)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    profile.email?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = uiState.error ?: "资料加载失败",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "点击重试",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { onRetry() }
                    )
                }
            }
        }
    }
}

private fun UserProfile.displayName(): String {
    return nickname?.takeIf { it.isNotBlank() }
        ?: email?.substringBefore("@")
        ?: "User"
}

/** 角色 badge：管理员=远青 / 高级会员=曙光橙 / 普通用户=灰（对齐 Web） */
@Composable
private fun RoleBadge(role: String?) {
    val (label, color) = when (role) {
        "ADMIN" -> "管理员" to MaterialTheme.colorScheme.primary
        "PREMIUM" -> "高级会员" to Color(0xFFB96F0F)
        else -> "普通用户" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun MenuCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
            content()
        }
    }
}

@Composable
private fun MenuRow(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(iconTint.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun ThemeModeDialog(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("外观设置") },
        text = {
            Column {
                listOf(
                    ThemeMode.SYSTEM to "跟随系统",
                    ThemeMode.LIGHT to "浅色",
                    ThemeMode.DARK to "深色"
                ).forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        if (mode == current) {
                            Text(
                                text = "✓",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
