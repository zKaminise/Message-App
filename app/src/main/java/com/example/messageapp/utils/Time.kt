package com.example.messageapp.utils

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object Time {
    private val dayFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun headerFor(ts: Timestamp?): String {
        if (ts == null) return ""
        val date = ts.toDate()
        val cal = Calendar.getInstance()
        val today = dayFmt.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = dayFmt.format(cal.time)
        val target = dayFmt.format(date)
        return when (target) {
            today -> "Hoje"
            yesterday -> "Ontem"
            else -> target
        }
    }

    fun timeFor(ts: Timestamp?): String = if (ts == null) "" else timeFmt.format(ts.toDate())

    fun sameDay(a: Timestamp?, b: Timestamp?): Boolean {
        if (a == null || b == null) return false
        val da = dayFmt.format(a.toDate())
        val db = dayFmt.format(b.toDate())
        return da == db
    }
}
