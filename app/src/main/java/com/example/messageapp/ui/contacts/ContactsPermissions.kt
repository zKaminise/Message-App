package com.example.messageapp.ui.contacts

import android.Manifest
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

data class PhoneContact(val displayName: String, val phone: String)
@Composable
fun rememberDeviceContacts(): Pair<List<PhoneContact>, () -> Unit> {
    val ctx = LocalContext.current
    var contacts by remember { mutableStateOf<List<PhoneContact>>(emptyList()) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) return@rememberLauncherForActivityResult

        val list = mutableListOf<PhoneContact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection, null, null, null
        )?.use { cur ->
            val idxName = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val idxNum  = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cur.moveToNext()) {
                val name = if (idxName >= 0) cur.getString(idxName) else ""
                val num  = if (idxNum >= 0) cur.getString(idxNum) else ""
                if (name.isNotBlank() || num.isNotBlank()) {
                    list += PhoneContact(displayName = name, phone = num)
                }
            }
        }

        contacts = list
    }

    val request: () -> Unit = { launcher.launch(Manifest.permission.READ_CONTACTS) }
    return Pair(contacts, request)
}
