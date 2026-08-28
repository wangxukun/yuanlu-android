package com.wxkzd.yuanlu.feature.auth

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 全局登录弹层（对齐 Web EmailCheckDialog 的结构与文案）：
 * 「欢迎来到远路播客 / 请选择登录方式」+ 双 Tab（手机号/邮箱）+ 协议勾选 + 胶囊主按钮。
 * 登录成功由 AppViewModel.tokenFlow 驱动自动收起。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LoginSheet(
    onDismiss: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isPhoneMode by rememberSaveable { mutableStateOf(true) }
    var account by rememberSaveable { mutableStateOf("") }
    var credential by rememberSaveable { mutableStateOf("") }
    var agreed by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var openAgreement by remember { mutableStateOf<AgreementDoc?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.reset()
            onDismiss()
        },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "欢迎来到远路播客",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "请选择登录方式",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            LoginModeTabs(
                isPhoneMode = isPhoneMode,
                onSwitch = { mode ->
                    if (isPhoneMode != mode) {
                        isPhoneMode = mode
                        account = ""
                        credential = ""
                        viewModel.clearError()
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (isPhoneMode) {
                OutlinedTextField(
                    value = account,
                    onValueChange = { account = it.filter(Char::isDigit).take(11) },
                    leadingIcon = { Icon(Icons.Filled.PhoneIphone, contentDescription = null) },
                    placeholder = { Text("请输入11位手机号码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = credential,
                    onValueChange = { credential = it.filter(Char::isDigit).take(6) },
                    leadingIcon = { Icon(Icons.Filled.Password, contentDescription = null) },
                    placeholder = { Text("输入6位验证码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    trailingIcon = {
                        if (uiState.countdownSeconds > 0) {
                            Text(
                                text = "${uiState.countdownSeconds}s",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        } else {
                            TextButton(
                                onClick = { viewModel.sendSmsCode(account) },
                                enabled = !uiState.isSending
                            ) {
                                if (uiState.isSending) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("获取验证码")
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    value = account,
                    onValueChange = { account = it },
                    leadingIcon = { Icon(Icons.Filled.Mail, contentDescription = null) },
                    placeholder = { Text("请输入邮箱地址") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = credential,
                    onValueChange = { credential = it },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    placeholder = { Text("请输入密码") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "隐藏" else "显示")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            AgreementRow(
                agreed = agreed,
                onToggle = { agreed = it },
                onOpenDoc = { openAgreement = it }
            )

            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Box(contentAlignment = Alignment.Center) {
                PressablePillButton(
                    text = if (isPhoneMode) "登录 / 注册" else "登录",
                    onClick = {
                        if (isPhoneMode) {
                            viewModel.loginWithSms(account, credential)
                        } else {
                            viewModel.loginWithPassword(account, credential)
                        }
                    },
                    enabled = agreed && !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }

    openAgreement?.let { doc ->
        AgreementDialog(doc = doc, onDismiss = { openAgreement = null })
    }
}

/** 双 Tab 分段容器（对齐 Web 的 tabs-boxed：激活白底 + primary 字） */
@Composable
private fun LoginModeTabs(
    isPhoneMode: Boolean,
    onSwitch: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LoginModeTab("手机号注册 / 登录", isPhoneMode, Modifier.weight(1f)) { onSwitch(true) }
        LoginModeTab("邮箱注册 / 登录", !isPhoneMode, Modifier.weight(1f)) { onSwitch(false) }
    }
}

@Composable
private fun LoginModeTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                color = if (selected) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** 协议勾选行：链接点击打开 Web 同款全文（AgreementDialog） */
@Composable
private fun AgreementRow(
    agreed: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpenDoc: (AgreementDoc) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = agreed, onCheckedChange = onToggle)
        Text(
            text = "我已阅读并同意 ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "用户协议",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onOpenDoc(UserAgreement) }
        )
        Text(
            text = " 和 ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "隐私政策",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onOpenDoc(PrivacyPolicy) }
        )
    }
}
