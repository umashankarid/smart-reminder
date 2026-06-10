package com.komet.smartreminder.email

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.komet.smartreminder.data.db.AppDatabase
import com.komet.smartreminder.data.model.Appointment
import com.komet.smartreminder.parser.AppointmentParser
import java.util.Base64

object GmailScanner {

    fun getSignInOptions() = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(GmailScopes.GMAIL_READONLY))
        .build()

    suspend fun scanEmails(context: Context) {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(GmailScopes.GMAIL_READONLY))
        credential.selectedAccount = account.account

        val gmail = Gmail.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("Smart Reminder")
            .build()

        val db = AppDatabase.get(context)

        try {
            // Get recent messages (last 50)
            val messages = gmail.users().messages().list("me")
                .setMaxResults(50)
                .setQ("newer_than:7d")
                .execute().messages ?: return

            for (msg in messages) {
                val full = gmail.users().messages().get("me", msg.id).setFormat("full").execute()
                val subject = full.payload.headers.find { it.name == "Subject" }?.value ?: ""
                val from = full.payload.headers.find { it.name == "From" }?.value ?: ""

                // Get body text
                val body = extractBody(full.payload)
                val textToScan = "$subject\n$body"

                val parsed = AppointmentParser.parse(textToScan, from) ?: continue

                val existing = db.appointmentDao().findDuplicate(textToScan.take(500), parsed.date)
                if (existing == null) {
                    db.appointmentDao().insert(
                        Appointment(
                            title = if (subject.length > 3) subject.take(80) else parsed.title,
                            date = parsed.date,
                            time = parsed.time,
                            location = parsed.location,
                            source = "email",
                            sourceDetail = from,
                            rawText = textToScan.take(500)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun extractBody(payload: com.google.api.services.gmail.model.MessagePart): String {
        if (payload.body?.data != null) {
            return String(Base64.getUrlDecoder().decode(payload.body.data))
        }
        payload.parts?.forEach { part ->
            if (part.mimeType == "text/plain" && part.body?.data != null) {
                return String(Base64.getUrlDecoder().decode(part.body.data))
            }
        }
        return ""
    }
}
