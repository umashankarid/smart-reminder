package com.komet.smartreminder.sms

import android.content.Context
import android.net.Uri
import com.komet.smartreminder.data.db.AppDatabase
import com.komet.smartreminder.data.model.Appointment
import com.komet.smartreminder.parser.AppointmentParser

object SmsScanner {
    suspend fun scanExistingSms(context: Context) {
        val db = AppDatabase.get(context)
        val cursor = context.contentResolver.query(
            Uri.parse("content://sms/inbox"), arrayOf("body", "address", "date"),
            null, null, "date DESC LIMIT 200"
        ) ?: return

        cursor.use {
            val bodyIdx = it.getColumnIndex("body")
            val addrIdx = it.getColumnIndex("address")
            while (it.moveToNext()) {
                val body = it.getString(bodyIdx) ?: continue
                val sender = it.getString(addrIdx) ?: ""
                val parsed = AppointmentParser.parse(body, sender) ?: continue

                val existing = db.appointmentDao().findDuplicate(body, parsed.date)
                if (existing == null) {
                    db.appointmentDao().insert(
                        Appointment(
                            title = parsed.title,
                            date = parsed.date,
                            time = parsed.time,
                            location = parsed.location,
                            source = "sms",
                            sourceDetail = sender,
                            rawText = body
                        )
                    )
                }
            }
        }
    }
}
