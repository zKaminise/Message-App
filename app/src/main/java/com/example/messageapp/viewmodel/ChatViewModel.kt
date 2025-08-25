package com.example.messageapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messageapp.data.ChatRepository
import com.example.messageapp.model.Chat
import com.example.messageapp.model.Message
import com.example.messageapp.utils.Crypto
import com.example.messageapp.storage.StorageAcl
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repo: ChatRepository = ChatRepository()
) : ViewModel() {

    private val _chat = MutableStateFlow<Chat?>(null)
    val chat = _chat.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private var regChat: ListenerRegistration? = null
    private var regMsgs: ListenerRegistration? = null

    fun start(chatId: String) {
        val myUid: String? = FirebaseAuth.getInstance().currentUser?.uid

        if (!myUid.isNullOrBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { StorageAcl.ensureMemberMarker(chatId, myUid) }
            }
        }

        regChat?.remove()
        regChat = repo.observeChat(chatId) { c ->
            _chat.value = c
        }

        regMsgs?.remove()
        regMsgs = repo.observeMessages(chatId, myUid) { list ->
            _messages.value = list

            if (!myUid.isNullOrBlank()) {
                viewModelScope.launch {
                    runCatching { repo.markAsRead(chatId, myUid) }
                }
            }
        }
    }

    fun stop() {
        regChat?.remove()
        regMsgs?.remove()
        regChat = null
        regMsgs = null
    }

    fun markRead(chatId: String, myUid: String) = viewModelScope.launch {
        repo.markAsRead(chatId, myUid)
    }

    fun sendText(chatId: String, myUid: String, plain: String) = viewModelScope.launch {
        val enc = Crypto.encrypt(plain)
        repo.sendText(chatId, myUid, enc)
    }

    fun pin(chatId: String, m: Message) = viewModelScope.launch {
        val id = m.id ?: return@launch
        val snippet = if (m.type == "text") {
            Crypto.decrypt(m.textEnc).take(60)
        } else {
            "[${m.type}]"
        }
        repo.pinMessage(chatId, id, snippet)
    }

    fun unpin(chatId: String) = viewModelScope.launch {
        repo.unpinMessage(chatId)
    }
}
