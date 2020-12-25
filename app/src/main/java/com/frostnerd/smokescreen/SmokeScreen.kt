package com.frostnerd.smokescreen

import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.frostnerd.dnstunnelproxy.AddressCreator
import com.frostnerd.encrypteddnstunnelproxy.AbstractHttpsDNSHandle
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.quic.AbstractQuicDnsHandle
import com.frostnerd.smokescreen.activity.ErrorDialogActivity
import com.frostnerd.smokescreen.activity.LoggingDialogActivity
import com.frostnerd.smokescreen.activity.PinActivity
import com.frostnerd.smokescreen.database.AppDatabase
import com.frostnerd.smokescreen.util.Notifications
import com.frostnerd.smokescreen.util.RequestCodes
import com.frostnerd.smokescreen.util.crashhelpers.DataSavingSentryEventProcessor
import com.frostnerd.smokescreen.util.preferences.AppSettings
import com.frostnerd.smokescreen.util.preferences.Crashreporting
import io.sentry.Integration
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.android.core.*
import io.sentry.protocol.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import leakcanary.LeakCanary
import org.minidns.dnsmessage.DnsMessage
import org.minidns.dnsmessage.Question
import org.minidns.record.A
import org.minidns.record.AAAA
import org.minidns.record.Record
import java.net.InetAddress
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
    companion object {
        var sentryReady:Boolean = false
            private set

        private fun setFallbackDns(to: HttpsDnsServerInformation?, context: Context) {
            if (to == null) {
                context.log("Using no fallback DNS server")
                AddressCreator.globalResolve = AddressCreator.defaultResolver
            }
            else {
                context.log("Using fallback server: $to")
                val configs = to.serverConfigurations.values
                AddressCreator.globalResolve = {
                    val responsesIpv4 = configs.random().query(Question(it, Record.TYPE.A))?.takeIf {
                        it.responseCode == DnsMessage.RESPONSE_CODE.NO_ERROR
                    }?.answerSection?.map {
                        it.payload as A
                    }?.map {
                        it.inetAddress
                    }
                    val responsesIpv6 = configs.random().query(Question(it, Record.TYPE.AAAA))?.takeIf {
                        it.responseCode == DnsMessage.RESPONSE_CODE.NO_ERROR
                    }?.answerSection?.map {
                        it.payload as AAAA
                    }?.map {
                        it.inetAddress
                    }

                    val responses = if(responsesIpv4 != null) {
                        if(responsesIpv6 != null) responsesIpv4 + responsesIpv6
                        else responsesIpv4
                    } else responsesIpv6

                    responses?.takeIf { it.isNotEmpty() }?.toTypedArray() ?: error("")
                }
            }
        }
    }
    private var defaultUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null
    val customUncaughtExceptionHandler = EnrichableUncaughtExceptionHandler()
    private fun showCrashNotification() {
        val notification =
            NotificationCompat.Builder(this, Notifications.getHighPriorityChannelId(this))
                .setSmallIcon(R.drawable.ic_cloud_warn)
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
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
        initSentry()
        defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(customUncaughtExceptionHandler)
        super.onCreate()
        log("Application created.")
        handleFallbackDns()
        loadKnownDNSServers()
        AbstractQuicDnsHandle.installProvider(this, {})
    }

    private fun handleFallbackDns() {
        val preferences = getPreferences()
        var runWithoutVPN = preferences.runWithoutVpn
        var fallback = preferences.fallbackDns as HttpsDnsServerInformation?
        preferences.listenForChanges(setOf("fallback_dns_server", "run_without_vpn"), preferences.preferenceChangeListener { changes ->
            var newFallback = if("fallback_dns_server" in changes) changes["fallback_dns_server"]?.second as HttpsDnsServerInformation? else fallback
            val newRunWithoutVPN = changes["run_without_vpn"]?.second as Boolean? ?: runWithoutVPN
            runWithoutVPN = newRunWithoutVPN
            fallback = newFallback

            if(runWithoutVPN && newFallback == null) newFallback = AbstractHttpsDNSHandle.waitUntilKnownServersArePopulated {
                it[0] // The IDs are stable and won't change. 0 == Cloudflare
            }
            setFallbackDns(newFallback, this@SmokeScreen)
        })

        if(preferences.runWithoutVpn && fallback == null) fallback = AbstractHttpsDNSHandle.waitUntilKnownServersArePopulated {
            it[0] // The IDs are stable and won't change. 0 == Cloudflare
        }
        setFallbackDns(fallback, this)
    }

    fun closeSentry() {
        Sentry.close()
        sentryReady = false
    }

    fun initSentry(forceStatus: Status = Status.NONE) {
        if (!BuildConfig.DEBUG && BuildConfig.SENTRY_DSN != "dummy") {
            log("Sentry will be initialized.")
            GlobalScope.launch(Dispatchers.IO) {
                log("Initializing Sentry.")
                sentryReady = false
                try {
                    val hostName = InetAddress.getLocalHost().hostName
                    if(!hostName.startsWith("mars-sandbox", true)) {
                        val enabledType = getPreferences().crashreportingType
                        if (forceStatus != Status.DATASAVING && (enabledType == Crashreporting.FULL || forceStatus == Status.ENABLED)) {
                            // Enable Sentry in full mode
                            // This passes some device-related data, but nothing which allows user actions to be tracked across the app
                            // Info: Some data is attached by the AndroidEventBuilderHelper class, which is present by default

                            SentryAndroid.init(this@SmokeScreen) {
                                it.dsn = BuildConfig.SENTRY_DSN
                            }
                            Sentry.setUser(User().apply {
                                this.username = getPreferences().crashReportingUUID
                            })
                            Sentry.setTag("user.language", Locale.getDefault().displayLanguage)
                            Sentry.setTag(
                                "app.database_version",
                                AppDatabase.currentVersion.toString()
                            )
                            Sentry.setTag(
                                "app.dns_server_name",
                                getPreferences().dnsServerConfig.name
                            )
                            Sentry.setTag(
                                "app.dns_server_primary",
                                getPreferences().dnsServerConfig.servers[0].address.formatToString()
                            )
                            Sentry.setTag(
                                "app.dns_server_secondary",
                                getPreferences().dnsServerConfig.servers.getOrNull(1)?.address?.formatToString()
                                    ?: ""
                            )
                            Sentry.setTag(
                                "app.installer_package",
                                packageManager.getInstallerPackageName(packageName) ?: ""
                            )
                            Sentry.setTag("richdata", "true")
                            Sentry.setTag("app.fromCi", BuildConfig.FROM_CI.toString())
                            Sentry.setTag("app.commit", BuildConfig.COMMIT_HASH)
                            sentryReady = true
                        } else if (enabledType == Crashreporting.MINIMAL || forceStatus == Status.DATASAVING) {
                            // Inits Sentry in datasaving mode
                            // Only data absolutely necessary is transmitted (Android version, app version).
                            // Only crashes will be reported, no regular events.
                            SentryAndroid.init(this@SmokeScreen) {
                                it.dsn = BuildConfig.SENTRY_DSN
                                setupSentryForDatasaving(it)
                            }
                            Sentry.setUser(User().apply {
                                this.username = "anon-" + BuildConfig.VERSION_CODE
                            })
                            Sentry.setTag("richdata", "false")
                            Sentry.setTag("dist", BuildConfig.VERSION_CODE.toString())
                            Sentry.setTag("app.commit", BuildConfig.COMMIT_HASH)
                            Sentry.setTag("app.fromCi", BuildConfig.FROM_CI.toString())
                            sentryReady = true
                        }
                    }
                } catch(ex:Throwable) {
                    ex.printStackTrace()
                }
                log("Sentry ready.")
            }
        }
    }

    private fun setupSentryForDatasaving(sentryOptions: SentryOptions) {
        val remove = mutableListOf<Integration>()
        sentryOptions.integrations.forEach {
            if (it is PhoneStateBreadcrumbsIntegration ||
                it is SystemEventsBreadcrumbsIntegration ||
                it is TempSensorBreadcrumbsIntegration ||
                it is AppComponentsBreadcrumbsIntegration ||
                it is SystemEventsBreadcrumbsIntegration ||
                it is AppLifecycleIntegration
            ) remove.add(it)
        }
        remove.forEach { sentryOptions.integrations.remove(it) }
        sentryOptions.eventProcessors.add(DataSavingSentryEventProcessor())
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
            log("Caught an uncaught exception.")
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