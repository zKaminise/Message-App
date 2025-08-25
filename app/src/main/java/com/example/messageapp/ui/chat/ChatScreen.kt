package com.example.messageapp.ui.chat

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.messageapp.data.StorageRepository
import com.example.messageapp.model.Message
import com.example.messageapp.utils.Crypto
import com.example.messageapp.viewmodel.ChatViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    vm: ChatViewModel,
    onBack: () -> Unit = {}
) {
    val chat by vm.chat.collectAsState()
    val msgs by vm.messages.collectAsState()
    val myUid = remember { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val storage = remember { StorageRepository() }
    val context = LocalContext.current

    LaunchedEffect(chatId) {
        vm.start(chatId)
        if (myUid.isNotBlank()) vm.markRead(chatId, myUid)
    }
    DisposableEffect(Unit) { onDispose { vm.stop() } }

    val filtered = remember(msgs, query) {
        val q = query.text.trim()
        if (q.isBlank()) msgs else msgs.filter {
            val plain = if (it.type == "text") Crypto.decrypt(it.textEnc) else ""
            plain.contains(q, ignoreCase = true)
        }
    }

    // --------- PICKERS ---------
    // Imagem & Vídeo: Android Photo Picker (não requer permissão)
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && myUid.isNotBlank()) {
            scope.launch { storage.sendMedia(chatId, myUid, uri, "image") }
        }
    }
    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && myUid.isNotBlank()) {
            scope.launch { storage.sendMedia(chatId, myUid, uri, "video") }
        }
    }

    // Áudio & Arquivo: OpenDocument com persistência de permissão
    val pickAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && myUid.isNotBlank()) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            scope.launch { storage.sendMedia(chatId, myUid, uri, "audio") }
        }
    }
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && myUid.isNotBlank()) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            scope.launch { storage.sendMedia(chatId, myUid, uri, "file") }
        }
    }
    // ---------------------------

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                title = { Text(chat?.name ?: "Conversa") },
                actions = {
                    if (chat?.pinnedSnippet != null) {
                        IconButton(onClick = { vm.unpin(chatId) }) {
                            Icon(Icons.Filled.PushPin, contentDescription = "Desafixar")
                        }
                    }
                }
            )
        }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {

            chat?.pinnedSnippet?.let {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0x11000000))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.PushPin, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Fixada: $it")
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.unpin(chatId) }) { Text("Remover") }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar na conversa…") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            LazyColumn(Modifier.weight(1f)) {
                items(filtered) { m ->
                    MessageBubble(
                        m = m,
                        isMine = m.senderId == myUid,
                        onLongPressPin = { vm.pin(chatId, m) }
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    label = { Text("Imagem") }
                )
                Spacer(Modifier.width(6.dp))
                AssistChip(
                    onClick = {
                        pickVideo.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    label = { Text("Vídeo") }
                )
                Spacer(Modifier.width(6.dp))
                AssistChip(
                    onClick = { pickAudio.launch(arrayOf("audio/*")) },
                    label = { Text("Áudio") }
                )
                Spacer(Modifier.width(6.dp))
                AssistChip(
                    onClick = { pickFile.launch(arrayOf("*/*")) },
                    label = { Text("Arquivo") }
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Escreva uma mensagem…") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val text = input.text.trim()
                    if (text.isNotBlank() && myUid.isNotBlank()) {
                        vm.sendText(chatId, myUid, text)
                        input = TextFieldValue("")
                    }
                }) { Text("Enviar") }
            }
        }
    }
}

@Composable
private fun MessageBubble(m: Message, isMine: Boolean, onLongPressPin: () -> Unit) {
    val text = if (m.type == "text") Crypto.decrypt(m.textEnc)
    else when (m.type) {
        "image" -> "[imagem]"
        "video" -> "[vídeo]"
        "audio" -> "[áudio]"
        else -> "[arquivo]"
    }
    val color = if (isMine) Color(0xFFDCF8C6) else Color(0xFFFFFFFF)

    Row(Modifier.fillMaxWidth().padding(4.dp)) {
        if (!isMine) Spacer(Modifier.weight(0.1f)) else Spacer(Modifier.weight(0.4f))
        Text(
            text = text,
            modifier = Modifier
                .weight(0.5f)
                .background(color)
                .padding(8.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onLongPressPin() })
                }
        )
        if (isMine) Spacer(Modifier.weight(0.1f)) else Spacer(Modifier.weight(0.4f))
    }
}
