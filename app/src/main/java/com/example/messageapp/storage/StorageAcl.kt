package com.example.messageapp.storage

import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

object StorageAcl {
    private val st = FirebaseStorage.getInstance()

    suspend fun ensureMemberMarker(chatId: String, uid: String) {
        val ref = st.reference.child("chats/$chatId/members/$uid")
        runCatching {
            ref.putBytes(ByteArray(0)).await()
        }.onFailure {
            Log.w("StorageAcl", "ensureMemberMarker falhou: chat=$chatId uid=$uid", it)
        }
    }

    suspend fun removeMemberMarker(chatId: String, uid: String) {
        val ref = st.reference.child("chats/$chatId/members/$uid")
        runCatching { ref.delete().await() }
            .onFailure { Log.w("StorageAcl", "removeMemberMarker falhou: chat=$chatId uid=$uid", it) }
    }
}
