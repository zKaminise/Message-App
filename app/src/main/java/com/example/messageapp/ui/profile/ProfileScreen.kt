package com.example.messageapp.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.messageapp.data.AuthRepository
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun ProfileScreen(
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repo = remember { AuthRepository() }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(TextFieldValue("")) }
    var photoUrl by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val uid = Firebase.auth.currentUser?.uid ?: return@LaunchedEffect
        val doc = Firebase.firestore.collection("users").document(uid).get().await()
        name = TextFieldValue(doc.getString("displayName") ?: "")
        photoUrl = doc.getString("photoUrl")
    }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    runCatching {
                        val uid = Firebase.auth.currentUser?.uid ?: return@launch
                        val ref = Firebase.storage.reference.child("avatars/$uid.jpg")
                        ref.putFile(uri).await()
                        val url = ref.downloadUrl.await().toString()
                        Firebase.firestore.collection("users").document(uid)
                            .update(mapOf("photoUrl" to url, "displayName" to name.text))
                            .await()
                        photoUrl = url
                    }.onFailure { status = it.message }
                }
            }
        }
    )

    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val painter = rememberAsyncImagePainter(photoUrl)
            Image(painter, contentDescription = null, modifier = Modifier.size(64.dp))
            Column(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Seu nome") }, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickImage.launch("image/*") }) { Text("Trocar foto") }
                    Button(onClick = {
                        scope.launch {
                            runCatching {
                                val uid = Firebase.auth.currentUser?.uid ?: return@launch
                                Firebase.firestore.collection("users").document(uid)
                                    .update("displayName", name.text).await()
                            }.onFailure { status = it.message }
                        }
                    }) { Text("Salvar") }
                }
            }
        }

        Divider()

        Button(
            onClick = {
                scope.launch {
                    runCatching { repo.signOutAndRemoveToken() }
                        .onSuccess { onLoggedOut() }
                        .onFailure { status = it.message }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) { Text("Sair") }

        status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
