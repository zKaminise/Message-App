package com.example.messageapp.data

import android.net.Uri
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class StorageRepository(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun sendImage(chatId: String, senderId: String, uri: Uri) =
        sendMedia(chatId, senderId, uri, "image")

    suspend fun sendMedia(chatId: String, senderId: String, uri: Uri, type: String) {
        val ext = when (type) {
            "image" -> "jpg"
            "video" -> "mp4"
            "audio" -> "m4a"
            else -> "bin"
        }
        val path = "chats/$chatId/${System.currentTimeMillis()}-$type.$ext"
        val ref = storage.reference.child(path)
        ref.putFile(uri).await()
        val download = ref.downloadUrl.await().toString()

        val chatRef = db.collection("chats").document(chatId)
        val msgRef = chatRef.collection("messages").document()
        db.runBatch { b ->
            b.set(msgRef, mapOf(
                "senderId" to senderId,
                "type" to type,
                "mediaUrl" to download,
                "createdAt" to FieldValue.serverTimestamp(),
                "deliveredTo" to mapOf<String, Any>(),
                "readBy" to mapOf<String, Any>()
            ))
            b.update(chatRef, mapOf(
                "lastMessage" to "[$type]",
                "updatedAt" to FieldValue.serverTimestamp()
            ))
        }.await()
    }
}
