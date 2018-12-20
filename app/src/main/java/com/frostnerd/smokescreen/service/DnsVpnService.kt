package com.frostnerd.smokescreen.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import com.frostnerd.networking.NetworkUtil
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.frostnerd.dnstunnelproxy.*
import com.frostnerd.encrypteddnstunnelproxy.AbstractHttpsDNSHandle
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.ServerConfiguration
import com.frostnerd.encrypteddnstunnelproxy.createSimpleServerConfig
import com.frostnerd.preferenceskt.typedpreferences.TypedPreferences
import com.frostnerd.smokescreen.*
import com.frostnerd.smokescreen.BuildConfig
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.activity.BackgroundVpnConfigureActivity
import com.frostnerd.smokescreen.activity.MainActivity
import com.frostnerd.smokescreen.util.Notifications
import com.frostnerd.smokescreen.util.proxy.ProxyHandler
import com.frostnerd.smokescreen.util.proxy.SmokeProxy
import com.frostnerd.vpntunnelproxy.TrafficStats
import com.frostnerd.vpntunnelproxy.VPNTunnelProxy
import org.minidns.dnsmessage.Question
import org.minidns.dnsname.DnsName
import org.minidns.record.Record
import java.io.Serializable
import java.lang.IllegalArgumentException
import java.net.Inet4Address
import java.net.Inet6Address


/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */
class DnsVpnService : VpnService(), Runnable {
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var handle: ProxyHandler? = null
    private var dnsProxy: SmokeProxy? = null
    private var vpnProxy: VPNTunnelProxy? = null
    private var destroyed = false
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var primaryServer: ServerConfiguration
    private lateinit var settingsSubscription: TypedPreferences<SharedPreferences>.OnPreferenceChangeListener
    private var secondaryServer: ServerConfiguration? = null
    private var queryCountOffset: Long = 0

    /*
        URLs passed to the Service, which haven't been retrieved from the settings.
        Null if the current servers are from the settings
     */
    private var primaryUserServerUrl: String? = null
    private var secondaryUserServerUrl: String? = null

    companion object {
        const val BROADCAST_VPN_ACTIVE = BuildConfig.APPLICATION_ID + ".VPN_ACTIVE"
        const val BROADCAST_VPN_INACTIVE = BuildConfig.APPLICATION_ID + ".VPN_INACTIVE"
        var currentTrafficStats: TrafficStats? = null
            private set

        fun startVpn(context: Context, primaryServerUrl: String? = null, secondaryServerUrl: String? = null) {
            val intent = Intent(context, DnsVpnService::class.java)
            if (primaryServerUrl != null) intent.putExtra(
                BackgroundVpnConfigureActivity.extraKeyPrimaryUrl,
                primaryServerUrl
            )
            if (secondaryServerUrl != null) intent.putExtra(
                BackgroundVpnConfigureActivity.extraKeySecondaryUrl,
                secondaryServerUrl
            )
            context.startForegroundServiceCompat(intent)
        }

        fun restartVpn(context: Context, fetchServersFromSettings: Boolean) {
            val bundle = Bundle()
            bundle.putBoolean("fetch_servers", fetchServersFromSettings)
            sendCommand(context, Command.RESTART, bundle)
        }

        fun restartVpn(context: Context, primaryServerUrl: String?, secondaryServerUrl: String?) {
            val bundle = Bundle()
            if (primaryServerUrl != null) bundle.putString(
                BackgroundVpnConfigureActivity.extraKeyPrimaryUrl,
                primaryServerUrl
            )
            if (secondaryServerUrl != null) bundle.putString(
                BackgroundVpnConfigureActivity.extraKeySecondaryUrl,
                secondaryServerUrl
            )
            sendCommand(context, Command.RESTART, bundle)
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
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            destroy()
            stopForeground(true)
            stopSelf()
            (application as SmokeScreen).customUncaughtExceptionHandler.uncaughtException(t, e)
        }
        log("Service onCreate()")
        createNotification()
        updateServiceTile()
        subscribeToSettings()
        log("Service created.")
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
            "user_bypass_packages"
        )
        settingsSubscription = getPreferences().listenForChanges(relevantSettings) { key, _, _ ->
            log("The Preference $key has changed, restarting the VPN.")
            recreateVpn(key == "doh_server_url_primary" || key == "doh_server_url_secondary", null)
        }
        log("Subscribed.")
    }

    private fun createNotification() {
        log("Creating notification")
        notificationBuilder = NotificationCompat.Builder(this, Notifications.servicePersistentNotificationChannel(this))
        notificationBuilder.setContentTitle(getString(R.string.app_name))
        notificationBuilder.setSmallIcon(R.mipmap.ic_launcher_round)
        notificationBuilder.setOngoing(true)
        notificationBuilder.setAutoCancel(false)
        notificationBuilder.setSound(null)
        notificationBuilder.setUsesChronometer(true)
        notificationBuilder.setContentIntent(
            PendingIntent.getActivity(
                this, 1,
                Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        val stopPendingIntent =
            PendingIntent.getService(this, 1, commandIntent(this, Command.STOP), PendingIntent.FLAG_CANCEL_CURRENT)
        val stopAction = NotificationCompat.Action(R.drawable.ic_stop, getString(R.string.all_stop), stopPendingIntent)

        notificationBuilder.addAction(stopAction)
        updateNotification(0)
        log("Notification created and posted.")
    }

    private fun updateNotification(queryCount: Int? = null) {
        if (queryCount != null) notificationBuilder.setSubText(
            getString(
                R.string.notification_main_subtext,
                queryCount + queryCountOffset
            )
        )
        startForeground(1, notificationBuilder.build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("Service onStartCommand", intent = intent)
        if (intent != null && intent.hasExtra("command")) {
            val command = intent.getSerializableExtra("command") as Command

            when (command) {
                Command.STOP -> {
                    log("Received STOP command, stopping service.")
                    destroy()
                    stopForeground(true)
                    stopSelf()
                }
                Command.RESTART -> {
                    log("Received RESTART command, restarting vpn.")
                    setNotificationText()
                    recreateVpn(intent.getBooleanExtra("fetch_servers", false), intent)
                }
            }
        } else {
            log("No command passed, fetching servers and establishing connection if needed")
            log("Checking whether The VPN is prepared")
            if (VpnService.prepare(this) != null) {
                log("The VPN isn't prepared, stopping self and starting Background configure")
                updateNotification(0)
                stopForeground(true)
                destroy()
                stopSelf()
                BackgroundVpnConfigureActivity.prepareVpn(
                    this,
                    intent?.extras?.getString(BackgroundVpnConfigureActivity.extraKeyPrimaryUrl),
                    intent?.extras?.getString(BackgroundVpnConfigureActivity.extraKeySecondaryUrl)
                )
            } else {
                log("The VPN is prepared, proceeding.")
                if (!destroyed) {
                    if (!this::primaryServer.isInitialized) {
                        setServerConfiguration(intent)
                        setNotificationText()
                    }
                    updateNotification(0)
                    establishVpn()
                }
            }
        }
        return if (destroyed) Service.START_NOT_STICKY else Service.START_STICKY
    }

    private fun setServerConfiguration(intent: Intent?) {
        log("Updating server configuration..")
        if (intent != null) {
            if (intent.hasExtra(BackgroundVpnConfigureActivity.extraKeyPrimaryUrl)) {
                primaryUserServerUrl = intent.getStringExtra(BackgroundVpnConfigureActivity.extraKeyPrimaryUrl)
                primaryServer = ServerConfiguration.createSimpleServerConfig(primaryUserServerUrl!!)
            } else {
                primaryUserServerUrl = null
                primaryServer = getPreferences().primaryServerConfig
            }

            if (intent.hasExtra(BackgroundVpnConfigureActivity.extraKeySecondaryUrl)) {
                secondaryUserServerUrl = intent.getStringExtra(BackgroundVpnConfigureActivity.extraKeySecondaryUrl)
                secondaryServer = ServerConfiguration.createSimpleServerConfig(secondaryUserServerUrl!!)
            } else {
                secondaryUserServerUrl = null
                secondaryServer = getPreferences().secondaryServerConfig
            }
        } else {
            primaryServer = getPreferences().primaryServerConfig
            secondaryServer = getPreferences().secondaryServerConfig
            primaryUserServerUrl = null
            secondaryUserServerUrl = null
        }
        log("Server configuration updated to $primaryServer and $secondaryServer")
    }

    private fun setNotificationText() {
        val text = if (secondaryServer != null) {
            getString(
                R.string.notification_main_text_with_secondary,
                primaryServer.urlCreator.baseUrl,
                secondaryServer!!.urlCreator.baseUrl,
                getPreferences().totalBypassPackageCount,
                dnsProxy?.cache?.livingCachedEntries() ?: 0
            )
        } else {
            getString(
                R.string.notification_main_text,
                primaryServer.urlCreator.baseUrl,
                getPreferences().totalBypassPackageCount,
                dnsProxy?.cache?.livingCachedEntries() ?: 0
            )
        }
        notificationBuilder.setStyle(NotificationCompat.BigTextStyle(notificationBuilder).bigText(text))
    }

    private fun establishVpn() {
        log("Establishing VPN")
        if (fileDescriptor == null) {
            fileDescriptor = createBuilder().establish()
            run()
        } else log("Connection already running, no need to establish.")
    }

    private fun recreateVpn(reloadServerConfiguration: Boolean, intent: Intent?) {
        log("Recreating the VPN (destroying & establishing)")
        destroy()
        if (VpnService.prepare(this) == null) {
            log("VpnService is still prepared, establishing VPN.")
            destroyed = false
            if (reloadServerConfiguration) {
                log("Re-fetching the servers (from intent or settings)")
                setServerConfiguration(intent)
            }
            establishVpn()
            updateNotification(0)
            setNotificationText()
        } else {
            log("VpnService isn't prepared, launching BackgroundVpnConfigureActivity.")
            BackgroundVpnConfigureActivity.prepareVpn(
                this,
                primaryUserServerUrl,
                secondaryUserServerUrl
            )
            log("BackgroundVpnConfigureActivity launched, stopping service.")
            stopForeground(true)
            stopSelf()
        }
    }

    private fun destroy() {
        log("Destroying the VPN")
        if (!destroyed) {
            queryCountOffset += currentTrafficStats?.packetsReceivedFromDevice ?: 0
            vpnProxy?.stop()
            fileDescriptor?.close()
            vpnProxy = null
            fileDescriptor = null
            destroyed = true
            log("VPN destroyed.")
        } else log("VPN is already destroyed.")
        currentTrafficStats = null
    }

    override fun onDestroy() {
        super.onDestroy()
        log("onDestroy() called (Was destroyed from within: $destroyed")
        log("Unregistering settings listener")
        getPreferences().unregisterOnChangeListener(settingsSubscription)
        log("Unregistered.")

        if (!destroyed && resources.getBoolean(R.bool.keep_service_alive)) {
            log("The service wasn't destroyed from within and keep_service_alive is true, restarting VPN.")
            val restartIntent = Intent(this, VpnRestartService::class.java)
            if (primaryUserServerUrl != null) restartIntent.putExtra(
                BackgroundVpnConfigureActivity.extraKeyPrimaryUrl,
                primaryUserServerUrl
            )
            if (secondaryUserServerUrl != null) restartIntent.putExtra(
                BackgroundVpnConfigureActivity.extraKeySecondaryUrl,
                secondaryUserServerUrl
            )
            startForegroundServiceCompat(restartIntent)
        } else {
            destroy()
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(BROADCAST_VPN_INACTIVE))
        }
        updateServiceTile()
    }

    override fun onRevoke() {
        log("onRevoke() called")
        destroy()
        stopForeground(true)
        stopSelf()
        if (getPreferences().disallowOtherVpns) {
            log("Disallow other VPNs is true, restarting in 250ms")
            Handler(Looper.getMainLooper()).postDelayed({
                BackgroundVpnConfigureActivity.prepareVpn(this, primaryUserServerUrl, secondaryUserServerUrl)
            }, 250)
        }
    }

    private fun createBuilder(): Builder {
        log("Creating the VpnBuilder.")
        val builder = Builder()
        val useIpv6 = getPreferences().enableIpv6 && (getPreferences().forceIpv6 || hasDeviceIpv6Address())
        val useIpv4 =
            !useIpv6 || (getPreferences().enableIpv4 && (getPreferences().forceIpv4 || hasDeviceIpv4Address()))

        val dummyServerIpv4 = getPreferences().dummyDnsAddressIpv4
        val dummyServerIpv6 = getPreferences().dummyDnsAddressIpv6
        log("Dummy address for Ipv4: $dummyServerIpv4")
        log("Dummy address for Ipv6: $dummyServerIpv6")
        log("Using IPv6: $useIpv6")
        log("Using IPv4: $useIpv4")

        var couldSetAddress = false
        if (useIpv4) {
            for (address in resources.getStringArray(R.array.interface_address_prefixes)) {
                val prefix = address.split("/")[0]
                val mask = address.split("/")[1].toInt()
                try {
                    builder.addAddress("$prefix.134", mask)
                    couldSetAddress = true
                    log("Ipv4-Address set to $prefix.134./$mask")
                    break
                } catch (ignored: IllegalArgumentException) {
                    log("Couldn't set Ipv4-Address $prefix.134/$mask")
                }
            }
            if (!couldSetAddress) {
                builder.addAddress("192.168.0.10", 24)
                log("Couldn't set any IPv4 dynamic address, trying 192.168.0.10...")
            }
        }
        couldSetAddress = false

        var tries = 0
        if (useIpv6) {
            do {
                val addr = NetworkUtil.randomLocalIPv6Address()
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

        if (getPreferences().catchKnownDnsServers) {
            log("Interception of requests towards known DNS servers is enabled, adding routes.")
            for (server in DnsServerInformation.waitUntilKnownServersArePopulated(-1)!!.values) {
                log("Adding all routes for ${server.name}")
                if (useIpv4) for (ipv4Server in server.getIpv4Servers()) {
                    log("Adding route for Ipv4 ${ipv4Server.address.address}")
                    builder.addRoute(ipv4Server.address.address, 32)
                } else log("Not adding routes of IPv4 servers.")
                if (useIpv6) for (ipv6Server in server.getIpv6Servers()) {
                    log("Adding route for Ipv6 ${ipv6Server.address.address}")
                    builder.addRoute(ipv6Server.address.address, 128)
                } else log("Not adding routes of IPv6 servers.")
            }
        } else log("Not intercepting traffic towards known DNS servers.")
        builder.setSession(getString(R.string.app_name))
        if (useIpv4) {
            builder.addDnsServer(dummyServerIpv4)
            builder.addRoute(dummyServerIpv4, 32)
            builder.allowFamily(OsConstants.AF_INET)
        }
        if (useIpv6) {
            builder.addDnsServer(dummyServerIpv6)
            builder.addRoute(dummyServerIpv6, 128)
            builder.allowFamily(OsConstants.AF_INET6)
        }
        builder.setBlocking(true)

        log("Applying ${getPreferences().totalBypassPackageCount} disallowed packages.")
        for (defaultBypassPackage in getPreferences().bypassPackagesIterator) {
            if (isPackageInstalled(defaultBypassPackage)) { //TODO Check what is faster: catching the exception, or checking ourselves
                builder.addDisallowedApplication(defaultBypassPackage)
            } else log("Package $defaultBypassPackage not installed, thus not bypassing")
        }
        return builder
    }

    private fun hasDeviceIpv4Address(): Boolean {
        val mgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        for (network in mgr.allNetworks) {
            val info = mgr.getNetworkInfo(network)
            if (info.isConnected) {
                val linkProperties = mgr.getLinkProperties(network)
                for (linkAddress in linkProperties.linkAddresses) {
                    if (linkAddress.address is Inet4Address && !linkAddress.address.isLoopbackAddress) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun hasDeviceIpv6Address(): Boolean {
        val mgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        for (network in mgr.allNetworks) {
            val info = mgr.getNetworkInfo(network)
            if (info.isConnected) {
                val linkProperties = mgr.getLinkProperties(network)
                for (linkAddress in linkProperties.linkAddresses) {
                    if (linkAddress.address is Inet6Address && !linkAddress.address.isLoopbackAddress) {
                        return true
                    }
                }
            }
        }
        return false
    }

    override fun run() {
        log("run() called")
        val list = mutableListOf<ServerConfiguration>()
        list.add(primaryServer)
        if (secondaryServer != null) list.add(secondaryServer!!)
        log("Using servers: $1", formatArgs = *arrayOf(list))

        log("Creating handle.")
        handle = ProxyHandler(
            list,
            connectTimeout = 500,
            queryCountCallback = {
                setNotificationText()
                updateNotification(it)
            }
        )
        log("Handle created, creating DNS proxy")
        val dnsCache: SimpleDnsCache?
        dnsCache = if (getPreferences().useDnsCache) {
            val cacheControl: CacheControl = if (!getPreferences().useDefaultDnsCacheTime) {
                val cacheTime = getPreferences().customDnsCacheTime.toLong()
                object : CacheControl {
                    override suspend fun getTtl(question: Question, record: Record<*>): Long = cacheTime
                    override suspend fun getTtl(dnsName: DnsName, type: Record.TYPE, record: Record<*>): Long =
                        cacheTime

                    override fun shouldCache(question: Question): Boolean = true
                }
            } else DefaultCacheControl()
            SimpleDnsCache(cacheControl, CacheStrategy(getPreferences().maxCacheSize))
        } else null
        dnsProxy = SmokeProxy(handle!!, this, dnsCache)
        log("DnsProxy created, creating VPN proxy")
        vpnProxy = VPNTunnelProxy(dnsProxy!!)

        log("VPN proxy creating, trying to run...")
        vpnProxy!!.run(fileDescriptor!!)
        log("VPN proxy started.")
        currentTrafficStats = vpnProxy!!.trafficStats
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(BROADCAST_VPN_ACTIVE))
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getApplicationInfo(packageName, 0).enabled
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}

fun AbstractHttpsDNSHandle.Companion.findKnownServerByUrl(url: String): HttpsDnsServerInformation? {
    for (info in AbstractHttpsDNSHandle.KNOWN_DNS_SERVERS.values) {
        for (server in info.servers) {
            if (server.address.getUrl().contains(url, true)) return info
        }
    }
    return null
}

enum class Command : Serializable {
    STOP, RESTART
}