package com.komet.smartreminder.data.db

import android.content.Context
import androidx.room.*
import com.komet.smartreminder.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments WHERE dismissed = 0 ORDER BY date, time")
    fun getAll(): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE date = :date AND dismissed = 0 ORDER BY time")
    fun getByDate(date: String): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE date = :date AND dismissed = 0")
    suspend fun getByDateSync(date: String): List<Appointment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(appointment: Appointment): Long

    @Query("UPDATE appointments SET dismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: Long)

    @Delete
    suspend fun delete(appointment: Appointment)

    @Query("SELECT * FROM appointments WHERE rawText = :rawText AND date = :date LIMIT 1")
    suspend fun findDuplicate(rawText: String, date: String): Appointment?
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): UserSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(settings: UserSettings)
}

@Database(entities = [Appointment::class, UserSettings::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appointmentDao(): AppointmentDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "smart_reminder.db")
                    .build().also { INSTANCE = it }
            }
        }
    }
}
