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
import com.example.messageapp.model.Chat
import com.example.messageapp.viewmodel.ChatListViewModel

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
    LaunchedEffect(myUid) { vm.start(myUid) }

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
            items(chats) { c -> ChatRow(c) { onOpenChat(c.id!!) } }
        }
    }
}

@Composable
private fun ChatRow(c: Chat, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(c.name ?: c.id ?: "") },
        supportingContent = { Text(c.pinnedSnippet ?: (c.lastMessage ?: "")) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
    Divider()
}
