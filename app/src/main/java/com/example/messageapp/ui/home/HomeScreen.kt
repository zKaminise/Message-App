package com.example.messageapp.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.messageapp.ui.chatlist.ChatListScreen
import com.example.messageapp.ui.contacts.ContactsScreen
import com.example.messageapp.ui.profile.ProfileScreen
import com.example.messageapp.viewmodel.ChatListViewModel
import com.google.firebase.auth.FirebaseAuth

@Composable
fun HomeScreen(
    onOpenChat: (String) -> Unit,
    onLoggedOut: () -> Unit,
    onOpenNewGroup: () -> Unit = {},
    onOpenContacts: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    var tab by remember { mutableStateOf(0) }
    val myUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val listVm = remember { ChatListViewModel() }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Chat, contentDescription = null) },
                    label = { Text("Chats") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Contacts, contentDescription = null) },
                    label = { Text("Contatos") }
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("Perfil") }
                )
            }
        }
    ) { insets ->
        when (tab) {
            0 -> ChatListScreen(
                myUid = myUid,
                vm = listVm,
                onOpenChat = onOpenChat,
                onOpenContacts = { tab = 1; onOpenContacts() },
                onOpenNewGroup = onOpenNewGroup,
                onOpenProfile = { tab = 2; onOpenProfile() },
                onLogout = onLoggedOut
            )
            1 -> ContactsScreen(
                myUid = myUid,
                onOpenChat = onOpenChat,
                modifier = Modifier.padding(insets)
            )
            2 -> ProfileScreen(
                onLoggedOut = onLoggedOut,
                modifier = Modifier.padding(insets)
            )
        }
    }
}
