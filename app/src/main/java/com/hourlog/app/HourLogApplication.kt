package com.hourlog.app

import android.app.Application
import androidx.room.Room
import com.hourlog.app.data.*
import com.hourlog.app.notifications.ReminderScheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class HourLogApplication : Application() {
    val security by lazy { com.hourlog.app.security.SecurityController(this) }
    val database by lazy { Room.databaseBuilder(this, HourLogDatabase::class.java, "hourlog.db").build() }
    val repository by lazy { HourRepository(database, PreferenceStore(this)) }
    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.createChannel(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            repository.recoverPreferences()
            ReminderScheduler.schedule(this@HourLogApplication, repository.preferences.first(), replace = false)
        }
    }
}
