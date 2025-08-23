package com.example.messageapp.data

import android.content.ContentResolver
import android.provider.ContactsContract
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ContactsRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun col(uid: String) = db.collection("contacts").document(uid).collection("items")

    suspend fun addContact(myUid: String, otherUid: String, alias: String?) {
        col(myUid).document(otherUid).set(
            mapOf("alias" to (alias ?: ""), "createdAt" to FieldValue.serverTimestamp())
        ).await()
    }
    suspend fun removeContact(myUid: String, otherUid: String) {
        col(myUid).document(otherUid).delete().await()
    }
    suspend fun listContacts(myUid: String): List<Map<String, Any?>> {
        return col(myUid).get().await().documents.map { it.data ?: emptyMap() }
    }

    fun importDeviceContacts(resolver: ContentResolver): List<String> {
        val phones = mutableListOf<String>()
        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )
        cursor?.use {
            val idx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val raw = it.getString(idx) ?: continue
                phones.add(raw.replace("\\s+".toRegex(), "")) // normaliza simples
            }
        }
        return phones.distinct()
    }
}
