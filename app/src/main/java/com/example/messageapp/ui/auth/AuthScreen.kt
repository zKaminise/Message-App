package com.example.messageapp.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.messageapp.data.AuthRepository
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    repo: AuthRepository,
    onLogged: () -> Unit
) {
    val ctx = LocalContext.current
    val act = ctx as Activity
    var tab by remember { mutableStateOf(0) } // 0=Email, 1=Telefone
    Column(Modifier.padding(16.dp)) {
        Text("Entrar no Chat", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Email") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Telefone") })
        }
        Spacer(Modifier.height(12.dp))

        if (tab == 0) EmailAuthSection(repo, onLogged) else PhoneAuthSection(repo, act, onLogged)
    }
}


@Composable
private fun EmailAuthSection(
    repo: AuthRepository,
    onLogged: () -> Unit
) {
    var mode by remember { mutableStateOf(0) } // 0=entrar, 1=cadastrar
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var msg  by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Entrar") })
        FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Criar conta") })
    }
    Spacer(Modifier.height(8.dp))

    OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        pass, { pass = it }, label = { Text("Senha") },
        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))

    val isValid = email.isNotBlank() && pass.length >= 6
    Button(enabled = isValid, onClick = {
        scope.launch {
            runCatching {
                if (mode == 0) repo.signInEmail(email, pass) else repo.signUpEmail(email, pass)
            }.onSuccess { onLogged() }.onFailure { msg = it.message }
        }
    }) { Text(if (mode == 0) "Entrar" else "Criar conta") }

    Spacer(Modifier.height(8.dp))
    TextButton(onClick = {
        if (email.isNotBlank()) {
            scope.launch {
                runCatching { repo.sendPasswordReset(email) }
                    .onSuccess { msg = "Enviamos um email de recuperação." }
                    .onFailure { msg = it.message }
            }
        } else msg = "Preencha o email."
    }) { Text("Esqueci minha senha") }

    msg?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
}


@Composable
private fun PhoneAuthSection(
    repo: AuthRepository,
    activity: Activity,
    onLogged: () -> Unit
) {
    var phone by remember { mutableStateOf("+55") }
    var code  by remember { mutableStateOf("") }
    var vid   by remember { mutableStateOf<String?>(null) }
    var msg   by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    if (vid == null) {
        OutlinedTextField(phone, { phone = it }, label = { Text("Telefone (+55...)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            val cb = repo.phoneVerifyCallbacks(
                onCodeSent = { v -> vid = v },
                onInstantSuccess = {
                    scope.launch { repo.upsertUserProfile(); repo.saveFcmToken(); onLogged() }
                },
                onError = { e -> msg = e.message }
            )
            repo.startPhoneVerification(activity, phone, cb)
        }) { Text("Enviar código") }
    } else {
        OutlinedTextField(code, { code = it }, label = { Text("Código") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            scope.launch {
                runCatching { repo.signInWithPhoneCredential(vid!!, code) }
                    .onSuccess { onLogged() }
                    .onFailure { msg = it.message }
            }
        }) { Text("Confirmar") }
    }

    msg?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
}
