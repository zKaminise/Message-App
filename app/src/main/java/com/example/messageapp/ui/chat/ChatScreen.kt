package com.example.messageapp.ui.chat

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.messageapp.data.StorageRepository
import com.example.messageapp.model.Message
import com.example.messageapp.storage.StorageAcl
import com.example.messageapp.utils.Crypto
import com.example.messageapp.utils.Time
import com.example.messageapp.viewmodel.ChatViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    // --------- launchers (OK chamar .launch() no onClick) ----------
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
    // ---------------------------------------------------------------

    LaunchedEffect(chatId) {
        vm.start(chatId)
        if (myUid.isNotBlank()) {
            vm.markRead(chatId, myUid)
            StorageAcl.ensureMemberMarker(chatId, myUid)
        }
    }
    DisposableEffect(Unit) { onDispose { vm.stop() } }

    val q = query.text.trim()
    val filtered = remember(msgs, q) {
        if (q.isBlank()) msgs else msgs.filter {
            val plain = if (it.type == "text") Crypto.decrypt(it.textEnc) else ""
            plain.contains(q, ignoreCase = true)
        }
    }

    // Agrupa por dia mantendo a ordem
    val grouped: List<Pair<String, List<Message>>> = remember(filtered) {
        val map = linkedMapOf<String, MutableList<Message>>()
        filtered.forEach { m ->
            val header = Time.headerFor(m.createdAt).ifBlank { " " }
            map.getOrPut(header) { mutableListOf() }.add(m)
        }
        map.entries.map { it.key to it.value.toList() }
    }

    // Controle do scroll-to-bottom
    val listState: LazyListState = rememberLazyListState()
    val showScrollToBottom by remember {
        derivedStateOf { listState.canScrollForward }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                title = {
                    Column {
                        Text(chat?.name ?: "Conversa", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (chat?.pinnedSnippet != null) {
                            Text(
                                "Mensagem fixada",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    if (chat?.pinnedSnippet != null) {
                        IconButton(onClick = { vm.unpin(chatId) }) {
                            Icon(Icons.Filled.PushPin, contentDescription = "Desafixar")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            AnimatedVisibility(visible = showScrollToBottom, enter = fadeIn(), exit = fadeOut()) {
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            val last = listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1
                            if (last >= 0) listState.animateScrollToItem(last)
                        }
                    }
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Ir para o fim")
                }
            }
        }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {

            // Banner da mensagem fixada
            AnimatedVisibility(
                visible = chat?.pinnedSnippet != null,
                enter = fadeIn(), exit = fadeOut()
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0x11000000))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.PushPin, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Fixada: ${chat?.pinnedSnippet}")
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.unpin(chatId) }) { Text("Remover") }
                }
            }

            // Busca
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar na conversa…") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            // Lista com sticky headers corretos
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                grouped.forEach { (header, list) ->
                    stickyHeader {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Divider(Modifier.weight(1f).padding(start = 12.dp))
                            Text(
                                header,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Divider(Modifier.weight(1f).padding(end = 12.dp))
                        }
                    }
                    items(list, key = { it.id }) { m ->
                        MessageBubble(
                            m = m,
                            isMine = m.senderId == myUid,
                            highlight = q.takeIf { it.isNotBlank() },
                            onLongPressPin = { vm.pin(chatId, m) }
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }

            // Barra de anexos com ícones
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Icon(Icons.Outlined.Image, contentDescription = "Imagem") }

                IconButton(onClick = {
                    pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                }) { Icon(Icons.Outlined.VideoFile, contentDescription = "Vídeo") }

                IconButton(onClick = { pickAudio.launch(arrayOf("audio/*")) }) {
                    Icon(Icons.Outlined.Mic, contentDescription = "Áudio")
                }

                IconButton(onClick = { pickFile.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = "Arquivo")
                }
            }

            // Caixa de mensagem + enviar
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
                    singleLine = false,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                ExtendedFloatingActionButton(
                    onClick = {
                        val text = input.text.trim()
                        if (text.isNotBlank() && myUid.isNotBlank()) {
                            vm.sendText(chatId, myUid, text)
                            input = TextFieldValue("")
                        }
                    },
                    icon = { Icon(Icons.Outlined.Send, null) },
                    text = { Text("Enviar") }
                )
            }
        }
    }
}

/* ----- Bolha de mensagem com destaque e ticks ----- */

@Composable
private fun MessageBubble(
    m: Message,
    isMine: Boolean,
    highlight: String?,
    onLongPressPin: () -> Unit
) {
    val bgMine = MaterialTheme.colorScheme.primaryContainer
    val bgOther = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val bubbleColor = if (isMine) bgMine else bgOther

    val plain = when (m.type) {
        "text" -> Crypto.decrypt(m.textEnc)
        "image" -> "[imagem]"
        "video" -> "[vídeo]"
        "audio" -> "[áudio]"
        else -> "[arquivo]"
    }

    val content: AnnotatedString = if (!highlight.isNullOrBlank() && m.type == "text") {
        val idx = plain.indexOf(highlight, ignoreCase = true)
        if (idx >= 0) {
            AnnotatedString.Builder().apply {
                append(plain.substring(0, idx))
                pushStyle(SpanStyle(background = Color.Yellow.copy(alpha = 0.4f)))
                append(plain.substring(idx, idx + highlight.length))
                pop()
                append(plain.substring(idx + highlight.length))
            }.toAnnotatedString()
        } else AnnotatedString(plain)
    } else AnnotatedString(plain)

    val sent = true // chegou ao servidor
    val delivered = m.deliveredTo.isNotEmpty()
    val read = m.readBy.isNotEmpty()

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        if (!isMine) Spacer(Modifier.weight(0.15f)) else Spacer(Modifier.weight(0.35f))
        Surface(
            color = bubbleColor,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 1.dp,
            modifier = Modifier
                .weight(0.5f)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPressPin() }) }
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(content, color = textColor)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Time.timeFor(m.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    if (isMine) DeliveryTicks(sent, delivered, read)
                }
            }
        }
        if (isMine) Spacer(Modifier.weight(0.15f)) else Spacer(Modifier.weight(0.35f))
    }
}
