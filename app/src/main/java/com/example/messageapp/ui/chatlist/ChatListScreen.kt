package com.example.messageapp.ui.chatlist

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.messageapp.data.ChatRepository
import com.example.messageapp.model.Chat
import com.example.messageapp.viewmodel.ChatListViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
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

    var menu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mensagens") },
                actions = {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, null)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Contatos") }, onClick = { menu = false; onOpenContacts() })
                        DropdownMenuItem(text = { Text("Novo grupo") }, onClick = { menu = false; onOpenNewGroup() })
                        DropdownMenuItem(text = { Text("Meu perfil") }, onClick = { menu = false; onOpenProfile() })
                        DropdownMenuItem(text = { Text("Sair") }, onClick = { menu = false; onLogout() })
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenContacts) {
                Icon(Icons.Default.Add, null)
            }
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            items(chats) { c ->
                ChatRow(myUid = myUid, c = c) { onOpenChat(c.id!!) }
            }
        }
    }
}

@Composable
private fun ChatRow(
    myUid: String,
    c: Chat,
    onOpen: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val repo = remember { ChatRepository() }

    var title by remember(c.id) { mutableStateOf(c.name ?: c.id ?: "") }
    var avatar by remember(c.id) { mutableStateOf<String?>(c.photoUrl) }

    // carrega nome e avatar do outro usuário em chat direto
    LaunchedEffect(c.id, c.type, c.members, myUid) {
        if (c.type == "direct") {
            val other = c.members.firstOrNull { it != myUid }
            if (!other.isNullOrBlank()) {
                val fs = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val snap = fs.collection("users").document(other).get().await()
                title = snap.getString("displayName") ?: "@${other.take(6)}"
                avatar = snap.getString("photoUrl")
            } else {
                title = "Conversa"
                avatar = null
            }
        } else {
            title = c.name ?: "Grupo"
            avatar = c.photoUrl
        }
    }

    val preview = remember(c.lastMessageEnc, c.lastMessage, c.pinnedSnippet) {
        c.lastMessageEnc?.let { com.example.messageapp.utils.Crypto.decrypt(it) }
            ?: c.pinnedSnippet
            ?: (c.lastMessage ?: "")
    }

    ListItem(
        leadingContent = {
            Avatar(avatar, title.take(1).uppercase())
        },
        headlineContent   = { Text(title) },
        supportingContent = { if (preview.isNotBlank()) Text(preview, maxLines = 1) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
    )
    Divider()
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
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(44.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(fallback, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
