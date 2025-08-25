package com.example.messageapp.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.messageapp.data.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLoggedOut: () -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db   = remember { FirebaseFirestore.getInstance() }
    val st   = remember { FirebaseStorage.getInstance() }
    val scope = rememberCoroutineScope()
    val repo = remember { AuthRepository() }

    val uid = auth.currentUser?.uid ?: return

    var name by remember { mutableStateOf(auth.currentUser?.displayName ?: "") }
    var bio  by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf<String?>(auth.currentUser?.photoUrl?.toString()) }
    var msg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uid) {
        runCatching {
            val snap = db.collection("users").document(uid).get().await()
            bio = snap.getString("bio") ?: ""
            if (name.isBlank()) name = snap.getString("displayName") ?: name
            snap.getString("photoUrl")?.let { photoUrl = it }
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val ref = st.reference.child("avatars/$uid.jpg")
                    ref.putFile(uri).await()
                    ref.downloadUrl.await().toString()
                }.onSuccess { url -> photoUrl = url }
                    .onFailure { msg = it.message }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                title = { Text("Meu perfil") }
            )
        }
    ) { insets ->
        Column(modifier.padding(insets).padding(16.dp)) {

            val painter = rememberAsyncImagePainter(photoUrl)
            Image(painter, contentDescription = null, modifier = Modifier.size(88.dp))
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { pick.launch("image/*") }) { Text("Trocar foto") }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(bio, { bio = it }, label = { Text("Bio") }, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                scope.launch {
                    runCatching {
                        val doc = db.collection("users").document(uid)
                        val data = mutableMapOf<String, Any>(
                            "displayName" to name,
                            "bio" to bio,
                            "lastSeen" to FieldValue.serverTimestamp()
                        )
                        photoUrl?.let { data["photoUrl"] = it }
                        doc.update(data).await()
                    }.onSuccess { msg = "Salvo!" }
                        .onFailure { msg = it.message }
                }
            }) { Text("Salvar") }

            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        runCatching { repo.signOutAndRemoveToken() }
                        onLoggedOut()
                    }
                }
            ) { Text("Sair da conta") }

            msg?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
