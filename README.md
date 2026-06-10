# Smart Reminder - Auto Appointment Extractor

An Android app that automatically reads SMS and Gmail for appointments/bookings and creates reminders.

## Features

- **📱 SMS Scanning** — Auto-detects appointments from incoming and existing SMS
- **📧 Gmail Scanning** — Reads emails for bookings/reservations (Google Sign-In)
- **🧠 Smart Parsing** — On-device NLP extracts date, time, location from text
- **📅 Calendar View** — Scrollable date strip showing appointments per day
- **🔔 Daily Reminder** — Notifies at your chosen time about next day's appointments
- **🔒 Biometric Lock** — Fingerprint/PIN required to open
- **🛡️ Privacy First** — All processing on-device, no data sent anywhere except Gmail API (read-only)

## Security & Privacy

- Network restricted to `googleapis.com` only (via network security config)
- No analytics, no tracking, no third-party SDKs
- All appointment data stored locally in encrypted Room DB
- Gmail access is read-only — app cannot send/modify emails
- SMS read permission — app only reads, never sends
- Biometric lock prevents unauthorized access

## Architecture

```
com.komet.smartreminder/
├── parser/AppointmentParser.kt  # On-device text → appointment extraction
├── sms/
│   ├── SmsReceiver.kt          # Real-time new SMS processing
│   └── SmsScanner.kt           # Scan existing SMS inbox
├── email/GmailScanner.kt       # Gmail API read-only scanning
├── reminder/ReminderScheduler.kt # Daily notification worker
├── data/
│   ├── db/AppDatabase.kt       # Room database
│   └── model/Models.kt         # Appointment & Settings entities
└── ui/
    ├── MainActivity.kt          # Compose UI with calendar
    └── MainViewModel.kt         # State management
```

## Supported Languages

Appointment detection works in English and Swedish (easily extensible).

## Setup

1. Create Google Cloud project with Gmail API enabled
2. Add OAuth credentials for Android
3. Open in Android Studio, run on device

## Permissions Required

| Permission | Why |
|-----------|-----|
| READ_SMS | Scan SMS for appointments |
| RECEIVE_SMS | Detect new appointments in real-time |
| INTERNET | Gmail API only |
| POST_NOTIFICATIONS | Daily reminders |
| USE_BIOMETRIC | App lock |
