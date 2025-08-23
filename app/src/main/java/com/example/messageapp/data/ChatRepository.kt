package com.example.messageapp.data

import com.example.messageapp.model.Chat
import com.example.messageapp.model.Message
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class ChatRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun chats() = db.collection("chats")

    fun directChatIdFor(a: String, b: String) =
        listOf(a, b).sorted().joinToString("_")

    suspend fun ensureDirectChat(uidA: String, uidB: String): String {
        val chatId = directChatIdFor(uidA, uidB)
        val ref = chats().document(chatId)
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            if (!snap.exists()) {
                tx.set(ref, mapOf(
                    "type" to "direct",
                    "members" to listOf(uidA, uidB),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }
        }.await()
        return chatId
    }

    fun observeChats(uid: String, onUpdate: (List<Chat>) -> Unit): ListenerRegistration =
        chats()
            .whereArrayContains("members", uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { qs, _ ->
                val items = qs?.documents?.mapNotNull {
                    it.toObject(Chat::class.java)?.copy(id = it.id)
                }.orEmpty()
                onUpdate(items)
            }

    fun observeChat(chatId: String, onUpdate: (Chat?) -> Unit): ListenerRegistration =
        chats().document(chatId)
            .addSnapshotListener { snap, _ ->
                onUpdate(snap?.toObject(Chat::class.java)?.copy(id = snap.id))
            }

    fun observeMessages(chatId: String, onUpdate: (List<Message>) -> Unit): ListenerRegistration =
        chats().document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { qs, _ ->
                val items = qs?.documents?.mapNotNull {
                    it.toObject(Message::class.java)?.copy(id = it.id)
                }.orEmpty()
                onUpdate(items)
            }

    suspend fun sendText(chatId: String, senderId: String, textEnc: String) {
        val chatRef = chats().document(chatId)
        val msgRef = chatRef.collection("messages").document()
        db.runBatch { b ->
            b.set(msgRef, mapOf(
                "senderId" to senderId,
                "type" to "text",
                "textEnc" to textEnc,
                "createdAt" to FieldValue.serverTimestamp(),
                "deliveredTo" to mapOf<String, Any>(),
                "readBy" to mapOf<String, Any>()
            ))
            b.update(chatRef, mapOf(
                "lastMessage" to "[texto]",
                "updatedAt" to FieldValue.serverTimestamp()
            ))
        }.await()
    }

    suspend fun markAsRead(chatId: String, uid: String) {
        val msgsRef = chats().document(chatId).collection("messages")
        val unread = msgsRef.whereEqualTo("readBy.$uid", null).get().await()
        val batch = db.batch()
        val ts = FieldValue.serverTimestamp()
        unread.documents.forEach { d -> batch.update(d.reference, "readBy.$uid", ts) }
        batch.commit().await()
    }

    suspend fun pinMessage(chatId: String, messageId: String, snippet: String) {
        chats().document(chatId)
            .update(mapOf("pinnedMessageId" to messageId, "pinnedSnippet" to snippet))
            .await()
    }

    suspend fun markDelivered(chatId: String, messageId: String, uid: String) {
        val ref = chats().document(chatId).collection("messages").document(messageId)
        ref.update("deliveredTo.$uid", FieldValue.serverTimestamp()).await()
    }

    suspend fun createGroup(name: String, photoUrl: String?, members: List<String>): String {
        val ref = db.collection("chats").document()
        ref.set(mapOf(
            "type" to "group",
            "members" to members,
            "name" to name,
            "photoUrl" to photoUrl,
            "updatedAt" to FieldValue.serverTimestamp()
        )).await()
        return ref.id
    }
    suspend fun addMember(chatId: String, uid: String) {
        chats().document(chatId).update("members", FieldValue.arrayUnion(uid)).await()
    }
    suspend fun removeMember(chatId: String, uid: String) {
        chats().document(chatId).update("members", FieldValue.arrayRemove(uid)).await()
    }
    suspend fun updateGroupMeta(chatId: String, name: String?, photoUrl: String?) {
        val map = mutableMapOf<String, Any>()
        if (name != null) map["name"] = name
        if (photoUrl != null) map["photoUrl"] = photoUrl
        chats().document(chatId).update(map).await()
    }
    suspend fun unpinMessage(chatId: String) {
        chats().document(chatId)
            .update(mapOf(
                "pinnedMessageId" to FieldValue.delete(),
                "pinnedSnippet" to FieldValue.delete()
            )).await()
    }
}
