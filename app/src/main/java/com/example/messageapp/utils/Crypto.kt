package com.example.messageapp.utils

import android.util.Base64

object Crypto {
    fun encrypt(plain: String): String =
        Base64.encodeToString(plain.toByteArray(), Base64.NO_WRAP)

    fun decrypt(enc: String?): String =
        if (enc.isNullOrBlank()) "" else String(Base64.decode(enc, Base64.NO_WRAP))
}
