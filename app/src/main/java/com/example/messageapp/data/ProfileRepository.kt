package com.example.messageapp.data

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class ProfileRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {
    suspend fun updateProfile(displayName: String, bio: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("users").document(uid).update(
            mapOf("displayName" to displayName, "bio" to bio)
        ).await()
    }


    suspend fun uploadAvatar(uri: Uri): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return ""
        val ref = storage.reference.child("avatars/$uid/avatar.jpg")

        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .build()

        ref.putFile(uri, metadata).await()
        val url = ref.downloadUrl.await().toString()
        db.collection("users").document(uid).update("photoUrl", url).await()
        return url
    }
}
