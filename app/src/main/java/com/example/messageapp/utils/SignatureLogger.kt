package com.example.messageapp.utils

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest

object SignatureLogger {

    private fun ByteArray.toHexColons(): String =
        joinToString(":") { "%02x".format(it) }

    private fun digest(bytes: ByteArray, algo: String): String =
        MessageDigest.getInstance(algo).digest(bytes).toHexColons()

    fun log(activity: Activity) {
        try {
            val pm = activity.packageManager
            val pkg = activity.packageName

            val signerBytes: List<ByteArray> = if (Build.VERSION.SDK_INT >= 28) {
                // signingInfo pode ser null em devices antigos/estranhos → usa safe calls e fallback
                val pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                val info = pi.signingInfo
                when {
                    info?.hasMultipleSigners() == true ->
                        info.apkContentsSigners?.map { it.toByteArray() } ?: emptyList()
                    info != null ->
                        info.signingCertificateHistory?.map { it.toByteArray() } ?: emptyList()
                    else -> emptyList()
                }
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
                    .signatures?.map { it.toByteArray() } ?: emptyList()
            }

            if (signerBytes.isEmpty()) {
                Log.w("SignatureLogger", "Nenhuma assinatura encontrada para $pkg")
                return
            }

            signerBytes.forEachIndexed { i, s ->
                val s1 = digest(s, "SHA-1")
                val s256 = digest(s, "SHA-256")
                Log.w("SignatureLogger", "package=$pkg signer#$i  SHA-1=$s1  SHA-256=$s256")
            }
        } catch (e: Exception) {
            Log.e("SignatureLogger", "Falha ao ler assinaturas", e)
        }
    }
}
