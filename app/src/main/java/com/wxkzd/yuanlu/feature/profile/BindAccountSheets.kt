package com.wxkzd.yuanlu.feature.profile

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.wxkzd.yuanlu.feature.auth.PressablePillButton

/**
 * 账号与安全弹层（复刻 Web AccountSecurityTab 的 BindPhoneForm/BindEmailForm/DeleteAccountCard）：
 * - BindPhoneSheet：手机号 + 短信验证码（scene=BIND）
 * - BindEmailSheet：邮箱 + 邮箱验证码 + 同时设置登录密码
 * - DeleteAccountConfirmDialog：二次确认弹窗（红色危险样式）
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BindPhoneSheet(
    form: SecurityFormState,
    onClose: () -> Unit,
    onPhoneChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onSubmit: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SheetHeader(title = "绑定手机号", subtitle = "绑定后可用手机号验证码登录")
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = form.phone,
                onValueChange = onPhoneChange,
                leadingIcon = { Icon(Icons.Filled.PhoneIphone, contentDescription = null) },
                placeholder = { Text("请输入11位手机号码") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                enabled = !form.isSubmitting,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = form.code,
                onValueChange = onCodeChange,
                leadingIcon = { Icon(Icons.Filled.Password, contentDescription = null) },
                placeholder = { Text("输入6位验证码") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                enabled = !form.isSubmitting,
                trailingIcon = {
                    CodeSendTrailing(
                        countdownSeconds = form.countdownSeconds,
                        isSending = form.isSendingCode,
                        // 手机号未满 11 位前不允许发送（Web BindPhoneForm 同口径）
                        enabled = ProfileUtils.validateBindPhone(form.phone) == null,
                        onClick = onSendCode
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            NoticeAndErrorRows(form)

            Spacer(modifier = Modifier.height(12.dp))
            SubmitButton(
                text = "确认绑定",
                enabled = !form.isSubmitting && form.phone.length == 11 && form.code.length == 6,
                loading = form.isSubmitting,
                onClick = onSubmit
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BindEmailSheet(
    form: SecurityFormState,
    onClose: () -> Unit,
    onEmailChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onSubmit: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val criteria = ProfileUtils.passwordCriteria(form.password)
    val confirmMatch = form.confirmPassword.isNotEmpty() && form.password == form.confirmPassword

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SheetHeader(
                title = "绑定邮箱",
                subtitle = "绑定后将同时设置登录密码，可用邮箱密码登录"
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = form.email,
                onValueChange = onEmailChange,
                leadingIcon = { Icon(Icons.Filled.Mail, contentDescription = null) },
                placeholder = { Text("请输入邮箱地址") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !form.isSubmitting,
                trailingIcon = {
                    CodeSendTrailing(
                        countdownSeconds = form.countdownSeconds,
                        isSending = form.isSendingCode,
                        // 邮箱格式不合法前不允许发送（Web BindEmailForm 同口径）
                        enabled = ProfileUtils.validateBindEmail(form.email) == null,
                        onClick = onSendCode
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = form.code,
                onValueChange = onCodeChange,
                leadingIcon = { Icon(Icons.Filled.Password, contentDescription = null) },
                placeholder = { Text("6位邮箱验证码") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                enabled = !form.isSubmitting,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = form.password,
                onValueChange = onPasswordChange,
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                placeholder = { Text("设置登录密码") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !form.isSubmitting,
                trailingIcon = {
                    TextButton(onClick = { passwordVisible = !passwordVisible }) {
                        Text(if (passwordVisible) "隐藏" else "显示")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            // 密码强度实时指示（Web BindEmailForm 的三格判定：8位以上/包含字母/包含数字）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CriteriaItem(met = criteria.length, label = "8位以上", modifier = Modifier.weight(1f))
                CriteriaItem(met = criteria.hasLetter, label = "包含字母", modifier = Modifier.weight(1f))
                CriteriaItem(met = criteria.hasNumber, label = "包含数字", modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = form.confirmPassword,
                onValueChange = onConfirmPasswordChange,
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                placeholder = { Text("确认登录密码") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !form.isSubmitting,
                modifier = Modifier.fillMaxWidth()
            )
            if (form.confirmPassword.isNotEmpty() && !confirmMatch) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "两次输入的密码不一致",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                )
            }

            NoticeAndErrorRows(form)

            Spacer(modifier = Modifier.height(12.dp))
            SubmitButton(
                text = "确认绑定",
                enabled = !form.isSubmitting && form.code.length == 6 && criteria.allMet && confirmMatch,
                loading = form.isSubmitting,
                onClick = onSubmit
            )
        }
    }
}

/** 注销账号二次确认（防误触）：红色危险样式，对齐 Web DeleteAccountCard 的确认弹窗 */
@Composable
fun DeleteAccountConfirmDialog(
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "确认注销账号？",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = "您的个人资料、学习记录、收藏等所有数据将被永久删除，且无法恢复。请确认您要继续此操作。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("确认注销", fontWeight = FontWeight.Medium)
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !isDeleting) {
                Text("取消")
            }
        }
    )
}

// ---------- 弹层内通用小组件 ----------

@Composable
private fun SheetHeader(title: String, subtitle: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 验证码输入框尾随：倒计时文本或「获取验证码」按钮（发送中转圈，LoginSheet 同款） */
@Composable
private fun CodeSendTrailing(
    countdownSeconds: Int,
    isSending: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    if (countdownSeconds > 0) {
        Text(
            text = "${countdownSeconds}s",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp)
        )
    } else {
        TextButton(onClick = onClick, enabled = enabled && !isSending) {
            if (isSending) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text("获取验证码")
            }
        }
    }
}

/** 非侵扰提示（primary）与错误提示（error）：上 notice 下 error，同一时间只展示一条 */
@Composable
private fun NoticeAndErrorRows(form: SecurityFormState) {
    if (form.notice != null && form.error == null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = form.notice,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
    if (form.error != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = form.error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 提交胶囊按钮 + 居中加载圈（LoginSheet 提交区同款） */
@Composable
private fun SubmitButton(
    text: String,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit
) {
    Box(contentAlignment = Alignment.Center) {
        PressablePillButton(
            text = text,
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

/** 密码强度单项：达标远青对勾，未达标空心圆（Web RequirementItem 同款） */
@Composable
private fun CriteriaItem(met: Boolean, label: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (met) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (met) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}
