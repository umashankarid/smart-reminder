package com.komet.smartreminder.parser

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

data class ParsedAppointment(
    val title: String,
    val date: String,
    val time: String = "",
    val location: String = ""
)

/**
 * On-device appointment parser. No network calls, no data leaves the device.
 * Detects appointments, bookings, reservations from SMS/email text.
 */
object AppointmentParser {

    // Multi-language appointment keywords - auto-detected
    private val keywordsByLang = mapOf(
        "en" to listOf("appointment", "booking", "reservation", "scheduled", "confirmed", "booked", "reminder", "visit", "meeting", "session", "check-in", "check-up"),
        "sv" to listOf("bokat", "bokad", "bokning", "bekräftelse", "tid hos", "besök", "termin", "möte", "påminnelse", "kallelse", "inbokat", "välkommen till"),
        "de" to listOf("termin", "buchung", "reservierung", "bestätigt", "vereinbart", "treffen", "sitzung", "erinnerung"),
        "fr" to listOf("rendez-vous", "réservation", "confirmé", "réunion", "visite", "rappel"),
        "es" to listOf("cita", "reserva", "confirmado", "reunión", "visita", "recordatorio")
    )

    private val allKeywords = keywordsByLang.values.flatten()

    private fun detectLanguage(text: String): String {
        val lower = text.lowercase()
        return keywordsByLang.maxByOrNull { (_, keywords) -> keywords.count { lower.contains(it) } }?.key ?: "en"
    }

    private val datePatterns = listOf(
        Pattern.compile("(\\d{4}-\\d{2}-\\d{2})"),
        Pattern.compile("(\\d{1,2}[/.]\\d{1,2}[/.]\\d{4})"),
        Pattern.compile("(\\d{1,2}[/.]\\d{1,2}[/.]\\d{2})"),
        Pattern.compile("(\\d{1,2}\\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|januari|februari|mars|april|maj|juni|juli|augusti|september|oktober|november|december|janvier|février|mars|avril|mai|juin|juillet|août|septembre|octobre|novembre|décembre|enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre|januar|februar|märz|april|mai|juni|juli|august|september|oktober|november|dezember)\\s+\\d{4})", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday|måndag|tisdag|onsdag|torsdag|fredag|lördag|söndag|montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag|lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche)\\s+(\\d{1,2}[/.]\\d{1,2})", Pattern.CASE_INSENSITIVE)
    )

    private val timePattern = Pattern.compile("(?:kl[.]?\\s*|at\\s+|um\\s+|à\\s+)?(\\d{1,2}[:.:]\\d{2})(?:\\s*(?:am|pm))?", Pattern.CASE_INSENSITIVE)

    private val locationKeywords = listOf(
        "at ", "location:", "plats:", "address:", "adress:", "venue:", "lokal:",
        "ort:", "lieu:", "dirección:", "ubicación:", "standort:"
    )

    // "tomorrow" in multiple languages
    private val tomorrowWords = listOf("tomorrow", "imorgon", "morgen", "demain", "mañana")
    private val todayWords = listOf("today", "idag", "heute", "aujourd'hui", "hoy")

    fun isAppointmentText(text: String): Boolean {
        val lower = text.lowercase()
        return allKeywords.any { lower.contains(it) } && extractDate(text) != null
    }

    fun parse(text: String, source: String = ""): ParsedAppointment? {
        if (!isAppointmentText(text)) return null

        val date = extractDate(text) ?: return null
        val time = extractTime(text)
        val location = extractLocation(text)
        val title = extractTitle(text, source)

        return ParsedAppointment(title = title, date = date, time = time, location = location)
    }

    private fun extractDate(text: String): String? {
        for (pattern in datePatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val raw = matcher.group(1) ?: continue
                return normalizeDate(raw)
            }
        }
        // Check for "tomorrow", "idag", "imorgon" etc in multiple languages
        val lower = text.lowercase()
        val today = LocalDate.now()
        return when {
            tomorrowWords.any { lower.contains(it) } ->
                today.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
            todayWords.any { lower.contains(it) } ->
                today.format(DateTimeFormatter.ISO_LOCAL_DATE)
            else -> null
        }
    }

    private fun extractTime(text: String): String {
        val matcher = timePattern.matcher(text)
        return if (matcher.find()) matcher.group(1)?.replace(".", ":") ?: "" else ""
    }

    private fun extractLocation(text: String): String {
        val lower = text.lowercase()
        for (kw in locationKeywords) {
            val idx = lower.indexOf(kw)
            if (idx >= 0) {
                val after = text.substring(idx + kw.length).trim()
                val end = after.indexOfFirst { it == '\n' || it == '.' || it == ',' }
                return if (end > 0) after.substring(0, end).trim() else after.take(50).trim()
            }
        }
        return ""
    }

    private fun extractTitle(text: String, source: String): String {
        // Use first line or subject-like content
        val firstLine = text.lines().firstOrNull { it.trim().length > 3 }?.trim() ?: "Appointment"
        return if (firstLine.length > 60) firstLine.take(60) + "..." else firstLine
    }

    private fun normalizeDate(raw: String): String? {
        return try {
            when {
                raw.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> raw
                raw.matches(Regex("\\d{1,2}[/.]\\d{1,2}[/.]\\d{4}")) -> {
                    val parts = raw.split("/", ".")
                    "${parts[2]}-${parts[1].padStart(2,'0')}-${parts[0].padStart(2,'0')}"
                }
                raw.matches(Regex("\\d{1,2}[/.]\\d{1,2}[/.]\\d{2}")) -> {
                    val parts = raw.split("/", ".")
                    "20${parts[2]}-${parts[1].padStart(2,'0')}-${parts[0].padStart(2,'0')}"
                }
                else -> {
                    // Month name format
                    val months = mapOf("jan" to "01","feb" to "02","mar" to "03","apr" to "04",
                        "may" to "05","maj" to "05","jun" to "06","jul" to "07","aug" to "08",
                        "sep" to "09","oct" to "10","okt" to "10","nov" to "11","dec" to "12")
                    val parts = raw.lowercase().split("\\s+".toRegex())
                    if (parts.size >= 3) {
                        val day = parts[0].padStart(2, '0')
                        val month = months.entries.find { parts[1].startsWith(it.key) }?.value ?: return null
                        val year = parts[2]
                        "$year-$month-$day"
                    } else null
                }
            }
        } catch (e: Exception) { null }
    }
}
