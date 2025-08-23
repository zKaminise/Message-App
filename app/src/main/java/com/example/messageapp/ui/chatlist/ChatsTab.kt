package com.example.messageapp.ui.chatlist

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.messageapp.data.ChatRepository
import com.example.messageapp.viewmodel.ChatListViewModel
import kotlinx.coroutines.launch

@Composable
fun ChatsTab(
    myUid: String,
    vm: ChatListViewModel,
    onOpenChat: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var show by remember { mutableStateOf(false) }
    var other by remember { mutableStateOf("") }
    val repo = remember { ChatRepository() }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { show = true }) { Text("+") }
        }
    ) { insets ->
        ChatListScreen(myUid = myUid, vm = vm, onOpenChat = onOpenChat, modifier = Modifier.padding(insets))
    }

    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val chatId = repo.ensureDirectChat(myUid, other.trim())
                        show = false
                        onOpenChat(chatId)
                    }
                }) { Text("Criar") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Cancelar") } },
            title = { Text("Novo chat") },
            text = {
                OutlinedTextField(
                    value = other, onValueChange = { other = it },
                    label = { Text("UID do usuário") }, singleLine = true
                )
            }
        )
    }
}
