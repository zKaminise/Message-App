package com.example.messageapp.ui.chatlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.messageapp.data.ChatRepository
import com.example.messageapp.model.Chat
import com.example.messageapp.viewmodel.ChatListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState

@Composable
fun ChatListScreen(
    myUid: String,
    vm: ChatListViewModel,
    onOpenChat: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val chats by vm.chats.collectAsState(initial = emptyList())
    LaunchedEffect(myUid) { vm.start(myUid) }

    Column(Modifier.fillMaxSize()) {
        NewDirectChatBar(myUid, onOpenChat)
        Divider()
        LazyColumn {
            items(chats) { c ->
                ChatRow(c, onOpenChat)
                Divider()
            }
        }
    }
}

@Composable
private fun NewDirectChatBar(myUid: String, onOpenChat: (String) -> Unit) {
    var otherUid by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Row(Modifier
        .fillMaxWidth()
        .padding(8.dp)) {

        OutlinedTextField(
            value = otherUid,
            onValueChange = { otherUid = it },
            label = { Text("UID para chat 1:1") },
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = {
            if (otherUid.isNotBlank()) {
                scope.launch {
                    val chatId = withContext(Dispatchers.IO) {
                        ChatRepository().ensureDirectChat(myUid, otherUid)
                    }
                    onOpenChat(chatId)
                }
            }
        }) { Text("Abrir") }
    }
}

@Composable
private fun ChatRow(c: Chat, onOpenChat: (String) -> Unit) {
    ListItem(
        headlineContent   = { Text(text = c.name ?: c.id) },
        supportingContent = { Text(text = c.pinnedSnippet ?: (c.lastMessage ?: "")) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenChat(c.id) }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}
