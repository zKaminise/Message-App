package com.example.messageapp.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.messageapp.data.AuthRepository
import kotlinx.coroutines.launch

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun AuthScreen(
    onLoggedIn: () -> Unit
) {
    val repo = remember { AuthRepository() }
    val activity = LocalContext.current.findActivity()
    var tab by remember { mutableStateOf(0) }

    Column(Modifier.padding(16.dp)) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Email") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Telefone") })
        }
        Spacer(Modifier.height(12.dp))

        when (tab) {
            0 -> EmailAuthPane(repo, onLoggedIn)
            1 -> PhoneAuthPane(repo, activity, onLoggedIn)
        }
    }
}

@Composable
private fun EmailAuthPane(
    repo: AuthRepository,
    onLoggedIn: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var pass  by remember { mutableStateOf("") }
    var msg   by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column {
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = pass, onValueChange = { pass = it },
            label = { Text("Senha") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        runCatching { repo.signInEmail(email, pass) }
                            .onSuccess { onLoggedIn() }
                            .onFailure { msg = it.message }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Entrar") }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        runCatching { repo.signUpEmail(email, pass) }
                            .onSuccess { onLoggedIn() }
                            .onFailure { msg = it.message }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Criar conta") }
        }

        TextButton(onClick = {
            if (email.isNotBlank()) {
                scope.launch {
                    runCatching { repo.sendPasswordReset(email) }
                        .onFailure { msg = it.message }
                }
            } else msg = "Preencha o e-mail para recuperar a senha."
        }) { Text("Esqueci a senha") }

        Divider(Modifier.padding(vertical = 8.dp))
        OutlinedButton(onClick = {
            scope.launch {
                runCatching { repo.signInAnonymouslyAndUpsert() }
                    .onSuccess { onLoggedIn() }
                    .onFailure { msg = it.message }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Entrar como convidado") }

        msg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PhoneAuthPane(
    repo: AuthRepository,
    activity: Activity?,
    onLoggedIn: () -> Unit
) {
    var phone by remember { mutableStateOf("+55") }
    var code  by remember { mutableStateOf("") }
    var vid   by remember { mutableStateOf<String?>(null) }
    var msg   by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column {
        if (vid == null) {
            OutlinedTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text("Telefone (+55...)") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                if (activity == null) {
                    msg = "Não foi possível obter a Activity. Tente a opção Email."
                    return@Button
                }
                val callbacks = repo.phoneVerifyCallbacks(
                    onCodeSent = { v -> vid = v },
                    onInstantSuccess = {
                        scope.launch {
                            repo.upsertUserProfile(); repo.saveFcmToken(); onLoggedIn()
                        }
                    },
                    onError = { e -> msg = e.message }
                )
                repo.startPhoneVerification(activity, phone, callbacks)
            }) { Text("Enviar código") }
        } else {
            OutlinedTextField(
                value = code, onValueChange = { code = it },
                label = { Text("Código") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                scope.launch {
                    runCatching { repo.signInWithPhoneCredential(vid!!, code) }
                        .onSuccess { onLoggedIn() }
                        .onFailure { msg = it.message }
                }
            }) { Text("Confirmar") }
        }
        msg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
