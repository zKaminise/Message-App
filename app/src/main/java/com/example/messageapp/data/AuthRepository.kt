package com.example.messageapp.data

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import com.google.firebase.firestore.SetOptions

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    // Escopo seguro pra tarefas em background
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** -------- Email/senha -------- */
    suspend fun signUpEmail(email: String, pass: String) {
        auth.createUserWithEmailAndPassword(email, pass).await()
        upsertUserProfile()
        saveFcmTokenSafe()
    }

    suspend fun signInEmail(email: String, pass: String) {
        auth.signInWithEmailAndPassword(email, pass).await()
        upsertUserProfile()
        saveFcmTokenSafe()
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    /** -------- Anônimo (opcional) -------- */
    suspend fun signInAnonymouslyAndUpsert() {
        auth.signInAnonymously().await()
        upsertUserProfile()
        saveFcmTokenSafe()
    }

    /** -------- Telefone (OTP) -------- */
    fun phoneVerifyCallbacks(
        onCodeSent: (verificationId: String) -> Unit,
        onInstantSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ): PhoneAuthProvider.OnVerificationStateChangedCallbacks =
        object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(cred: PhoneAuthCredential) {
                auth.signInWithCredential(cred)
                    .addOnSuccessListener {
                        // Rodar funções suspend fora de callbacks, em background
                        ioScope.launch {
                            upsertUserProfile()
                            saveFcmTokenSafe()
                        }
                        onInstantSuccess()
                    }
                    .addOnFailureListener { onError(it) }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                onError(e)
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                onCodeSent(verificationId)
            }
        }

    fun startPhoneVerification(
        activity: Activity,
        phone: String,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
    ) {
        val opts = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(opts)
    }

    suspend fun signInWithPhoneCredential(verificationId: String, code: String) {
        val cred = PhoneAuthProvider.getCredential(verificationId, code)
        auth.signInWithCredential(cred).await()
        upsertUserProfile()
        saveFcmTokenSafe()
    }

    /** -------- Perfil/FCM/Presença -------- */
    suspend fun upsertUserProfile() {
        val u = auth.currentUser ?: return
        val doc = db.collection("users").document(u.uid)

        val data = mapOf(
            "displayName" to (u.displayName ?: u.email ?: u.phoneNumber ?: "User"),
            "photoUrl"     to (u.photoUrl?.toString()),
            "email"        to (u.email ?: ""),
            "phone"        to (u.phoneNumber ?: ""),
            "bio"          to "",
            "isOnline"     to true,
            "lastSeen"     to FieldValue.serverTimestamp()
        )

        // 👉 sem leitura/transaction; só write com merge
        doc.set(data, SetOptions.merge()).await()
    }

    /** BEST-EFFORT: não quebra o login se FCM falhar */
    suspend fun saveFcmTokenSafe() {
        val uid = auth.currentUser?.uid ?: return
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            db.collection("users").document(uid)
                .update("fcmTokens", FieldValue.arrayUnion(token)).await()
        }
        // Se falhar, seguimos sem token; tentamos de novo mais tarde.
    }

    // --------- Apelidos de compatibilidade (para chamadas antigas) ---------
    @Deprecated("Use saveFcmTokenSafe() ou saveFcmTokenInBackground()")
    suspend fun saveFcmToken() = saveFcmTokenSafe()

    fun saveFcmTokenInBackground() {
        ioScope.launch { saveFcmTokenSafe() }
    }
    // ----------------------------------------------------------------------

    suspend fun updatePresence(online: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .update("isOnline", online, "lastSeen", FieldValue.serverTimestamp()).await()
    }

    suspend fun signOutAndRemoveToken() {
        val uid = auth.currentUser?.uid
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            if (uid != null) {
                db.collection("users").document(uid)
                    .update("fcmTokens", FieldValue.arrayRemove(token)).await()
            }
        }
        auth.signOut()
    }
}
