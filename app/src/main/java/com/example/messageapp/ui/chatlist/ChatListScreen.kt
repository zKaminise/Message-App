package com.example.messageapp.ui.chatlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
            runCatching { repo.migrateVisibleForFor(myUid) }  // backfill
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
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }

    // título: nome do outro usuário em chat direto
    LaunchedEffect(c.id, c.type, c.members, myUid) {
        if (c.type == "direct") {
            val other = c.members.firstOrNull { it != myUid }
            if (!other.isNullOrBlank()) {
                val fs = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val snap = fs.collection("users").document(other).get().await()
                title = snap.getString("displayName") ?: "@${other.take(6)}"
            } else title = "Conversa"
        } else title = c.name ?: "Grupo"
    }

    val preview = remember(c.lastMessageEnc, c.lastMessage, c.pinnedSnippet) {
        c.lastMessageEnc?.let { com.example.messageapp.utils.Crypto.decrypt(it) }
            ?: c.pinnedSnippet
            ?: (c.lastMessage ?: "")
    }

    ListItem(
        headlineContent   = { Text(title) },
        supportingContent = { if (preview.isNotBlank()) Text(preview) },
        trailingContent = {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = null)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                // comum
                DropdownMenuItem(
                    text = { Text("Abrir") },
                    onClick = { menu = false; onOpen() }
                )

                if (c.type == "direct") {
                    DropdownMenuItem(
                        text = { Text("Excluir conversa") },
                        onClick = {
                            menu = false
                            confirm = "Excluir conversa? Ela sumirá só pra você."
                        }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Sair do grupo") },
                        onClick = {
                            menu = false
                            confirm = "Sair do grupo?"
                        }
                    )
                    if (c.ownerId == myUid) {
                        DropdownMenuItem(
                            text = { Text("Apagar grupo") },
                            onClick = {
                                menu = false
                                confirm = "Apagar definitivamente o grupo?"
                            }
                        )
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
    )
    Divider()

    if (confirm != null) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Confirmação") },
            text = { Text(confirm!!) },
            confirmButton = {
                TextButton(onClick = {
                    val action = confirm
                    confirm = null
                    scope.launch {
                        when (action) {
                            "Excluir conversa? Ela sumirá só pra você." ->
                                repo.hideChatForUser(c.id!!, myUid)
                            "Sair do grupo?" ->
                                repo.leaveGroup(c.id!!, myUid)
                            "Apagar definitivamente o grupo?" ->
                                repo.deleteGroup(c.id!!)
                        }
                    }
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar") } }
        )
    }
}

