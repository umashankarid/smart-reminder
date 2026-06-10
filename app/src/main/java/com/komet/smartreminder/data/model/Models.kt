package com.komet.smartreminder.data.model

import androidx.room.*

@Entity(tableName = "appointments")
data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val date: String, // yyyy-MM-dd
    val time: String = "", // HH:mm
    val location: String = "",
    val source: String, // "sms" or "email"
    val sourceDetail: String = "", // sender/subject
    val rawText: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val dismissed: Boolean = false
)

@Entity(tableName = "settings")
data class UserSettings(
    @PrimaryKey val id: Int = 1,
    val reminderHour: Int = 20, // 8 PM default
    val reminderMinute: Int = 0,
    val scanEmails: Boolean = true,
    val scanSms: Boolean = true
)
