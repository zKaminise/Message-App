package com.example.messageapp.utils

import android.Manifest
import android.content.ContentResolver
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

data class DeviceContact(val name: String, val phones: List<String>)

@Composable
fun rememberDeviceContactsDetailed(): Pair<List<DeviceContact>, () -> Unit> {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) items = loadContacts(ctx.contentResolver)
    }
    return items to { launcher.launch(Manifest.permission.READ_CONTACTS) }
}

private fun loadContacts(cr: ContentResolver): List<DeviceContact> {
    val list = mutableListOf<DeviceContact>()
    val cursor = cr.query(
        ContactsContract.Contacts.CONTENT_URI, null, null, null, null
    ) ?: return emptyList()
    cursor.use {
        val idIdx = it.getColumnIndex(ContactsContract.Contacts._ID)
        val nameIdx = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
        val hasPhoneIdx = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
        while (it.moveToNext()) {
            val id = it.getString(idIdx)
            val name = it.getString(nameIdx) ?: ""
            val hasPhone = it.getInt(hasPhoneIdx) > 0
            val phones = mutableListOf<String>()
            if (hasPhone) {
                val pCur = cr.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
                    arrayOf(id),
                    null
                )
                pCur?.use { pc ->
                    val numIdx = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (pc.moveToNext()) phones += pc.getString(numIdx)
                }
            }
            list += DeviceContact(name, phones)
        }
    }
    return list
}
