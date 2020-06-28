package com.frostnerd.smokescreen.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.content.pm.PackageManager
import android.net.*
import android.os.*
import android.system.OsConstants
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.frostnerd.dnstunnelproxy.*
import com.frostnerd.dnstunnelproxy.QueryListener
import com.frostnerd.encrypteddnstunnelproxy.AbstractHttpsDNSHandle
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.ServerConfiguration
import com.frostnerd.encrypteddnstunnelproxy.tls.AbstractTLSDnsHandle
import com.frostnerd.encrypteddnstunnelproxy.tls.TLSUpstreamAddress
import com.frostnerd.general.CombinedIterator
import com.frostnerd.general.service.isServiceRunning
import com.frostnerd.preferenceskt.typedpreferences.TypedPreferences
import com.frostnerd.smokescreen.*
import com.frostnerd.smokescreen.BuildConfig
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.activity.BackgroundVpnConfigureActivity
import com.frostnerd.smokescreen.activity.PinActivity
import com.frostnerd.smokescreen.activity.PinType
import com.frostnerd.smokescreen.database.entities.CachedResponse
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.util.DeepActionState
import com.frostnerd.smokescreen.util.Notifications
import com.frostnerd.smokescreen.util.RequestCodes
import com.frostnerd.smokescreen.util.preferences.VpnServiceState
import com.frostnerd.smokescreen.util.proxy.*
import com.frostnerd.vpntunnelproxy.Proxy
import com.frostnerd.vpntunnelproxy.RetryingVPNTunnelProxy
import com.frostnerd.vpntunnelproxy.TrafficStats
import kotlinx.coroutines.*
import leakcanary.LeakSentry
import org.minidns.dnsname.DnsName
import org.minidns.record.Record
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.Serializable
import java.lang.Exception
import java.net.*
import java.util.*
import java.util.concurrent.TimeoutException
import kotlin.coroutines.CoroutineContext
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow


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
class DnsVpnService : VpnService(), Runnable {
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var dnsProxy: DnsPacketProxy? = null
    private var vpnProxy: RetryingVPNTunnelProxy? = null
    private var dnsServerProxy:DnsServerPacketProxy? = null
    private var destroyed = false
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var noConnectionNotificationBuilder: NotificationCompat.Builder
    private var noConnectionNotificationShown = false
    private lateinit var serverConfig: DnsServerConfiguration
    private lateinit var settingsSubscription: TypedPreferences<SharedPreferences>.OnPreferenceChangeListener
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var pauseNotificationAction: NotificationCompat.Action? = null
    private var packageBypassAmount = 0
    private var connectedToANetwork: Boolean? = null
    private var lastScreenOff: Long? = null
    private lateinit var screenStateReceiver: BroadcastReceiver
    private var dnsRuleRefreshReceiver:BroadcastReceiver? = null
    private var simpleNotification = getPreferences().simpleNotification
    private var lastVPNStopTime:Long? = null
    private val coroutineScope:CoroutineContext = SupervisorJob()
    private var queryCount = 0
    private var dnsCache:SimpleDnsCache? = null
    private var localResolver:LocalResolver? = null
    private var runInNonVpnMode:Boolean = getPreferences().runWithoutVpn
    private val addressResolveScope:CoroutineScope by lazy {
        CoroutineScope(newSingleThreadContext("service-resolve-retry"))
    }

    /*
        URLs passed to the Service, which haven't been retrieved from the settings.
        Null if the current servers are from the settings
     */
    private var userServerConfig: DnsServerInformation<*>? = null

    companion object {
        const val BROADCAST_VPN_ACTIVE = BuildConfig.APPLICATION_ID + ".VPN_ACTIVE"
        const val BROADCAST_VPN_INACTIVE = BuildConfig.APPLICATION_ID + ".VPN_INACTIVE"
        const val BROADCAST_VPN_PAUSED = BuildConfig.APPLICATION_ID + ".VPN_PAUSED"
        const val BROADCAST_VPN_RESUMED = BuildConfig.APPLICATION_ID + ".VPN_RESUME"
        const val BROADCAST_DNSRULES_REFRESHED = BuildConfig.APPLICATION_ID + ".DNSRULE_REFRESH"

        var currentTrafficStats: TrafficStats? = null
            private set
        var paused:Boolean = false
            private set

        fun startVpn(context: Context, serverInfo: DnsServerInformation<*>? = null) {
            val intent = Intent(context, DnsVpnService::class.java)
            if (serverInfo != null) {
                BackgroundVpnConfigureActivity.writeServerInfoToIntent(serverInfo, intent)
            }
            context.startForegroundServiceCompat(intent)
        }

        fun restartVpn(context: Context, fetchServersFromSettings: Boolean) {
            if (context.isServiceRunning(DnsVpnService::class.java)) {
                val bundle = Bundle()
                bundle.putBoolean("fetch_servers", fetchServersFromSettings)
                sendCommand(context, Command.RESTART, bundle)
            } else startVpn(context)
        }

        fun invalidateDNSCache(context: Context) {
            if(context.isServiceRunning(DnsVpnService::class.java)) {
                sendCommand(context, Command.INVALIDATE_DNS_CACHE)
            }
        }

        fun restartVpn(context: Context, serverInfo: DnsServerInformation<*>?) {
            if (context.isServiceRunning(DnsVpnService::class.java)) {
                val bundle = Bundle()
                if (serverInfo != null) {
                    BackgroundVpnConfigureActivity.writeServerInfoToIntent(serverInfo, bundle)
                    bundle.putBoolean("fetch_servers", true)
                }
                sendCommand(context, Command.RESTART, bundle)
            } else startVpn(context, serverInfo)
        }

        fun sendCommand(context: Context, command: Command, extras: Bundle? = null) {
            val intent = Intent(context, DnsVpnService::class.java).putExtra("command", command)
            if (extras != null) intent.putExtras(extras)
            context.startService(intent)
        }

        fun commandIntent(context: Context, command: Command, extras: Bundle? = null): Intent {
            val intent = Intent(context, DnsVpnService::class.java).putExtra("command", command)
            if (extras != null) intent.putExtras(extras)
            return intent
        }
    }


    override fun onCreate() {
        super.onCreate()
        AbstractHttpsDNSHandle // Loads the known servers.
        AbstractTLSDnsHandle
        KnownDnsServers
        if (getPreferences().vpnServiceState == VpnServiceState.STARTED &&
            !getPreferences().ignoreServiceKilled &&
            getPreferences().vpnLaunchLastVersion == BuildConfig.VERSION_CODE
        ) { // The app didn't stop properly
            val ignoreIntent = Intent(this, DnsVpnService::class.java).putExtra(
                "command",
                Command.IGNORE_SERVICE_KILLED
            )
            val ignorePendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    this@DnsVpnService,
                    RequestCodes.REQUEST_CODE_IGNORE_SERVICE_KILLED,
                    ignoreIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            } else {
                PendingIntent.getService(
                    this@DnsVpnService,
                    RequestCodes.REQUEST_CODE_IGNORE_SERVICE_KILLED,
                    ignoreIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
            NotificationCompat.Builder(this, Notifications.getDefaultNotificationChannelId(this))
                .apply {
                    setContentTitle(getString(R.string.notification_service_killed_title))
                    setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_service_killed_message)))
                    setSmallIcon(R.drawable.ic_cloud_warn)
                    setAutoCancel(true)
                    setOngoing(false)
                    setContentIntent(
                        DeepActionState.BATTERY_OPTIMIZATION_DIALOG.pendingIntentTo(
                            this@DnsVpnService
                        )
                    )
                    addAction(
                        R.drawable.ic_eye,
                        getString(R.string.notification_service_killed_ignore),
                        ignorePendingIntent
                    )
                }.build().also {
                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
                        Notifications.ID_SERVICE_KILLED,
                        it
                    )
                }
        }
        getPreferences().vpnServiceState = VpnServiceState.STARTED
        getPreferences().vpnLaunchLastVersion = BuildConfig.VERSION_CODE
        LeakSentry.watchIfEnabled(this, "DnsVpnService")
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            log("Encountered an uncaught exception.")
            destroy()
            stopForeground(true)
            stopSelf()

            (application as SmokeScreen).apply {
                (dnsProxy?.queryListener as com.frostnerd.smokescreen.util.proxy.QueryListener?)?.apply {
                    if (lastDnsResponse != null) {
                        customUncaughtExceptionHandler.addExtra(
                            "dns_packet",
                            lastDnsResponse.toString()
                        )
                        customUncaughtExceptionHandler.addExtra(
                            "dns_packet_bytes",
                            lastDnsResponse!!.toArray().joinToString(separator = "") {
                                it.toInt().toUByte().toString(16)
                            })
                    }
                }
            }.customUncaughtExceptionHandler.uncaughtException(t, e)
        }
        log("Service onCreate()")
        createNotification()
        updateServiceTile()
        subscribeToSettings()
        addNetworkChangeListener()
        screenStateReceiver =
            registerReceiver(listOf(Intent.ACTION_SCREEN_OFF, Intent.ACTION_SCREEN_ON)) {
                if (it?.action == Intent.ACTION_SCREEN_OFF) {
                    lastScreenOff = System.currentTimeMillis()
                } else {
                    if (lastScreenOff != null && System.currentTimeMillis() - lastScreenOff!! >= 60000) {
                        if (fileDescriptor != null && getPreferences().restartVpnOnNetworkChange) recreateVpn(false, null)
                    }
                }
            }
        dnsRuleRefreshReceiver = registerLocalReceiver(listOf(BROADCAST_DNSRULES_REFRESHED)) {
            vpnProxy?.apply {
                ((packetProxy as DnsPacketProxy).localResolver)?.apply {
                    (this as DnsRuleResolver).refreshRuleCount()
                }
            }
        }
        log("Service created.")
    }

    private fun addNetworkChangeListener() {
        if(runInNonVpnMode) return
        val mgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network?) {
                super.onLost(network)
                val activeNetwork = mgr.activeNetworkInfo
                val networkInfo = mgr.getNetworkInfo(network)
                log("Network lost: $network, info: $networkInfo, current active network: $activeNetwork")
                handleChange()
            }

            override fun onAvailable(network: Network?) {
                super.onAvailable(network)
                val activeNetwork = mgr.activeNetworkInfo
                val networkInfo = mgr.getNetworkInfo(network)
                log("Network became available: $network, info: $networkInfo, current active network: $activeNetwork")
                handleChange()
            }

            private fun handleChange() {
                if (this@DnsVpnService::serverConfig.isInitialized) serverConfig.forEachAddress { _, upstreamAddress ->
                    upstreamAddress.addressCreator.reset()
                    upstreamAddress.addressCreator.resetListeners()
                    resolveAllServerAddresses()
                }
                if (fileDescriptor != null && getPreferences().restartVpnOnNetworkChange) recreateVpn(false, null)
            }
        }
        val builder = NetworkRequest.Builder()
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        mgr.registerNetworkCallback(builder.build(), networkCallback)
    }

    private fun subscribeToSettings() {
        log("Subscribing to settings for automated restart")
        val relevantSettings = mutableSetOf(
            "ipv4_enabled",
            "ipv6_enabled",
            "force_ipv6",
            "force_ipv4",
            "catch_known_servers",
            "dnscache_enabled",
            "dnscache_maxsize",
            "dnscache_use_default_time",
            "dnscache_custom_time",
            "user_bypass_packages",
            "dnscache_keepacrosslaunches",
            "bypass_searchdomains",
            "user_bypass_blacklist",
            "log_dns_queries",
            "show_notification_on_lockscreen",
            "hide_notification_icon",
            "pause_on_captive_portal",
            "allow_ipv6_traffic",
            "allow_ipv4_traffic",
            "dns_server_config",
            "notification_allow_stop",
            "notification_allow_pause",
            "dns_rules_enabled",
            "simple_notification",
            "pin"
        )
        settingsSubscription = getPreferences().listenForChanges(
            relevantSettings,
            getPreferences().preferenceChangeListener { changes ->
                log("The Preference(s) ${changes.keys} have changed, restarting the VPN.")
                log("Detailed changes: $changes")
                if ("hide_notification_icon" in changes || "hide_notification_icon" in changes || "simple_notification" in changes || "pin" in changes) {
                    simpleNotification = getPreferences().simpleNotification
                    log("Recreating the notification because of the change in preferences")
                    createNotification()
                    setNotificationText()
                } else if ("notification_allow_pause" in changes || "notification_allow_stop" in changes) {
                    log("Recreating the notification because of the change in preferences")
                    createNotification()
                    setNotificationText()
                }
                val reload = "dns_server_config" in changes
                recreateVpn(reload, null)
            })
        log("Subscribed.")
    }

    private fun createNotification() {
        log("Creating notification")
        notificationBuilder = NotificationCompat.Builder(
            this,
            Notifications.servicePersistentNotificationChannel(this)
        )
        if (getPreferences().hideNotificationIcon)
            notificationBuilder.priority = NotificationCompat.PRIORITY_MIN
        if (!getPreferences().showNotificationOnLockscreen)
            notificationBuilder.setVisibility(NotificationCompat.VISIBILITY_SECRET)
        notificationBuilder.setContentTitle(getString(R.string.app_name))
        notificationBuilder.setSmallIcon(R.drawable.ic_mainnotification)
        notificationBuilder.setOngoing(true)
        notificationBuilder.setAutoCancel(false)
        notificationBuilder.setSound(null)
        notificationBuilder.setOnlyAlertOnce(true)
        notificationBuilder.setUsesChronometer(!getPreferences().simpleNotification)
        notificationBuilder.setContentIntent(
            PendingIntent.getActivity(
                this, RequestCodes.MAIN_NOTIFICATION,
                PinActivity.openAppIntent(this), PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        if (getPreferences().allowStopInNotification) {
            val stopPendingIntent =
                PendingIntent.getService(
                    this,
                    RequestCodes.MAIN_NOTIFICATION_STOP,
                    commandIntent(this, Command.STOP),
                    PendingIntent.FLAG_CANCEL_CURRENT
                )
            val stopAction = NotificationCompat.Action(
                R.drawable.ic_stop,
                getString(R.string.all_stop),
                stopPendingIntent
            )
            notificationBuilder.addAction(stopAction)
        }
        if (getPreferences().allowPauseInNotification) {
            val pausePendingIntent =
                PendingIntent.getService(
                    this,
                    RequestCodes.MAIN_NOTIFICATION_PAUSE,
                    commandIntent(this, Command.PAUSE_RESUME),
                    PendingIntent.FLAG_CANCEL_CURRENT
                )
            pauseNotificationAction = NotificationCompat.Action(
                R.drawable.ic_stat_pause,
                getString(R.string.all_pause),
                pausePendingIntent
            )
            notificationBuilder.addAction(pauseNotificationAction)
        }
        updateNotification()
        log("Notification created and posted.")
    }

    private fun updateNotification() {
        if(!simpleNotification) {
            notificationBuilder.setSubText(
                getString(
                    R.string.notification_main_subtext,
                    queryCount
                )
            )
        }
        startForeground(Notifications.ID_VPN_SERVICE, notificationBuilder.build())
    }

    private fun showNoConnectionNotification() {
        if (!getPreferences().showNoConnectionNotification) return
        if (!this::noConnectionNotificationBuilder.isInitialized) {
            noConnectionNotificationBuilder = NotificationCompat.Builder(
                this,
                Notifications.noConnectionNotificationChannelId(this)
            )
            noConnectionNotificationBuilder.priority = NotificationCompat.PRIORITY_HIGH
            noConnectionNotificationBuilder.setOngoing(false)
            noConnectionNotificationBuilder.setSmallIcon(R.drawable.ic_cloud_strikethrough)
            noConnectionNotificationBuilder.setContentTitle(getString(R.string.notification_noconnection_title))
            noConnectionNotificationBuilder.setContentText(getString(R.string.notification_noconnection_text))
            noConnectionNotificationBuilder.setStyle(
                NotificationCompat.BigTextStyle(
                    notificationBuilder
                ).bigText(getString(R.string.notification_noconnection_text))
            )
        }
        noConnectionNotificationBuilder.setWhen(System.currentTimeMillis())
        if (!noConnectionNotificationShown) {
            try {
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
                    Notifications.ID_NO_CONNECTION,
                    noConnectionNotificationBuilder.build()
                )
            } catch (ex:java.lang.NullPointerException) {
                // AIDL bug causing NPE in  android.app.ApplicationPackageManager.getUserIfProfile(ApplicationPackageManager.java:2813)
                // Ignore it.
            }
        }
        noConnectionNotificationShown = true
    }

    private fun hideNoConnectionNotification() {
        if (noConnectionNotificationShown) (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(
            Notifications.ID_NO_CONNECTION
        )
        noConnectionNotificationShown = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("Service onStartCommand", intent = intent)
        runInNonVpnMode = getPreferences().runWithoutVpn
        if (intent != null && intent.hasExtra("command")) {
            when (intent.getSerializableExtra("command") as Command) {
                Command.STOP -> {
                    log("Received STOP command.")
                    if (PinActivity.shouldValidatePin(this, intent)) {
                        log("The pin has to be validated before actually stopping.")
                        PinActivity.askForPin(this, PinType.STOP_SERVICE)
                    } else {
                        log("No need to ask for pin, stopping.")
                        destroy()
                        stopForeground(true)
                        stopSelf()
                    }
                }
                Command.RESTART -> {
                    log("Received RESTART command, restarting vpn.")
                    recreateVpn(intent.getBooleanExtra("fetch_servers", false), intent)
                    setNotificationText()
                }
                Command.PAUSE_RESUME -> {
                    if (vpnProxy != null) {
                        log("Received RESUME command while app running, destroying vpn.")
                        destroy(false)
                        pauseNotificationAction?.title = getString(R.string.all_resume)
                        pauseNotificationAction?.icon = R.drawable.ic_stat_resume
                        notificationBuilder.setSmallIcon(R.drawable.ic_notification_paused)
                        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(
                            BROADCAST_VPN_PAUSED))
                        paused = true
                    } else {
                        log("Received RESUME command while app paused, restarting vpn.")
                        recreateVpn(false, null)
                        pauseNotificationAction?.title = getString(R.string.all_pause)
                        pauseNotificationAction?.icon = R.drawable.ic_stat_pause
                        notificationBuilder.setSmallIcon(R.drawable.ic_mainnotification)
                        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(
                            BROADCAST_VPN_RESUMED))
                        paused = false
                    }
                    updateNotification()
                }
                Command.IGNORE_SERVICE_KILLED -> {
                    getPreferences().ignoreServiceKilled = true
                    updateNotification()
                }
                Command.INVALIDATE_DNS_CACHE -> {
                    dnsProxy?.dnsCache?.clear()
                    restartVpn(this, false)
                }
            }
        } else {
            log("No command passed, fetching servers and establishing connection if needed")
            log("Checking whether The VPN is prepared")
            if (!runInNonVpnMode && prepare(this) != null) {
                log("The VPN isn't prepared, stopping self and starting Background configure")
                updateNotification()
                stopForeground(true)
                destroy()
                stopSelf()
                BackgroundVpnConfigureActivity.prepareVpn(
                    this,
                    userServerConfig
                )
            } else {
                log("The VPN is prepared, proceeding.")
                if (!destroyed) {
                    if (!this::serverConfig.isInitialized) {
                        setServerConfiguration(intent)
                        setNotificationText()
                    }
                    updateNotification()
                    establishVpn()
                }
            }
        }
        return if (destroyed) Service.START_NOT_STICKY else Service.START_STICKY
    }

    override fun onTrimMemory(level: Int) {
        if(level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
                level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            dnsCache?.clear()
            localResolver?.cleanup()
        }
    }

    private fun setServerConfiguration(intent: Intent?) {
        log("Updating server configuration..")
        userServerConfig = BackgroundVpnConfigureActivity.readServerInfoFromIntent(intent)
        serverConfig = getServerConfig()
        resolveAllServerAddresses()
        log("Server configuration updated to $serverConfig")
    }

    private fun resolveAllServerAddresses() {
        val initialBackoffTime = 200
        var tries = 0.toDouble()
        var totalTries = 0
        addressResolveScope.cancel()
        serverConfig.forEachAddress { _, address ->
            val listener = address.addressCreator.whenResolveFinished { resolveException, resolveResult ->
                if(resolveException != null) {
                    showNoConnectionNotification()
                    if (resolveException is TimeoutException || resolveException is UnknownHostException) {
                        log("Address resolve failed: $resolveException. Total tries $totalTries/70")
                        if (totalTries <= 70) {
                            addressResolveScope.launch {
                                val exponentialBackoff =
                                    (initialBackoffTime * 2.toDouble().pow(tries++)).toLong()
                                delay(min(45000L, exponentialBackoff))
                                totalTries++
                                if (tries >= 9) tries = 0.toDouble()
                                if(isActive) address.addressCreator.resolveOrGetResultOrNull(true)
                            }
                            true
                        } else false
                    } else {
                        log("Address resolve failed: $resolveException. Not retrying.")
                        false
                    }
                } else if(resolveResult != null){
                    hideNoConnectionNotification()
                    false
                } else {
                    false
                }
            }
            if (!address.addressCreator.isCurrentlyResolving()) {
                addressResolveScope.launch {
                    if(!address.addressCreator.isCurrentlyResolving() && !address.addressCreator.resolveOrGetResultOrNull(
                            true
                        ).isNullOrEmpty()) address.addressCreator.removeListener(listener)
                }
            }
        }
    }

    private fun setNotificationText() {
        if (this::serverConfig.isInitialized) {
            val primaryServer: String
            val secondaryServer: String?
            if (serverConfig.httpsConfiguration != null) {
                notificationBuilder.setContentTitle(getString(R.string.notification_main_title_https))
                primaryServer = serverConfig.httpsConfiguration!![0].urlCreator.address.getUrl(true)
                secondaryServer =
                    serverConfig.httpsConfiguration!!.getOrNull(1)
                        ?.urlCreator?.address?.getUrl(true)
            } else {
                notificationBuilder.setContentTitle(getString(R.string.notification_main_title_tls))
                primaryServer = serverConfig.tlsConfiguration!![0].formatToString()
                secondaryServer = serverConfig.tlsConfiguration!!.getOrNull(1)?.formatToString()
            }
            val text =
                when {
                    getPreferences().simpleNotification -> getString(
                        R.string.notification_simple_text,
                        serverConfig.name
                    )
                    secondaryServer != null -> getString(
                        if (getPreferences().isBypassBlacklist) R.string.notification_main_text_with_secondary else R.string.notification_main_text_with_secondary_whitelist,
                        primaryServer,
                        secondaryServer,
                        packageBypassAmount,
                        (dnsProxy?.dnsCache as SimpleDnsCache?)?.livingCachedEntries() ?: 0
                    )
                    else -> getString(
                        if (getPreferences().isBypassBlacklist) R.string.notification_main_text else R.string.notification_main_text_whitelist,
                        primaryServer,
                        packageBypassAmount,
                        (dnsProxy?.dnsCache as SimpleDnsCache?)?.livingCachedEntries() ?: 0
                    )
                }
            if (simpleNotification) {
                notificationBuilder.setStyle(null)
                notificationBuilder.setContentText(text)
            } else {
                notificationBuilder.setStyle(
                    NotificationCompat.BigTextStyle(notificationBuilder).bigText(
                        text
                    )
                )
            }
        }
    }

    private fun establishVpn() {
        log("Establishing VPN")
        if (vpnProxy == null) {
            destroyed = false
            val runVpn = {
                if(!runInNonVpnMode) fileDescriptor = createBuilder().establish()
                run()
                setNotificationText()
                updateNotification()
            }
            val timeDiff = lastVPNStopTime?.let { System.currentTimeMillis() - it }
            if(timeDiff != null && timeDiff < 750) {
                GlobalScope.launch(coroutineScope) {
                    delay(750-timeDiff)
                    if(isActive) runVpn()
                }
            } else {
                runVpn()
            }

        } else log("Connection already running, no need to establish.")
    }

    private fun recreateVpn(reloadServerConfiguration: Boolean, intent: Intent?) {
        log("Recreating the VPN (destroying & establishing)")
        destroy(false)
        if (runInNonVpnMode || prepare(this) == null) {
            log("VpnService is still prepared, establishing VPN.")
            destroyed = false
            if (reloadServerConfiguration || !this::serverConfig.isInitialized) {
                log("Re-fetching the servers (from intent or settings)")
                setServerConfiguration(intent)
            } else serverConfig.forEachAddress { _, address ->
                address.addressCreator.reset()
                resolveAllServerAddresses()
            }
            establishVpn()
            setNotificationText()
            updateNotification()
        } else {
            log("VpnService isn't prepared, launching BackgroundVpnConfigureActivity.")
            BackgroundVpnConfigureActivity.prepareVpn(
                this,
                userServerConfig
            )
            log("BackgroundVpnConfigureActivity launched, stopping service.")
            stopForeground(true)
            stopSelf()
        }
    }

    private fun destroy(isStoppingCompletely: Boolean = true) {
        log("Destroying the VPN")
        if (isStoppingCompletely || connectedToANetwork == true) hideNoConnectionNotification()
        if (!destroyed) {
            vpnProxy?.stop()
            dnsServerProxy?.stop()
            fileDescriptor?.close()
            addressResolveScope.cancel()
            lastVPNStopTime = System.currentTimeMillis()
            if (isStoppingCompletely) {
                if (networkCallback != null) {
                    (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(
                        networkCallback
                    )
                    networkCallback = null
                }
                tryUnregisterReceiver(screenStateReceiver)
            }
            vpnProxy = null
            fileDescriptor = null
            destroyed = true
            log("VPN destroyed.")
        } else log("VPN is already destroyed.")
        currentTrafficStats = null
    }

    override fun onDestroy() {
        super.onDestroy()
        log("onDestroy() called (Was destroyed from within: $destroyed)")
        log("Unregistering settings listener")
        getPreferences().unregisterOnChangeListener(settingsSubscription)
        log("Unregistered.")

        if (!destroyed && resources.getBoolean(R.bool.keep_service_alive)) {
            log("The service wasn't destroyed from within and keep_service_alive is true, restarting VPN.")
            val restartIntent = Intent(this, VpnRestartService::class.java)
            if (userServerConfig != null) BackgroundVpnConfigureActivity.writeServerInfoToIntent(
                userServerConfig!!,
                restartIntent
            )
            startForegroundServiceCompat(restartIntent)
        } else {
            destroy()
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(BROADCAST_VPN_INACTIVE))
        }
        updateServiceTile()
        getPreferences().vpnServiceState = VpnServiceState.STOPPED
        log("onDestroy() done.")
    }

    override fun onRevoke() {
        log("onRevoke() called")
        destroy()
        stopForeground(true)
        stopSelf()
        if (getPreferences().disallowOtherVpns) {
            log("Disallow other VPNs is true, restarting in 250ms")
            Handler(Looper.getMainLooper()).postDelayed({
                BackgroundVpnConfigureActivity.prepareVpn(this, userServerConfig)
            }, 250)
        } else if(getPreferences().showNotificationOnRevoked){
            NotificationCompat.Builder(this, Notifications.getHighPriorityChannelId(this)).apply {
                setSmallIcon(R.drawable.ic_cloud_warn)
                setContentTitle(getString(R.string.notification_service_revoked_title))
                setContentText(getString(R.string.notification_service_revoked_message))
                setContentIntent(PendingIntent.getActivity(this@DnsVpnService, RequestCodes.RESTART_AFTER_REVOKE, Intent(this@DnsVpnService, BackgroundVpnConfigureActivity::class.java), PendingIntent.FLAG_CANCEL_CURRENT))
                setAutoCancel(true)
                priority = NotificationCompat.PRIORITY_HIGH
            }.build().also {
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(Notifications.ID_SERVICE_REVOKED, it)
            }
        }
    }

    private fun randomLocalIPv6Address(): String {
        val prefix = StringBuilder(randomIPv6LocalPrefix())
        for (i in 0..4) prefix.append(":").append(randomIPv6Block(16, false))
        return prefix.toString()
    }

    private fun randomIPv6LocalPrefix(): String {
        return "fd" + randomIPv6Block(8, true) + ":" + randomIPv6Block(
            16,
            false
        ) + ":" + randomIPv6Block(16, false)
    }

    private fun randomIPv6Block(bits: Int, leading_zeros: Boolean): String {
        var hex =
            java.lang.Long.toHexString(floor(Math.random() * 2.0.pow(bits.toDouble())).toLong())
        if (!leading_zeros || hex.length == bits / 4) hex =
            "0000".substring(0, bits / 4 - hex.length) + hex
        return hex
    }

    private fun createBuilder(): Builder {
        log("Creating the VpnBuilder.")
        val builder = Builder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val mgr =
                getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork =
                mgr.activeNetwork
            log("Current active network: $activeNetwork")
            if(activeNetwork != null) try {
                mgr.getLinkProperties(activeNetwork)?.domains?.takeIf {
                    it.isNotEmpty()
                }?.split(",")?.forEach {
                    log("Adding search domain '$it' to VPN")
                    builder.addSearchDomain(it)
                }
            } catch (ex:Exception) {
                log("Failure when setting search domains of network: $ex")
            }
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }
        builder.setConfigureIntent(PendingIntent.getActivity(this, RequestCodes.VPN_CONFIGURE, PinActivity.openAppIntent(this), PendingIntent.FLAG_CANCEL_CURRENT))
        val deviceHasIpv6 = hasDeviceIpv6Address()
        val deviceHasIpv4 = hasDeviceIpv4Address()

        val dhcpDnsServer = getDhcpDnsServers()

        val useIpv6 = getPreferences().enableIpv6 && (getPreferences().forceIpv6 || deviceHasIpv6)
        val useIpv4 =
            !useIpv6 || (getPreferences().enableIpv4 && (getPreferences().forceIpv4 || deviceHasIpv4))

        val allowIpv4Traffic = useIpv4 || getPreferences().allowIpv4Traffic
        val allowIpv6Traffic = useIpv6 || getPreferences().allowIpv6Traffic

        log("Using IPv4: $useIpv4 (device has IPv4: $deviceHasIpv4), Ipv4 Traffic allowed: $allowIpv4Traffic")
        log("Using IPv6: $useIpv6 (device has IPv6: $deviceHasIpv6), Ipv6 Traffic allowed: $allowIpv6Traffic")
        log("DHCP Dns servers: $dhcpDnsServer")

        var couldSetAddress = false
        if (useIpv4) {
            for (address in resources.getStringArray(R.array.interface_address_prefixes)) {
                val prefix = address.split("/")[0]
                val mask = address.split("/")[1].toInt()
                try {
                    builder.addAddress("$prefix.134", mask)
                    couldSetAddress = true
                    log("Ipv4-Address set to $prefix.134/$mask")
                    break
                } catch (ignored: IllegalArgumentException) {
                    log("Couldn't set Ipv4-Address $prefix.134/$mask")
                }
            }
            if (!couldSetAddress) {
                log("Couldn't set any IPv4 dynamic address, trying 192.168.0.10...")
                builder.addAddress("192.168.0.10", 24)
            }
        }
        couldSetAddress = false

        var tries = 0
        if (useIpv6) {
            do {
                val addr = randomLocalIPv6Address()
                try {
                    builder.addAddress(addr, 48)
                    couldSetAddress = true
                    log("Ipv6-Address set to $addr")
                    break
                } catch (e: IllegalArgumentException) {
                    if (tries >= 5) throw e
                    log("Couldn't set Ipv6-Address $addr, try $tries")
                }
            } while (!couldSetAddress && ++tries < 5)
        }

        val catchKnownDnsServers = getPreferences().catchKnownDnsServers
        if (catchKnownDnsServers) {
            log("Interception of requests towards known DNS servers is enabled, adding routes.")
            for (server in KnownDnsServers.waitUntilKnownServersArePopulated(-1)!!.values) {
                log("Adding all routes for ${server.name}")
                server.servers.forEach {
                    it.address.addressCreator.whenResolveFinishedSuccessfully { addresses ->
                        addresses.forEach { address ->
                            if (address is Inet6Address && useIpv6) {
                                log("Adding route for Ipv6 $address")
                                builder.addRoute(address, 128)
                            } else if (address is Inet4Address && useIpv4 && address.hostAddress != "1.1.1.1") {
                                log("Adding route for Ipv4 $address")
                                builder.addRoute(address, 32)
                            }
                        }
                        false
                    }
                    if (!it.address.addressCreator.startedResolve && !it.address.addressCreator.isCurrentlyResolving()) it.address.addressCreator.resolveOrGetResultOrNull(
                        retryIfError = true,
                        runResolveNow = true
                    )
                }
            }
        } else log("Not intercepting traffic towards known DNS servers.")
        builder.setSession(getString(R.string.app_name))


        if (useIpv4) {
            serverConfig.getAllDnsIpAddresses(ipv4 = true, ipv6 = false).forEach {
                log("Adding $it as dummy DNS server address")
                builder.addDnsServer(it)
                builder.addRoute(it, 32)
            }
            if(catchKnownDnsServers) dhcpDnsServer.forEach {
                if (it is Inet4Address) {
                    builder.addRoute(it, 32)
                }
            }
        } else if (deviceHasIpv4 && allowIpv4Traffic) builder.allowFamily(OsConstants.AF_INET) // If not allowing no IPv4 connections work anymore.

        if (useIpv6) {
            serverConfig.getAllDnsIpAddresses(ipv4 = false, ipv6 = true).forEach {
                log("Adding $it as dummy DNS server address")
                builder.addDnsServer(it)
                builder.addRoute(it, 128)
            }
            if(catchKnownDnsServers) dhcpDnsServer.forEach {
                if (it is Inet6Address) {
                    builder.addRoute(it, 128)
                }
            }
        } else if (deviceHasIpv6 && allowIpv6Traffic) builder.allowFamily(OsConstants.AF_INET6)
        builder.setBlocking(true)

        log("Applying disallowed packages.")
        val userBypass = getPreferences().userBypassPackages
        val defaultBypass = getPreferences().defaultBypassPackages
        if (getPreferences().isBypassBlacklist || userBypass.size == 0) {
            log("Mode is set to blacklist, bypassing ${userBypass.size + defaultBypass.size} packages.")
            for (defaultBypassPackage in CombinedIterator(
                userBypass.iterator(),
                defaultBypass.iterator()
            )) {
                if (isPackageInstalled(defaultBypassPackage)) { //TODO Check what is faster: catching the exception, or checking ourselves
                    builder.addDisallowedApplication(defaultBypassPackage)
                    packageBypassAmount++
                } else log("Package $defaultBypassPackage not installed, thus not bypassing")
            }
        } else {
            log("Mode is set to whitelist, whitelisting ${userBypass.size} packages.")
            for (pkg in userBypass) {
                if (!defaultBypass.contains(pkg)) {
                    if (isPackageInstalled(pkg)) { //TODO Check what is faster: catching the exception, or checking ourselves
                        builder.addAllowedApplication(pkg)
                        packageBypassAmount++
                    } else log("Package $pkg not installed, thus not bypassing")
                } else log("Not whitelisting $pkg, it is blacklisted by default.")
            }
        }
        return builder
    }

    private fun getDhcpDnsServers(): List<InetAddress> {
        val mgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        for (network in mgr.allNetworks) {
            if (network == null) continue
            val info = try {
                mgr.getNetworkInfo(network)
            } catch (ex:NullPointerException) {
                // Android seems to love to throw NullPointerException with getNetworkInfo() - completely out of our control.
                log("Exception when trying to determine DHCP DNS servers: $ex")
                null
            } ?: continue
            val capabilities = mgr.getNetworkCapabilities(network) ?: continue
            if (info.isConnected && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                val linkProperties = mgr.getLinkProperties(network) ?: continue
                return linkProperties.dnsServers
            }
        }
        return emptyList()
    }

    private fun getServerConfig(): DnsServerConfiguration {
        TLSUpstreamAddress
        return if (userServerConfig != null) {
            userServerConfig!!.let { config ->
                return if (config is HttpsDnsServerInformation) DnsServerConfiguration(
                    config.name,
                    config.serverConfigurations.values.toList(),
                    null
                )
                else {
                    DnsServerConfiguration(config.name, null, config.servers.map {
                        it.address as TLSUpstreamAddress
                    })
                }
            }
        } else {
            val config = getPreferences().dnsServerConfig
            if (config.hasTlsServer()) {
                DnsServerConfiguration(config.name, null, config.servers.map {
                    it.address as TLSUpstreamAddress
                })
            } else {
                DnsServerConfiguration(config.name, (config as HttpsDnsServerInformation).serverConfigurations.map {
                    it.value
                }, null)
            }
        }
    }

    override fun run() {
        log("run() called")
        log("Starting with config: $serverConfig")
        log("Running in non-vpn mode: $runInNonVpnMode")

        log("Creating handle.")
        var defaultHandle: DnsHandle? = null
        val handles = mutableListOf<DnsHandle>()
        val ipv6Enabled = getPreferences().enableIpv6 && (getPreferences().forceIpv6 || hasDeviceIpv6Address())
        val ipv4Enabled = !ipv6Enabled || (getPreferences().enableIpv4 && (getPreferences().forceIpv4 || hasDeviceIpv4Address()))

        serverConfig.httpsConfiguration?.forEach {
            val addresses = serverConfig.getIpAddressesFor(ipv4Enabled, ipv6Enabled, it)
            log("Creating handle for DoH $it with IP-Addresses $addresses")
            val handle = ProxyHttpsHandler(
                addresses,
                listOf(it),
                connectTimeout = 20000,
                queryCountCallback = {
                    if (!simpleNotification) {
                        setNotificationText()
                        queryCount++
                        updateNotification()
                    }
                },
                mapQueryRefusedToHostBlock = getPreferences().mapQueryRefusedToHostBlock
            )
            handle.ipv4Enabled = ipv4Enabled
            handle.ipv6Enabled = ipv6Enabled
            if (defaultHandle == null) defaultHandle = handle
            else handles.add(handle)
        }
        serverConfig.tlsConfiguration?.forEach {
            val addresses = serverConfig.getIpAddressesFor(ipv4Enabled, ipv6Enabled, it)
            log("Creating handle for DoH $it with IP-Addresses $addresses")
            val handle = ProxyTlsHandler(
                addresses,
                listOf(it),
                connectTimeout = 8000,
                queryCountCallback = {
                    if (!simpleNotification) {
                        setNotificationText()
                        queryCount++
                        updateNotification()
                    }
                }, mapQueryRefusedToHostBlock = getPreferences().mapQueryRefusedToHostBlock
            )
            handle.ipv4Enabled = ipv4Enabled
            handle.ipv6Enabled = ipv6Enabled
            if (defaultHandle == null) defaultHandle = handle
            else handles.add(handle)
        }
        log("Creating DNS proxy with ${1 + handles.size} handles")

        dnsCache = createDnsCache()
        localResolver = createLocalResolver()

        log("DnsProxy created, creating VPN proxy")
         if(runInNonVpnMode) {
             log("Running in non-VPN mode, starting async")
             GlobalScope.launch {
                 dnsProxy = NonIPSmokeProxy(
                     defaultHandle!!,
                     handles + createProxyBypassHandlers(),
                     dnsCache,
                     createQueryLogger(),
                     localResolver,
                     InetAddress.getLocalHost(),
                     getPreferences().dnsServerModePort
                 )
                 vpnProxy = RetryingVPNTunnelProxy(dnsProxy!!, socketProtector = object:Proxy.SocketProtector {
                     override fun protectDatagramSocket(socket: DatagramSocket) {}
                     override fun protectSocket(socket: Socket) {}
                     override fun protectSocket(socket: Int) {}
                 }, coroutineScope = CoroutineScope(
                     newFixedThreadPoolContext(2, "proxy-pool")
                 ), logger = VpnLogger(applicationContext))
                 vpnProxy?.maxRetries = 15
                 dnsServerProxy = DnsServerPacketProxy(vpnProxy!!, InetAddress.getLocalHost(), getPreferences().dnsServerModePort)
                 dnsServerProxy!!.startServer()
                 log("Non-VPN proxy started.")
             }
        } else {
             dnsProxy = SmokeProxy(
                 defaultHandle!!,
                 handles + createProxyBypassHandlers(),
                 dnsCache,
                 createQueryLogger(),
                 localResolver
             )
            vpnProxy = RetryingVPNTunnelProxy(dnsProxy!!, vpnService = this, coroutineScope = CoroutineScope(
                newFixedThreadPoolContext(2, "proxy-pool")
            ), logger = VpnLogger(applicationContext))
             vpnProxy?.maxRetries = 15
             log("VPN proxy creating, trying to run...")
             fileDescriptor?.let {
                 vpnProxy?.runProxyWithRetry(it, it)
             } ?: run {
                 recreateVpn(false, null)
                 return
             }
             log("VPN proxy started.")
        }
        currentTrafficStats = vpnProxy?.trafficStats
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(BROADCAST_VPN_ACTIVE))
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(Notifications.ID_SERVICE_REVOKED)
    }

    private fun createQueryLogger(): QueryListener? {
        return if (getPreferences().shouldLogDnsQueriesToConsole() || getPreferences().queryLoggingEnabled) {
            com.frostnerd.smokescreen.util.proxy.QueryListener(applicationContext)
        } else null
    }


    /**
     * Creates bypass handlers for each network and its associated search domains
     * Requests for .*SEARCHDOMAIN won't use doh and are sent to the DNS servers of the network they originated from.
     */
    private fun createProxyBypassHandlers(): MutableList<DnsHandle> {
        val bypassHandlers = mutableListOf<DnsHandle>()
        if (getPreferences().bypassSearchdomains) {
            log("Creating bypass handlers for search domains of connected networks.")
            val mgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            for (network in mgr.allNetworks) {
                if (network == null) continue
                val networkInfo = try {
                    mgr.getNetworkInfo(network)
                } catch (ex:NullPointerException) {
                    // Android seems to love to throw NullPointerException with getNetworkInfo() - completely out of our control.
                    log("Exception when trying to create proxy bypass handlers: $ex")
                    null
                } ?: continue
                if (networkInfo.isConnected && !mgr.isVpnNetwork(network)) {
                    val linkProperties = mgr.getLinkProperties(network) ?: continue
                    if (!linkProperties.domains.isNullOrBlank() && linkProperties.dnsServers.isNotEmpty()) {
                        log("Bypassing domains ${linkProperties.domains} for network of type ${networkInfo.typeName}")
                        val domains = linkProperties.domains.split(",").toList()
                        bypassHandlers.add(
                            ProxyBypassHandler(
                                domains,
                                linkProperties.dnsServers[0]!!
                            )
                        )
                    }
                }
            }
            log("${bypassHandlers.size} bypass handlers created.")
        } else log("Not creating bypass handlers for search domains, bypass is disabled.")
        if (getPreferences().pauseOnCaptivePortal) {
            val dhcpServers = getDhcpDnsServers()
            if (dhcpServers.isNotEmpty()) bypassHandlers.add(
                CaptivePortalUdpDnsHandle(
                    targetDnsServer = { dhcpServers.first() })
            )
        }
        bypassHandlers.add(
            NoConnectionDnsHandle(
                getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager,
                NoConnectionDnsHandle.Behavior.DROP_PACKETS,
                regularConnectionCheckIntervalMs = 60000,
                connectionListener = {
                    log("Connection changed to connected=$it", "NoConnectionDnsHandle-Listener")
                    connectedToANetwork = it
                    if (!it) showNoConnectionNotification()
                    else hideNoConnectionNotification()
                })
        )
        return bypassHandlers
    }

    private fun createDnsCache(): SimpleDnsCache? {
        val dnsCache: SimpleDnsCache?
        dnsCache = if (getPreferences().useDnsCache) {
            log("Creating DNS Cache.")
            val cacheControl: CacheControl = if (!getPreferences().useDefaultDnsCacheTime) {
                NxDomainCacheControl(applicationContext)
            } else DefaultCacheControl(getPreferences().minimumCacheTime.toLong())
            val onClearCache: ((currentCache: Map<String, Map<Record.TYPE, Map<Record<*>, Long>>>) -> Unit)? =
                if (getPreferences().keepDnsCacheAcrossLaunches) {
                    { cache ->
                        log("Persisting current cache to Database.")
                        getDatabase().cachedResponseDao().deleteAll()
                        var persisted = 0
                        val entries = mutableListOf<CachedResponse>()
                        for (entry in cache) {
                            for (cachedType in entry.value) {
                                val recordsToPersist: MutableMap<Record<*>, Long> = mutableMapOf()
                                for (cachedRecord in cachedType.value) {
                                    if (cachedRecord.value > System.currentTimeMillis()) {
                                        recordsToPersist[cachedRecord.key] = cachedRecord.value
                                        persisted++
                                    }
                                }

                                if (recordsToPersist.isNotEmpty()) {
                                    entries.add(
                                        createPersistedCacheEntry(
                                            entry.key,
                                            cachedType.key,
                                            recordsToPersist
                                        )
                                    )
                                }
                            }
                        }
                        GlobalScope.launch {
                            val dao = getDatabase().cachedResponseDao()
                            dao.insertAll(entries)
                        }
                        log("Cache persisted [$persisted records]")
                    }
                } else null
            SimpleDnsCache(
                cacheControl,
                CacheStrategy(getPreferences().maxCacheSize),
                onClearCache = onClearCache
            )
        } else {
            log("Not creating DNS cache, is disabled.")
            null
        }
        if (dnsCache != null) log("Cache created.")

        // Restores persisted cache
        if (dnsCache != null && getPreferences().keepDnsCacheAcrossLaunches) {
            log("Restoring old cache")
            var restored = 0
            var tooOld = 0
            GlobalScope.launch {
                for (cachedResponse in getDatabase().cachedResponseRepository().getAllAsync(
                    GlobalScope
                )) {
                    val records = mutableMapOf<Record<*>, Long>()
                    for (record in cachedResponse.records) {
                        if (record.value > System.currentTimeMillis()) {
                            val bytes = Base64.decode(record.key, Base64.NO_WRAP)
                            val stream = DataInputStream(ByteArrayInputStream(bytes))
                            records[Record.parse(stream, bytes)] = record.value
                            restored++
                        } else tooOld++
                    }
                    dnsCache.addToCache(
                        DnsName.from(cachedResponse.dnsName),
                        cachedResponse.type,
                        records
                    )
                }
                getDatabase().cachedResponseDao().deleteAll()
            }
            log("$restored old records restored, deleting persisted cache. $tooOld records were too old.")
            log("Persisted cache deleted.")
        }
        return dnsCache
    }

    private fun createLocalResolver(): LocalResolver? {
        return if (getPreferences().dnsRulesEnabled) {
            DnsRuleResolver(applicationContext)
        } else {
            null
        }
    }

    private fun createPersistedCacheEntry(
        dnsName: String,
        type: Record.TYPE,
        recordsToPersist: MutableMap<Record<*>, Long>
    ): CachedResponse {
        val entity = CachedResponse(
            dnsName,
            type,
            mutableMapOf()
        )
        for (record in recordsToPersist) {
            entity.records[Base64.encodeToString(record.key.toByteArray(), Base64.NO_WRAP)] =
                record.value
        }
        return entity
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getApplicationInfo(packageName, 0).enabled
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}

enum class Command : Serializable {
    STOP, RESTART, PAUSE_RESUME, IGNORE_SERVICE_KILLED, INVALIDATE_DNS_CACHE
}

data class DnsServerConfiguration(
    val name:String,
    val httpsConfiguration: List<ServerConfiguration>?,
    val tlsConfiguration: List<TLSUpstreamAddress>?
) {

    fun getAllDnsIpAddresses(ipv4:Boolean, ipv6:Boolean):List<String> {
        val ips = mutableListOf<String>()
        httpsConfiguration?.forEach { ips.addAll(getIpAddressesFor(ipv4, ipv6, it)) }
        tlsConfiguration?.forEach { ips.addAll(getIpAddressesFor(ipv4, ipv6, it)) }
        return ips
    }

    fun getIpAddressesFor(ipv4:Boolean, ipv6:Boolean, config:ServerConfiguration):List<String> {
        if(httpsConfiguration.isNullOrEmpty() || config !in httpsConfiguration) return emptyList()
        val index = httpsConfiguration.indexOf(config) + 100
        val list = mutableListOf<String>()
        if(ipv4) list.add("203.0.113." + String.format(Locale.ROOT,"%03d", index))
        if(ipv6) list.add("fd21:c5ea:169d:fff1:3418:d688:36c5:e8" + String.format(Locale.ROOT, "%02x", index))
        return list
    }

    fun getIpAddressesFor(ipv4:Boolean, ipv6:Boolean, address:TLSUpstreamAddress):List<String> {
        if(tlsConfiguration.isNullOrEmpty() || address !in tlsConfiguration) return emptyList()
        val index = tlsConfiguration.indexOf(address) + 1
        val list = mutableListOf<String>()
        if(ipv4) list.add("203.0.113." + String.format(Locale.ROOT, "%03d", index))
        if(ipv6) list.add("fd21:c5ea:169d:fff1:3418:d688:36c5:e8" + String.format(Locale.ROOT, "%02x", index))
        return list
    }

    fun forEachAddress(block: (isHttps: Boolean, UpstreamAddress) -> Unit) {
        httpsConfiguration?.forEach {
            block(true, it.urlCreator.address)
        }
        tlsConfiguration?.forEach {
            block(false, it)
        }
    }
}