package com.komet.smartreminder.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.komet.smartreminder.data.db.AppDatabase
import com.komet.smartreminder.data.model.Appointment
import com.komet.smartreminder.parser.AppointmentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val fullText = messages.joinToString("") { it.messageBody }
        val sender = messages.firstOrNull()?.originatingAddress ?: ""

        processMessage(context, fullText, sender)
    }

    companion object {
        fun processMessage(context: Context, text: String, sender: String) {
            val parsed = AppointmentParser.parse(text, sender) ?: return

            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.get(context)
                // Avoid duplicates
                val existing = db.appointmentDao().findDuplicate(text, parsed.date)
                if (existing != null) return@launch

                db.appointmentDao().insert(
                    Appointment(
                        title = parsed.title,
                        date = parsed.date,
                        time = parsed.time,
                        location = parsed.location,
                        source = "sms",
                        sourceDetail = sender,
                        rawText = text
                    )
                )
            }
        }
    }
}
