@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.messageapp.ui.chatlist

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.messageapp.data.ChatRepository
import com.example.messageapp.model.Chat
import com.example.messageapp.utils.Crypto
import com.example.messageapp.viewmodel.ChatListViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun ChatListScreen(
    myUid: String,
    vm: ChatListViewModel,
    onOpenChat: (String) -> Unit,
    onOpenContacts: () -> Unit,
    onOpenNewGroup: () -> Unit,
    onOpenProfile: () -> Unit,
    onLogout: () -> Unit
) {
    val chats by vm.chats.collectAsState()

    LaunchedEffect(myUid) {
        if (myUid.isNotBlank()) {
            val repo = ChatRepository()
            runCatching { repo.migrateVisibleForFor(myUid) }
            vm.start(myUid)
        }
    }

    var topMenu by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) } // ⬅️ alterna entre "ativas" e "ocultas"

    var confirmLeave by remember { mutableStateOf<Chat?>(null) }
    var confirmDeleteGroup by remember { mutableStateOf<Chat?>(null) }

    val scope = rememberCoroutineScope()
    val repo = remember { ChatRepository() }

    val (activeChats, hiddenChats) = remember(chats, myUid) {
        val act = mutableListOf<Chat>()
        val hid = mutableListOf<Chat>()
        for (c in chats) {
            val visible = c.visibleFor.isNullOrEmpty() || c.visibleFor?.contains(myUid) == true
            if (visible) act += c else hid += c
        }
        act to hid
    }
    val list = if (showHidden) hiddenChats else activeChats

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showHidden) "Conversas ocultas" else "Mensagens") },
                actions = {
                    IconButton(onClick = { topMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = topMenu, onDismissRequest = { topMenu = false }) {
                        // Alterna entre ativas e ocultas
                        DropdownMenuItem(
                            text = { Text(if (showHidden) "Ver conversas ativas" else "Ver conversas ocultas") },
                            onClick = { topMenu = false; showHidden = !showHidden }
                        )
                        DropdownMenuItem(text = { Text("Contatos") }, onClick = {
                            topMenu = false; onOpenContacts()
                        })
                        DropdownMenuItem(text = { Text("Novo grupo") }, onClick = {
                            topMenu = false; onOpenNewGroup()
                        })
                        DropdownMenuItem(text = { Text("Meu perfil") }, onClick = {
                            topMenu = false; onOpenProfile()
                        })
                        DropdownMenuItem(text = { Text("Sair") }, onClick = {
                            topMenu = false; onLogout()
                        })
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenContacts) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            items(list, key = { it.id }) { c ->
                ChatRow(
                    myUid = myUid,
                    chat = c,
                    isHiddenList = showHidden,
                    onOpen = { onOpenChat(c.id) },
                    onHide = { scope.launch { repo.hideChatForUser(c.id, myUid) } },       // “Esconder”
                    onUnhide = { scope.launch { repo.unhideChatForUser(c.id, myUid) } },   // “Reexibir”
                    onDeleteForMe = { scope.launch { repo.hideChatForUser(c.id, myUid) } },// “Excluir conversa”
                    onLeave = { confirmLeave = c },
                    onDeleteGroup = { confirmDeleteGroup = c }
                )
                Divider()
            }
        }
    }

    confirmLeave?.let { chat ->
        AlertDialog(
            onDismissRequest = { confirmLeave = null },
            title = { Text("Sair do grupo") },
            text = { Text("Tem certeza que deseja sair do grupo \"${chat.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.leaveGroup(chat.id, myUid)
                        confirmLeave = null
                    }
                }) { Text("Sair") }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = null }) { Text("Cancelar") } }
        )
    }

    confirmDeleteGroup?.let { chat ->
        AlertDialog(
            onDismissRequest = { confirmDeleteGroup = null },
            title = { Text("Apagar grupo") },
            text = { Text("Apagar o grupo \"${chat.name}\" para todos os participantes? Esta ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.deleteGroup(chat.id)
                        confirmDeleteGroup = null
                    }
                }) { Text("Apagar") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteGroup = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun ChatRow(
    myUid: String,
    chat: Chat,
    isHiddenList: Boolean,
    onOpen: () -> Unit,
    onHide: () -> Unit,
    onUnhide: () -> Unit,
    onDeleteForMe: () -> Unit,
    onLeave: () -> Unit,
    onDeleteGroup: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    var title by remember(chat.id) { mutableStateOf(chat.name ?: chat.id) }
    var avatar by remember(chat.id) { mutableStateOf(chat.photoUrl) }

    LaunchedEffect(chat.id, chat.type, chat.members, myUid) {
        if (chat.type == "direct") {
            val other = chat.members.firstOrNull { it != myUid }
            if (!other.isNullOrBlank()) {
                val fs = FirebaseFirestore.getInstance()
                val snap = fs.collection("users").document(other).get().await()
                title = snap.getString("displayName") ?: "@${other.take(6)}"
                avatar = snap.getString("photoUrl")
            } else {
                title = "Conversa"
                avatar = null
            }
        } else {
            title = chat.name ?: "Grupo"
            avatar = chat.photoUrl
        }
    }

    val snippet = remember(chat.lastMessageEnc, chat.lastMessage, chat.pinnedSnippet) {
        chat.lastMessageEnc?.let { Crypto.decrypt(it) }
            ?: chat.pinnedSnippet
            ?: (chat.lastMessage ?: "")
    }

    ElevatedCard(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        ListItem(
            leadingContent = {
                Avatar(avatar, title.take(1).uppercase())
            },
            headlineContent = {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                if (snippet.isNotBlank()) {
                    Text(snippet, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            trailingContent = {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Mais opções")
                    }
                    ChatRowMenu(
                        expanded = menuOpen,
                        onDismiss = { menuOpen = false },
                        chat = chat,
                        myUid = myUid,
                        isHiddenList = isHiddenList,
                        onHide = { menuOpen = false; onHide() },
                        onUnhide = { menuOpen = false; onUnhide() },
                        onDeleteForMe = { menuOpen = false; onDeleteForMe() },
                        onLeave = { menuOpen = false; onLeave() },
                        onDeleteGroup = { menuOpen = false; onDeleteGroup() }
                    )
                }
            },
            modifier = Modifier.clickable { onOpen() }
        )
    }
}

@Composable
private fun Avatar(url: String?, fallback: String) {
    if (!url.isNullOrBlank()) {
        Image(
            painter = rememberAsyncImagePainter(url),
            contentDescription = null,
            modifier = Modifier.size(44.dp).clip(CircleShape)
        )
    } else {
        androidx.compose.material3.Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(44.dp)
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(fallback, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ChatRowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    chat: Chat,
    myUid: String,
    isHiddenList: Boolean,
    onHide: () -> Unit,
    onUnhide: () -> Unit,
    onDeleteForMe: () -> Unit,
    onLeave: () -> Unit,
    onDeleteGroup: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (isHiddenList) {
            DropdownMenuItem(
                text = { Text("Reexibir conversa") },
                onClick = onUnhide
            )
        } else {
            DropdownMenuItem(
                text = { Text("Esconder conversa") },
                onClick = onHide
            )
            if (chat.type == "direct") {
                DropdownMenuItem(
                    text = { Text("Excluir conversa") },
                    onClick = onDeleteForMe
                )
            }
        }

        if (chat.type == "group") {
            DropdownMenuItem(
                text = { Text("Sair do grupo") },
                onClick = onLeave
            )
            if (chat.ownerId == myUid) {
                DropdownMenuItem(
                    text = { Text("Apagar grupo para todos") },
                    onClick = onDeleteGroup
                )
            }
        }
    }
}
