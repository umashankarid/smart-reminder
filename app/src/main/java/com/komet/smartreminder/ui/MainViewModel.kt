package com.komet.smartreminder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.komet.smartreminder.data.db.AppDatabase
import com.komet.smartreminder.data.model.Appointment
import com.komet.smartreminder.data.model.UserSettings
import com.komet.smartreminder.email.GmailScanner
import com.komet.smartreminder.reminder.ReminderScheduler
import com.komet.smartreminder.sms.SmsScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    val appointments = db.appointmentDao().getAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedDate = MutableStateFlow(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
    val selectedDate = _selectedDate.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = db.settingsDao().get() ?: UserSettings()
            ReminderScheduler.scheduleDailyReminder(app, settings.reminderHour, settings.reminderMinute)
        }
    }

    fun selectDate(date: String) { _selectedDate.value = date }

    fun onPermissionsGranted() {
        viewModelScope.launch(Dispatchers.IO) { SmsScanner.scanExistingSms(getApplication()) }
    }

    fun scanEmails() {
        viewModelScope.launch(Dispatchers.IO) { GmailScanner.scanEmails(getApplication()) }
    }

    fun scanAll() {
        viewModelScope.launch(Dispatchers.IO) {
            SmsScanner.scanExistingSms(getApplication())
            GmailScanner.scanEmails(getApplication())
        }
    }

    fun dismiss(apt: Appointment) {
        viewModelScope.launch(Dispatchers.IO) { db.appointmentDao().dismiss(apt.id) }
    }

    fun updateReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            db.settingsDao().save(UserSettings(reminderHour = hour, reminderMinute = minute))
            ReminderScheduler.scheduleDailyReminder(getApplication(), hour, minute)
        }
    }
}
