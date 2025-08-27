package com.example.messageapp.ui.chat

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.messageapp.data.ChatRepository
import com.example.messageapp.data.StorageRepository
import com.example.messageapp.model.Message
import com.example.messageapp.storage.StorageAcl
import com.example.messageapp.utils.Crypto
import com.example.messageapp.utils.Time
import com.example.messageapp.viewmodel.ChatViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private data class SenderUi(val name: String, val photo: String?)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    chatId: String,
    vm: ChatViewModel,
    onBack: () -> Unit = {},
    onOpenInfo: (String) -> Unit = {}
) {
    val chat by vm.chat.collectAsState()
    val msgs by vm.messages.collectAsState()
    val myUid = remember { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val storage = remember { StorageRepository() }
    val repo = remember { ChatRepository() }
    val context = LocalContext.current

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            if (myUid.isNotBlank()) scope.launch { storage.sendMedia(chatId, myUid, it, "image") }
        }
    }
    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            if (myUid.isNotBlank()) scope.launch { storage.sendMedia(chatId, myUid, it, "video") }
        }
    }
    val pickAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            if (myUid.isNotBlank()) scope.launch { storage.sendMedia(chatId, myUid, it, "audio") }
        }
    }
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            if (myUid.isNotBlank()) scope.launch { storage.sendMedia(chatId, myUid, it, "file") }
        }
    }

    LaunchedEffect(chatId) {
        vm.start(chatId)
        if (myUid.isNotBlank()) {
            vm.markRead(chatId, myUid)
            StorageAcl.ensureMemberMarker(chatId, myUid)
        }
    }
    DisposableEffect(Unit) { onDispose { vm.stop() } }

    val users = remember { mutableStateMapOf<String, SenderUi>() }
    val senderIds = remember(msgs) { msgs.map { it.senderId }.toSet() }
    val db = remember { FirebaseFirestore.getInstance() }

    LaunchedEffect(senderIds) {
        val missing = senderIds.filter { it.isNotBlank() && !users.containsKey(it) }
        if (missing.isEmpty()) return@LaunchedEffect
        missing.chunked(10).forEach { chunk ->
            val qs = db.collection("users")
                .whereIn(FieldPath.documentId(), chunk)
                .get().await()
            qs.documents.forEach { d ->
                users[d.id] = SenderUi(
                    name = d.getString("displayName") ?: "@${d.id.take(6)}",
                    photo = d.getString("photoUrl")
                )
            }
        }
    }

    val q = query.text.trim()
    val filtered: List<Message> = remember(msgs, q, myUid) {
        val base = msgs
            .filter { !it.deletedFor.getOrDefault(myUid, false) } // esconde minhas excluídas
        if (q.isBlank()) base else base.filter {
            if (it.type != "text") return@filter false
            val plain = Crypto.decrypt(it.textEnc)
            plain.contains(q, ignoreCase = true)
        }
    }

    val grouped: List<Pair<String, List<Message>>> = remember(filtered) {
        val map = linkedMapOf<String, MutableList<Message>>()
        filtered.forEach { m ->
            val header = Time.headerFor(m.createdAt).ifBlank { " " }
            map.getOrPut(header) { mutableListOf() }.add(m)
        }
        map.entries.map { it.key to it.value.toList() }
    }

    var selected by remember { mutableStateOf<Message?>(null) }

    val listState: LazyListState = rememberLazyListState()
    val showScrollToBottom by remember { derivedStateOf { listState.canScrollForward } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Voltar") }
                },
                title = {
                    Column {
                        Text(
                            chat?.name ?: "Conversa",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                    IconButton(onClick = { onOpenInfo(chatId) }) {
                        Icon(Icons.Filled.MoreVert, "Informações")
                    }
                    if (chat?.pinnedSnippet != null) {
                        IconButton(onClick = { vm.unpin(chatId) }) {
                            Icon(Icons.Filled.PushPin, "Desafixar")
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
                ) { Icon(Icons.Filled.KeyboardArrowDown, "Ir para o fim") }
            }
        }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {

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

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar na conversa…") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                for ((header, list) in grouped) {
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
                            author = users[m.senderId],
                            onLongPress = { selected = m }
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { pickImage.launch(arrayOf("image/*")) }) {
                    Icon(Icons.Outlined.Image, "Imagem")
                }
                IconButton(onClick = { pickVideo.launch(arrayOf("video/*")) }) {
                    Icon(Icons.Outlined.VideoFile, "Vídeo")
                }
                IconButton(onClick = { pickAudio.launch(arrayOf("audio/*")) }) {
                    Icon(Icons.Outlined.Mic, "Áudio")
                }
                IconButton(onClick = { pickFile.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Outlined.AttachFile, "Arquivo")
                }
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

    val selectedMsg = selected
    if (selectedMsg != null) {
        val isMine = selectedMsg.senderId == myUid
        val isDeletedAll = selectedMsg.deletedForAll || selectedMsg.type == "deleted"
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Ações da mensagem") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Fixar (só se não for placeholder)
                    if (!isDeletedAll) {
                        TextButton(onClick = {
                            vm.pin(chatId, selectedMsg)
                            selected = null
                        }) { Text("Fixar") }
                    }

                    TextButton(onClick = {
                        scope.launch {
                            repo.hideMessageForUser(chatId, selectedMsg.id, myUid)
                            selected = null
                        }
                    }) { Text("Excluir para mim") }

                    // Excluir para todos (só se a msg é minha e ainda não está apagada global)
                    if (isMine && !isDeletedAll) {
                        TextButton(onClick = {
                            scope.launch {
                                repo.deleteMessageForAll(chatId, selectedMsg.id)
                                selected = null
                            }
                        }) { Text("Excluir para todos") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selected = null }) { Text("Cancelar") }
            }
        )
    }
}


@Composable
private fun MessageBubble(
    m: Message,
    isMine: Boolean,
    author: SenderUi?,
    onLongPress: () -> Unit
) {
    val bgMine = MaterialTheme.colorScheme.primaryContainer
    val bgOther = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val bubbleColor = if (isMine) bgMine else bgOther
    val ctx = LocalContext.current

    val isDeletedAll = m.deletedForAll || m.type == "deleted"

    val plain = if (m.type == "text") Crypto.decrypt(m.textEnc) else ""
    val content: AnnotatedString = AnnotatedString(plain)

    val sent = true
    val delivered = m.deliveredTo.isNotEmpty()
    val read = m.readBy.isNotEmpty()

    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
        if (!isMine) Spacer(Modifier.weight(0.15f)) else Spacer(Modifier.weight(0.35f))
        Surface(
            color = bubbleColor,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 1.dp,
            modifier = Modifier
                .weight(0.5f)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {

                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = ImageRequest.Builder(ctx).data(author?.photo).build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isMine) "Você" else (author?.name ?: "@${m.senderId.take(6)}"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(6.dp))

                if (isDeletedAll) {
                    Text(
                        "Mensagem apagada",
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    when (m.type) {
                        "text" -> {
                            Text(content, color = textColor)
                        }
                        "image" -> {
                            if (!m.mediaUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(ctx)
                                        .data(m.mediaUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Imagem",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 120.dp, max = 260.dp)
                                )
                            } else {
                                Text("[imagem]", color = textColor)
                            }
                        }
                        "video" -> {
                            TextButton(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(m.mediaUrl))
                                        ctx.startActivity(intent)
                                    }
                                }
                            ) {
                                Icon(Icons.Outlined.VideoFile, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Abrir vídeo")
                            }
                        }
                        "audio" -> {
                            TextButton(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(m.mediaUrl))
                                        ctx.startActivity(intent)
                                    }
                                }
                            ) {
                                Icon(Icons.Outlined.Mic, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Reproduzir áudio")
                            }
                        }
                        else -> {
                            TextButton(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(m.mediaUrl))
                                        ctx.startActivity(intent)
                                    }
                                }
                            ) {
                                Icon(Icons.Outlined.AttachFile, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Abrir arquivo")
                            }
                        }
                    }
                }

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
