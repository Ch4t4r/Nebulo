package com.frostnerd.smokescreen

import android.app.Application
import android.content.Intent
import com.frostnerd.smokescreen.activity.ErrorDialogActivity
import com.frostnerd.smokescreen.database.AppDatabase
import io.sentry.Sentry
import io.sentry.android.AndroidSentryClientFactory
import io.sentry.event.User
import java.util.*
import kotlin.system.exitProcess

/*
 * Copyright (C) 2019 Daniel Wolf (Ch4t4r)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * You can contact the developer at daniel.wolf@frostnerd.com.
 */
class SmokeScreen : Application() {
    private var defaultUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null
    val customUncaughtExceptionHandler: Thread.UncaughtExceptionHandler =
        Thread.UncaughtExceptionHandler { t, e ->
            e.printStackTrace()
            log(e)
            val isPrerelease = BuildConfig.VERSION_NAME.contains("alpha",true) || BuildConfig.VERSION_NAME.contains("beta",true)
            if(isPrerelease && getPreferences().loggingEnabled) {
                startActivity(Intent(this, ErrorDialogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            closeLogger()
            defaultUncaughtExceptionHandler?.uncaughtException(t, e)
            exitProcess(0)
        }

    override fun onCreate() {
        initSentry()
        defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(customUncaughtExceptionHandler)
        super.onCreate()
        log("Application created.")
    }

    fun initSentry(forceEnabled:Boolean = false) {
        if(forceEnabled || getPreferences().crashReportingEnabled) {
            Sentry.init("https://fadeddb58abf408db50809922bf064cc@sentry.frostnerd.com:443/2", AndroidSentryClientFactory(this))
            Sentry.getContext().user = User(getPreferences().crashReportingUUID, null, null, null)
            Sentry.getStoredClient().addTag("user.language", Locale.getDefault().displayLanguage)
            Sentry.getStoredClient().addTag("app.database_version", AppDatabase.currentVersion.toString())
            Sentry.getStoredClient().addTag("app.dns_server_name", getPreferences().dnsServerConfig.name)
            Sentry.getStoredClient().addTag("app.dns_server_primary", getPreferences().dnsServerConfig.servers[0].address.formatToString())
            Sentry.getStoredClient().addTag("app.dns_server_secondary", getPreferences().dnsServerConfig.servers.getOrNull(1)?.address?.formatToString())
        } else {
            Sentry.close()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        log("The system seems to have low memory")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        log("Memory has been trimmed with level $level")
    }
}