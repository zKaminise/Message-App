package com.example.messageapp.ui.groups

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.messageapp.data.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class UserItem(val uid: String, val name: String, val photo: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCreateScreen(
    onCreated: (String) -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit = {}
) {
    val myUid = remember { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    val db = remember { FirebaseFirestore.getInstance() }
    val st = remember { FirebaseStorage.getInstance() }
    val repo = remember { ChatRepository() }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var users by remember { mutableStateOf(listOf<UserItem>()) }
    val selected = remember { mutableStateListOf<String>() }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(myUid) {
        if (myUid.isNotBlank() && !selected.contains(myUid)) selected += myUid
    }

    LaunchedEffect(Unit) {
        db.collection("users").get().addOnSuccessListener { qs ->
            users = qs.documents.mapNotNull {
                val uid = it.id
                val nameU = it.getString("displayName") ?: "@${uid.take(6)}"
                val photo = it.getString("photoUrl")
                UserItem(uid, nameU, photo)
            }
        }
    }

    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> photoUri = uri }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                title = { Text("Novo grupo") }
            )
        }
    ) { insets ->
        Column(Modifier.padding(insets).padding(16.dp)) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Nome do grupo") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(onClick = { pick.launch("image/*") }) { Text("Foto do grupo") }
                Spacer(Modifier.width(12.dp))
                photoUri?.let { Image(rememberAsyncImagePainter(it), null, Modifier.size(48.dp)) }
            }
            Spacer(Modifier.height(12.dp))

            Text("Participantes", style = MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.weight(1f)) {
                items(users) { u ->
                    val checked = selected.contains(u.uid)
                    ListItem(
                        headlineContent = { Text(u.name) },
                        supportingContent = { Text("@${u.uid.take(6)}") },
                        leadingContent = {
                            Image(rememberAsyncImagePainter(u.photo), null, Modifier.size(40.dp))
                        },
                        trailingContent = {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (it) selected.add(u.uid) else selected.remove(u.uid)
                                }
                            )
                        }
                    )
                    Divider()
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel) { Text("Cancelar") }
                Button(onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) { msg = "Informe o nome do grupo"; return@Button }
                    if (selected.isEmpty()) { msg = "Selecione ao menos 1 participante"; return@Button }

                    scope.launch {
                        runCatching {
                            val chatId = repo.createGroup(
                                name = trimmed,
                                ownerId = myUid,
                                members = selected.toList(),
                                photoUrl = null // setaremos depois se tiver foto
                            )

                            val finalPhotoUrl = if (photoUri != null) {
                                val ref = st.reference.child("chats/$chatId/group_photo.jpg")
                                ref.putFile(photoUri!!).await()
                                ref.downloadUrl.await().toString()
                            } else null

                            if (finalPhotoUrl != null) {
                                repo.updateGroupMeta(chatId, name = null, photoUrl = finalPhotoUrl)
                            }

                            chatId
                        }.onSuccess { id -> onCreated(id) }
                            .onFailure { e -> msg = e.message }
                    }
                }) { Text("Criar") }
            }

            msg?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
