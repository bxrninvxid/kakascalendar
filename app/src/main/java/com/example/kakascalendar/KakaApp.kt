package com.example.kakascalendar

import android.app.Application
import com.example.kakascalendar.data.AppDb
import com.example.kakascalendar.data.PrefsRepo
import com.example.kakascalendar.social.SocialRepo
import com.example.kakascalendar.system.Reminders

/** Tiny manual DI container. */
class KakaApp : Application() {
    val db by lazy { AppDb.get(this) }
    val prefs by lazy { PrefsRepo(this) }
    val social by lazy { SocialRepo(prefs) }

    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
    }
}
