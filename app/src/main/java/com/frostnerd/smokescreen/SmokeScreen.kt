package com.frostnerd.smokescreen

import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.frostnerd.smokescreen.activity.ErrorDialogActivity
import com.frostnerd.smokescreen.activity.LoggingDialogActivity
import com.frostnerd.smokescreen.activity.PinActivity
import com.frostnerd.smokescreen.database.AppDatabase
import com.frostnerd.smokescreen.util.Notifications
import com.frostnerd.smokescreen.util.RequestCodes
import com.frostnerd.smokescreen.util.crashhelpers.DatasavingSentryEventHelper
import com.frostnerd.smokescreen.util.preferences.AppSettings
import com.frostnerd.smokescreen.util.preferences.Crashreporting
import io.sentry.Sentry
import io.sentry.android.AndroidSentryClientFactory
import io.sentry.event.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import leakcanary.LeakSentry
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
    val customUncaughtExceptionHandler = EnrichableUncaughtExceptionHandler()
    private fun showCrashNotification() {
        val notification =
            NotificationCompat.Builder(this, Notifications.noConnectionNotificationChannelId(this))
                .setSmallIcon(R.drawable.ic_cloud_warn)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this, RequestCodes.CRASH_NOTIFICATION,
                        PinActivity.openAppIntent(this), PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
                .setContentTitle(getString(R.string.notification_appcrash_title))
        if (getPreferences().loggingEnabled) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                RequestCodes.CRASH_NOTIFICATION_SEND_LOGS,
                Intent(
                    this,
                    LoggingDialogActivity::class.java
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_CANCEL_CURRENT
            )
            notification.addAction(
                R.drawable.ic_share,
                getString(R.string.title_send_logs),
                pendingIntent
            )
        }

        notification.setStyle(NotificationCompat.BigTextStyle(notification).bigText(getString(R.string.notification_appcrash_message)))
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
            Notifications.ID_APP_CRASH,
            notification.build()
        )
    }

    override fun onCreate() {
        if(!BuildConfig.LEAK_DETECTION) LeakSentry.config = LeakSentry.config.copy(enabled = false)
        initSentry()
        defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(customUncaughtExceptionHandler)
        super.onCreate()
        log("Application created.")
        LeakSentry.watchIfEnabled(this)
    }

    fun initSentry(forceStatus: Status = Status.NONE) {
        if (!BuildConfig.DEBUG && BuildConfig.SENTRY_DSN != "dummy") {
            GlobalScope.launch(Dispatchers.IO) {
                val enabledType = getPreferences().crashreportingType
                if (forceStatus != Status.DATASAVING && (enabledType == Crashreporting.FULL || forceStatus == Status.ENABLED)) {
                    // Enable Sentry in full mode
                    // This passes some device-related data, but nothing which allows user actions to be tracked across the app
                    // Info: Some data is attached by the AndroidEventBuilderHelper class, which is present by default

                    Sentry.init(
                        BuildConfig.SENTRY_DSN,
                        AndroidSentryClientFactory(this@SmokeScreen)
                    )
                    Sentry.getContext().user =
                        User(getPreferences().crashReportingUUID, null, null, null)
                    Sentry.getStoredClient().apply {
                        addTag("user.language", Locale.getDefault().displayLanguage)
                        addTag("app.database_version", AppDatabase.currentVersion.toString())
                        addTag("app.dns_server_name", getPreferences().dnsServerConfig.name)
                        addTag(
                            "app.dns_server_primary",
                            getPreferences().dnsServerConfig.servers[0].address.formatToString()
                        )
                        addTag(
                            "app.dns_server_secondary",
                            getPreferences().dnsServerConfig.servers.getOrNull(1)?.address?.formatToString()
                        )
                        addTag(
                            "app.installer_package",
                            packageManager.getInstallerPackageName(packageName)
                        )
                        addTag("richdata", "true")
                        addTag("app.fromCi", BuildConfig.FROM_CI.toString())
                        addTag("app.commit", BuildConfig.COMMIT_HASH)
                    }
                } else if (enabledType == Crashreporting.MINIMAL || forceStatus == Status.DATASAVING) {
                    // Inits Sentry in datasaving mode
                    // Only data absolutely necessary is transmitted (Android version, app version).
                    // Only crashes will be reported, no regular events.
                    Sentry.init(
                        BuildConfig.SENTRY_DSN,
                        AndroidSentryClientFactory(this@SmokeScreen)
                    )
                    Sentry.getContext().user =
                        User("anon-" + BuildConfig.VERSION_CODE, null, null, null)
                    Sentry.getStoredClient().apply {
                        addTag("richdata", "false")
                        addTag("dist", BuildConfig.VERSION_CODE.toString())
                        addTag("app.commit", BuildConfig.COMMIT_HASH)
                        addTag("app.fromCi", BuildConfig.FROM_CI.toString())
                        addExtra("dist", BuildConfig.VERSION_CODE)
                        this.builderHelpers.forEach {
                            this.removeBuilderHelper(it)
                        }
                        this.addBuilderHelper(DatasavingSentryEventHelper())
                    }
                }
            }
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

    inner class EnrichableUncaughtExceptionHandler : Thread.UncaughtExceptionHandler {
        private val extras = mutableMapOf<String, String>()

        override fun uncaughtException(t: Thread, e: Throwable) {
            e.printStackTrace()
            log(e, extras)
            extras.clear()
            val isPrerelease = !AppSettings.isReleaseVersion
            if (isPrerelease && getPreferences().loggingEnabled && getPreferences().crashreportingType == Crashreporting.OFF) {
                startActivity(
                    Intent(
                        this@SmokeScreen,
                        ErrorDialogActivity::class.java
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } else if (isPrerelease && getPreferences().crashreportingType != Crashreporting.OFF) {
                showCrashNotification()
            }
            closeLogger()
            defaultUncaughtExceptionHandler?.uncaughtException(t, e)
            exitProcess(0)
        }

        fun addExtra(key: String, value: String) {
            extras[key] = value
        }

    }
}

enum class Status {
    ENABLED, DATASAVING, NONE
}