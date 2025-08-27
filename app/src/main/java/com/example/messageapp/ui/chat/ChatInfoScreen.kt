package com.example.messageapp.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class MemberUi(val uid: String, val name: String, val photo: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInfoScreen(
    chatId: String,
    onBack: () -> Unit = {}
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var title by remember { mutableStateOf("Informações") }
    var photo by remember { mutableStateOf<String?>(null) }
    var type by remember { mutableStateOf("direct") }
    var members by remember { mutableStateOf(listOf<MemberUi>()) }
    var owner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(chatId) {
        val snap = db.collection("chats").document(chatId).get().await()
        type = snap.getString("type") ?: "direct"
        title = snap.getString("name") ?: "Conversa"
        photo = snap.getString("photoUrl")
        owner = snap.getString("ownerId")
        @Suppress("UNCHECKED_CAST")
        val memberIds = (snap.get("members") as? List<String>).orEmpty()

        // Busca usuários em lotes de até 10 ids (limite do IN)
        val all = mutableListOf<MemberUi>()
        val chunks = memberIds.chunked(10)
        for (ch in chunks) {
            val qs = db.collection("users")
                .whereIn(FieldPath.documentId(), ch)
                .get().await()
            qs.documents.forEach { d ->
                all += MemberUi(
                    uid = d.id,
                    name = d.getString("displayName") ?: "@${d.id.take(6)}",
                    photo = d.getString("photoUrl")
                )
            }
        }
        // ordena: dono primeiro (se for grupo)
        members = if (type == "group" && owner != null)
            all.sortedByDescending { it.uid == owner } else all
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                title = { Text(if (type == "group") "Informações do grupo" else "Perfil") }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {

            Row {
                Image(
                    painter = rememberAsyncImagePainter(photo),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp).clip(CircleShape)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    if (type == "group") {
                        Text("${members.size} participantes", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(8.dp))

            if (type == "direct") {
                // único membro “o outro”
                members.firstOrNull()?.let { m ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Image(
                            painter = rememberAsyncImagePainter(m.photo),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp).clip(CircleShape)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(m.name, style = MaterialTheme.typography.titleMedium)
                            Text("@${m.uid.take(6)}")
                        }
                    }
                }
            } else {
                Text("Participantes", style = MaterialTheme.typography.titleMedium)
                LazyColumn {
                    items(members, key = { it.uid }) { m ->
                        ListItem(
                            leadingContent = {
                                Image(
                                    painter = rememberAsyncImagePainter(m.photo),
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp).clip(CircleShape)
                                )
                            },
                            headlineContent = { Text(m.name) },
                            supportingContent = {
                                if (owner == m.uid) Text("Administrador")
                                else Text("@${m.uid.take(6)}")
                            }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}
