package com.wxkzd.yuanlu.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isPasswordMode by remember { mutableStateOf(true) }
    var account by remember { mutableStateOf("") }
    var credential by remember { mutableStateOf("") }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onLoginSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to Yuanlu",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Row(modifier = Modifier.padding(bottom = 16.dp)) {
            FilterChip(
                selected = isPasswordMode,
                onClick = { isPasswordMode = true; account = ""; credential = "" },
                label = { Text("Password") },
                modifier = Modifier.padding(end = 8.dp)
            )
            FilterChip(
                selected = !isPasswordMode,
                onClick = { isPasswordMode = false; account = ""; credential = "" },
                label = { Text("SMS Code") }
            )
        }

        OutlinedTextField(
            value = account,
            onValueChange = { account = it },
            label = { Text(if (isPasswordMode) "Email" else "Phone Number") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = credential,
            onValueChange = { credential = it },
            label = { Text(if (isPasswordMode) "Password" else "Verification Code") },
            visualTransformation = if (isPasswordMode) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            singleLine = true
        )

        if (uiState.error != null) {
            Text(
                text = uiState.error!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        Button(
            onClick = {
                if (isPasswordMode) {
                    viewModel.loginWithPassword(account, credential)
                } else {
                    viewModel.loginWithSms(account, credential)
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !uiState.isLoading
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Login")
            }
        }
    }
}
