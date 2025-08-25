package com.example.messageapp.storage

import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

object StorageAcl {
    private val st = FirebaseStorage.getInstance()

    suspend fun ensureMemberMarker(chatId: String, uid: String) {
        val ref = st.reference.child("chats/$chatId/members/$uid")
        try {
            ref.metadata.await()
        } catch (e: Exception) {
            ref.putBytes(ByteArray(0)).await()
        }
    }

    suspend fun removeMemberMarker(chatId: String, uid: String) {
        val ref = st.reference.child("chats/$chatId/members/$uid")
        try { ref.delete().await() } catch (_: Exception) {}
    }
}
