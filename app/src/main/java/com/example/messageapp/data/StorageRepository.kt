package com.example.messageapp.data

import android.net.Uri
import com.example.messageapp.storage.StorageAcl
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class StorageRepository(
    private val st: FirebaseStorage = FirebaseStorage.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun sendMedia(chatId: String, myUid: String, uri: Uri, type: String) {
        StorageAcl.ensureMemberMarker(chatId, myUid)

        val ext = when (type) {
            "image" -> "jpg"
            "video" -> "mp4"
            "audio" -> "m4a"
            else -> "bin"
        }
        val name = "${System.currentTimeMillis()}_${myUid}.$ext"
        val ref = st.reference.child("chats/$chatId/$type/$name")

        ref.putFile(uri).await()
        val url = ref.downloadUrl.await().toString()

        val chatRef = db.collection("chats").document(chatId)
        val msgRef = chatRef.collection("messages").document()

        db.runBatch { b ->
            b.set(
                msgRef,
                mapOf(
                    "senderId" to myUid,
                    "type" to type,
                    "textEnc" to "",
                    "mediaUrl" to url,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "deliveredTo" to mapOf<String, Any>(),
                    "readBy" to mapOf<String, Any>()
                )
            )
            b.update(
                chatRef,
                mapOf("lastMessage" to "[$type]", "updatedAt" to FieldValue.serverTimestamp())
            )
        }.await()
    }
}
