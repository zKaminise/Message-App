package com.example.messageapp.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.messageapp.data.StorageRepository
import com.example.messageapp.model.Message
import com.example.messageapp.utils.Crypto
import com.example.messageapp.viewmodel.ChatViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    chatId: String,
    vm: ChatViewModel
) {
    val chat by vm.chat.collectAsState()
    val msgs by vm.messages.collectAsState()
    val myUid = remember { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val storage = remember { StorageRepository() }

    LaunchedEffect(chatId) {
        vm.start(chatId)
        if (myUid.isNotBlank()) vm.markRead(chatId, myUid)
    }
    DisposableEffect(Unit) { onDispose { vm.stop() } }

    val filtered = remember(msgs, query) {
        if (query.isBlank()) msgs else msgs.filter {
            Crypto.decrypt(it.textEnc).contains(query, ignoreCase = true)
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && myUid.isNotBlank()) {
            scope.launch { storage.sendMedia(chatId, myUid, uri, "image") }
        }
    }
    val pickVideo = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && myUid.isNotBlank()) {
            scope.launch { storage.sendMedia(chatId, myUid, uri, "video") }
        }
    }
    val pickAudio = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && myUid.isNotBlank()) {
            scope.launch { storage.sendMedia(chatId, myUid, uri, "audio") }
        }
    }
    val pickFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && myUid.isNotBlank()) {
            scope.launch { storage.sendMedia(chatId, myUid, uri, "file") }
        }
    }

    Column(Modifier.fillMaxSize()) {

        chat?.pinnedSnippet?.let {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0x11000000))
                    .padding(8.dp)
            ) { Text("Fixada: $it") }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Buscar na conversa…") },
            modifier = Modifier.fillMaxWidth()
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

        Row(Modifier.padding(8.dp)) {
            Button(onClick = { pickImage.launch("image/*") }) { Text("Imagem") }
            Spacer(Modifier.width(6.dp))
            Button(onClick = { pickVideo.launch("video/*") }) { Text("Vídeo") }
            Spacer(Modifier.width(6.dp))
            Button(onClick = { pickAudio.launch("audio/*") }) { Text("Áudio") }
            Spacer(Modifier.width(6.dp))
            Button(onClick = { pickFile.launch("*/*") }) { Text("Arquivo") }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (input.isNotBlank() && myUid.isNotBlank()) {
                    vm.sendText(chatId, myUid, input)
                    input = ""
                }
            }) { Text("Enviar") }
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
