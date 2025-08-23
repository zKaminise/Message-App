package com.example.messageapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messageapp.data.ChatRepository
import com.example.messageapp.model.Chat
import com.example.messageapp.model.Message
import com.example.messageapp.utils.Crypto
import com.google.firebase.firestore.ListenerRegistration
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
        val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        regMsgs?.remove()
        regMsgs = repo.observeMessages(chatId) { list ->
            _messages.value = list
            if (myUid != null) {
                viewModelScope.launch {
                    list.filter { it.senderId != myUid && !it.deliveredTo.containsKey(myUid) }
                        .forEach { msg ->
                            repo.markDelivered(chatId, msg.id, myUid)
                        }
                }
            }
        }
    }


    fun stop() {
        regChat?.remove()
        regMsgs?.remove()
    }

    fun markRead(chatId: String, myUid: String) = viewModelScope.launch {
        repo.markAsRead(chatId, myUid)
    }

    fun sendText(chatId: String, myUid: String, plain: String) = viewModelScope.launch {
        val enc = Crypto.encrypt(plain)
        repo.sendText(chatId, myUid, enc)
    }

    fun pin(chatId: String, m: Message) = viewModelScope.launch {
        val snippet = if (m.type == "text") Crypto.decrypt(m.textEnc).take(60) else "[${m.type}]"
        repo.pinMessage(chatId, m.id, snippet)
    }

    fun unpin(chatId: String) = viewModelScope.launch { repo.unpinMessage(chatId) }
}
