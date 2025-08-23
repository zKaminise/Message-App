package com.example.messageapp.ui.contacts

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.messageapp.data.ChatRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

data class ContactItem(
    val uid: String,
    val name: String,
    val photo: String?
)

@Composable
fun ContactsScreen(
    myUid: String,
    onOpenChat: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var items by remember { mutableStateOf(listOf<ContactItem>()) }
    val scope = rememberCoroutineScope()
    val repo = remember { ChatRepository() }

    DisposableEffect(Unit) {
        var reg: ListenerRegistration? = null
        reg = db.collection("users")
            .addSnapshotListener { qs, _ ->
                items = qs?.documents
                    ?.mapNotNull {
                        val uid = it.id
                        if (uid == myUid) null else ContactItem(
                            uid = uid,
                            name = it.getString("displayName") ?: ("@" + uid.take(5)),
                            photo = it.getString("photoUrl")
                        )
                    }.orEmpty()
            }
        onDispose { reg?.remove() }
    }

    val filtered = remember(items, query) {
        val q = query.text.trim()
        if (q.isBlank()) items else items.filter {
            it.name.contains(q, true) || it.uid.contains(q, true)
        }
    }

    Column(modifier.padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar contatos") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered) { u ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val painter = rememberAsyncImagePainter(u.photo)
                        Image(painter, contentDescription = null, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.name, style = MaterialTheme.typography.titleMedium)
                            Text("@${u.uid.take(6)}")
                        }
                        Button(onClick = {
                            scope.launch {
                                val chatId = repo.ensureDirectChat(myUid, u.uid)
                                onOpenChat(chatId)
                            }
                        }) { Text("Conversar") }
                    }
                }
            }
        }
    }
}
