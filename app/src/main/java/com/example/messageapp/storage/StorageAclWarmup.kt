package com.example.messageapp.storage

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object StorageAclWarmup {
    suspend fun ensureForMyChats() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val qs = db.collection("chats").whereArrayContains("members", uid).get().await()
        qs.documents.forEach { d ->
            val chatId = d.id
            @Suppress("UNCHECKED_CAST")
            val members = (d.get("members") as? List<String>).orEmpty()
            members.forEach { StorageAcl.ensureMemberMarker(chatId, it) }
        }
    }
}
