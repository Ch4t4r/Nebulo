package com.frostnerd.smokescreen.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.*
import android.os.*
import android.system.OsConstants
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.frostnerd.dnstunnelproxy.*
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.ServerConfiguration
import com.frostnerd.encrypteddnstunnelproxy.tls.TLSUpstreamAddress
import com.frostnerd.general.CombinedIterator
import com.frostnerd.general.service.isServiceRunning
import com.frostnerd.networking.NetworkUtil
import com.frostnerd.preferenceskt.typedpreferences.TypedPreferences
import com.frostnerd.smokescreen.*
import com.frostnerd.smokescreen.BuildConfig
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.activity.BackgroundVpnConfigureActivity
import com.frostnerd.smokescreen.activity.PinActivity
import com.frostnerd.smokescreen.activity.PinType
import com.frostnerd.smokescreen.database.entities.CachedResponse
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.util.Notifications
import com.frostnerd.smokescreen.util.proxy.ProxyBypassHandler
import com.frostnerd.smokescreen.util.proxy.ProxyHttpsHandler
import com.frostnerd.smokescreen.util.proxy.ProxyTlsHandler
import com.frostnerd.smokescreen.util.proxy.SmokeProxy
import com.frostnerd.vpntunnelproxy.TrafficStats
import com.frostnerd.vpntunnelproxy.VPNTunnelProxy
import kotlinx.coroutines.*
import org.minidns.dnsmessage.DnsMessage
import org.minidns.dnsmessage.Question
import org.minidns.dnsname.DnsName
import org.minidns.record.A
import org.minidns.record.AAAA
import org.minidns.record.Record
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.Serializable
import java.lang.IllegalStateException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.TimeoutException
import java.util.logging.Level


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
    private var handle: ProxyHttpsHandler? = null
    private var dnsProxy: SmokeProxy? = null
    private var vpnProxy: VPNTunnelProxy? = null
    private var destroyed = false
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var noConnectionNotificationBuilder: NotificationCompat.Builder
    private var noConnectionNotificationShown = false
    private lateinit var serverConfig:DnsServerConfiguration
    private lateinit var settingsSubscription: TypedPreferences<SharedPreferences>.OnPreferenceChangeListener
    private var networkCallback:ConnectivityManager.NetworkCallback? = null
    private var pauseNotificationAction:NotificationCompat.Action? = null
    private var queryCountOffset: Long = 0
    private var packageBypassAmount = 0
    private var connectedToANetwork:Boolean? = null

    /*
        URLs passed to the Service, which haven't been retrieved from the settings.
        Null if the current servers are from the settings
     */
    private var userServerConfig:DnsServerInformation<*>? = null

    companion object {
        const val BROADCAST_VPN_ACTIVE = BuildConfig.APPLICATION_ID + ".VPN_ACTIVE"
        const val BROADCAST_VPN_INACTIVE = BuildConfig.APPLICATION_ID + ".VPN_INACTIVE"
        var currentTrafficStats: TrafficStats? = null
            private set

        fun startVpn(context: Context, serverInfo:DnsServerInformation<*>? = null) {
            val intent = Intent(context, DnsVpnService::class.java)
            if (serverInfo != null) {
                BackgroundVpnConfigureActivity.writeServerInfoToIntent(serverInfo, intent)
            }
            context.startForegroundServiceCompat(intent)
        }

        fun restartVpn(context: Context, fetchServersFromSettings: Boolean) {
            if(context.isServiceRunning(DnsVpnService::class.java)) {
                val bundle = Bundle()
                bundle.putBoolean("fetch_servers", fetchServersFromSettings)
                sendCommand(context, Command.RESTART, bundle)
            } else startVpn(context)
        }

        fun restartVpn(context: Context, serverInfo:DnsServerInformation<*>?) {
            if(context.isServiceRunning(DnsVpnService::class.java)) {
                val bundle = Bundle()
                if(serverInfo != null) {
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
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            log("Encountered an uncaught exception.")
            destroy()
            stopForeground(true)
            stopSelf()
            (application as SmokeScreen).customUncaughtExceptionHandler.uncaughtException(t, e)
        }
        log("Service onCreate()")
        createNotification()
        updateServiceTile()
        subscribeToSettings()
        addNetworkChangeListener()
        log("Service created.")
    }

    private fun addNetworkChangeListener() {
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
                if(this@DnsVpnService::serverConfig.isInitialized) serverConfig.forEachAddress { _, upstreamAddress ->
                    upstreamAddress.addressCreator.reset()
                    upstreamAddress.addressCreator.resolve(force = true, runResolveNow = true)
                }
                if (fileDescriptor != null) recreateVpn(false, null)
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
            "null_terminate_keweon",
            "allow_ipv6_traffic",
            "allow_ipv4_traffic",
            "dns_server_config",
            "notification_allow_stop",
            "notification_allow_pause",
            "dns_rules_enabled"
        )
        settingsSubscription = getPreferences().listenForChanges(relevantSettings, getPreferences().preferenceChangeListener {
                changes ->
            log("The Preference(s) ${changes.keys} have changed, restarting the VPN.")
            log("Detailed changes: $changes")
            if("hide_notification_icon" in changes || "hide_notification_icon" in changes) {
                log("Recreating the notification because of the change in preferences")
                createNotification()
                setNotificationText()
            } else if("notification_allow_pause" in changes || "notification_allow_stop" in changes) {
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
        notificationBuilder = NotificationCompat.Builder(this, Notifications.servicePersistentNotificationChannel(this))
        if(getPreferences().hideNotificationIcon)
            notificationBuilder.priority = NotificationCompat.PRIORITY_MIN
        if(!getPreferences().showNotificationOnLockscreen)
            notificationBuilder.setVisibility(NotificationCompat.VISIBILITY_SECRET)
        notificationBuilder.setContentTitle(getString(R.string.app_name))
        notificationBuilder.setSmallIcon(R.drawable.ic_mainnotification)
        notificationBuilder.setOngoing(true)
        notificationBuilder.setAutoCancel(false)
        notificationBuilder.setSound(null)
        notificationBuilder.setOnlyAlertOnce(true)
        notificationBuilder.setUsesChronometer(true)
        notificationBuilder.setContentIntent(
            PendingIntent.getActivity(
                this, 1,
                Intent(this, PinActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        if(getPreferences().allowStopInNotification) {
            val stopPendingIntent =
                PendingIntent.getService(this, 1, commandIntent(this, Command.STOP), PendingIntent.FLAG_CANCEL_CURRENT)
            val stopAction = NotificationCompat.Action(R.drawable.ic_stop, getString(R.string.all_stop), stopPendingIntent)
            notificationBuilder.addAction(stopAction)
        }
        if(getPreferences().allowPauseInNotification) {
            val pausePendingIntent =
                PendingIntent.getService(this, 2, commandIntent(this, Command.PAUSE_RESUME), PendingIntent.FLAG_CANCEL_CURRENT)
            pauseNotificationAction = NotificationCompat.Action(R.drawable.ic_stat_pause, getString(R.string.all_pause), pausePendingIntent)
            notificationBuilder.addAction(pauseNotificationAction)
        }
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

    private fun showNoConnectionNotification() {
        if(!getPreferences().showNoConnectionNotification) return
        if(!this::noConnectionNotificationBuilder.isInitialized) {
            noConnectionNotificationBuilder = NotificationCompat.Builder(this, Notifications.noConnectionNotificationChannelId(this))
            noConnectionNotificationBuilder.priority = NotificationCompat.PRIORITY_HIGH
            noConnectionNotificationBuilder.setOngoing(false)
            noConnectionNotificationBuilder.setSmallIcon(R.drawable.ic_cloud_strikethrough)
            noConnectionNotificationBuilder.setContentTitle(getString(R.string.notification_noconnection_title))
            noConnectionNotificationBuilder.setContentText(getString(R.string.notification_noconnection_text))
            noConnectionNotificationBuilder.setStyle(NotificationCompat.BigTextStyle(notificationBuilder).bigText(getString(R.string.notification_noconnection_text)))
        }
        if(!noConnectionNotificationShown) (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(2, noConnectionNotificationBuilder.build())
        noConnectionNotificationShown = true
    }

    private fun hideNoConnectionNotification() {
        if(noConnectionNotificationShown) (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(2)
        noConnectionNotificationShown = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("Service onStartCommand", intent = intent)
        if (intent != null && intent.hasExtra("command")) {
            val command = intent.getSerializableExtra("command") as Command

            when (command) {
                Command.STOP -> {
                    log("Received STOP command.")
                    if(PinActivity.shouldValidatePin(this, intent)) {
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
                    if(vpnProxy != null) {
                        destroy(false)
                        pauseNotificationAction?.title = getString(R.string.all_resume)
                        pauseNotificationAction?.icon = R.drawable.ic_stat_resume
                        notificationBuilder.setSmallIcon(R.drawable.ic_notification_paused)
                    } else {
                        recreateVpn(false, null)
                        pauseNotificationAction?.title = getString(R.string.all_pause)
                        pauseNotificationAction?.icon = R.drawable.ic_stat_pause
                        notificationBuilder.setSmallIcon(R.drawable.ic_mainnotification)
                    }
                    updateNotification()
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
                    userServerConfig
                )
            } else {
                log("The VPN is prepared, proceeding.")
                if (!destroyed) {
                    if (!this::serverConfig.isInitialized) {
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
        userServerConfig = BackgroundVpnConfigureActivity.readServerInfoFromIntent(intent)
        serverConfig = getServerConfig()
        serverConfig.forEachAddress { _, address ->
            if(!address.addressCreator.isCurrentlyResolving()) address.addressCreator.resolveOrGetResultOrNull(true)
            address.addressCreator.whenResolveFailed {
                showNoConnectionNotification()
                if(it is TimeoutException) {
                    address.addressCreator.resolveOrGetResultOrNull(true)
                    address.addressCreator.whenResolveFinishedSuccessfully {
                        hideNoConnectionNotification()
                    }
                }
            }
            address.addressCreator.whenResolveFinishedSuccessfully {
                hideNoConnectionNotification()
            }
        }
        log("Server configuration updated to $serverConfig")
    }

    private fun setNotificationText() {
        val primaryServer:String
        val secondaryServer:String?
        if(serverConfig.httpsConfiguration != null) {
            notificationBuilder.setContentTitle(getString(R.string.notification_main_title_https))
            primaryServer = serverConfig.httpsConfiguration!![0].urlCreator.address.getUrl(true)
            secondaryServer = serverConfig.httpsConfiguration!!.getOrNull(1)?.urlCreator?.address?.getUrl(true)
        } else {
            notificationBuilder.setContentTitle(getString(R.string.notification_main_title_tls))
            primaryServer = serverConfig.tlsConfiguration!![0].formatToString()
            secondaryServer = serverConfig.tlsConfiguration!!.getOrNull(1)?.formatToString()
        }
        val text = if (secondaryServer != null) {
            getString(
                if(getPreferences().isBypassBlacklist) R.string.notification_main_text_with_secondary else R.string.notification_main_text_with_secondary_whitelist,
                primaryServer,
                secondaryServer,
                packageBypassAmount,
                dnsProxy?.cache?.livingCachedEntries() ?: 0
            )
        } else {
            getString(
                if(getPreferences().isBypassBlacklist) R.string.notification_main_text else R.string.notification_main_text_whitelist,
                primaryServer,
                packageBypassAmount,
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
            setNotificationText()
            updateNotification()
        } else log("Connection already running, no need to establish.")
    }

    private fun recreateVpn(reloadServerConfiguration: Boolean, intent: Intent?) {
        log("Recreating the VPN (destroying & establishing)")
        destroy(false)
        if (VpnService.prepare(this) == null) {
            log("VpnService is still prepared, establishing VPN.")
            destroyed = false
            if (reloadServerConfiguration || !this::serverConfig.isInitialized) {
                log("Re-fetching the servers (from intent or settings)")
                setServerConfiguration(intent)
            } else serverConfig.forEachAddress { _, address ->
                address.addressCreator.resolveOrGetResultOrNull(true)
            }
            establishVpn()
            setNotificationText()
            updateNotification(0)
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

    private fun destroy(isStoppingCompletely:Boolean = true) {
        log("Destroying the VPN")
        if(isStoppingCompletely || connectedToANetwork == true) hideNoConnectionNotification()
        if (!destroyed) {
            queryCountOffset += currentTrafficStats?.packetsReceivedFromDevice ?: 0
            vpnProxy?.stop()
            fileDescriptor?.close()
            if(isStoppingCompletely && networkCallback != null) {
                (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(networkCallback)
                networkCallback = null
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
            if(userServerConfig != null) BackgroundVpnConfigureActivity.writeServerInfoToIntent(userServerConfig!!, restartIntent)
            startForegroundServiceCompat(restartIntent)
        } else {
            destroy()
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(BROADCAST_VPN_INACTIVE))
        }
        updateServiceTile()
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
        }
    }

    private fun createBuilder(): Builder {
        log("Creating the VpnBuilder.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetwork
            log("Current active network: $activeNetwork")
        }
        val builder = Builder()

        val deviceHasIpv6 = hasDeviceIpv6Address()
        val deviceHasIpv4 = hasDeviceIpv4Address()

        val dhcpDnsServer = getDhcpDnsServers()

        val useIpv6 = getPreferences().enableIpv6 && (getPreferences().forceIpv6 || deviceHasIpv6)
        val useIpv4 =
            !useIpv6 || (getPreferences().enableIpv4 && (getPreferences().forceIpv4 || deviceHasIpv4))

        val allowIpv4Traffic = useIpv4 || getPreferences().allowIpv4Traffic
        val allowIpv6Traffic = useIpv6 || getPreferences().allowIpv6Traffic

        val dummyServerIpv4 = getPreferences().dummyDnsAddressIpv4
        val dummyServerIpv6 = getPreferences().dummyDnsAddressIpv6
        log("Dummy address for Ipv4: $dummyServerIpv4")
        log("Dummy address for Ipv6: $dummyServerIpv6")
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
                    log("Ipv4-Address set to $prefix.134./$mask")
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
                server.servers.forEach {
                    it.address.addressCreator.whenResolveFinishedSuccessfully { addresses ->
                        addresses.forEach { address ->
                            if (address is Inet6Address && useIpv6) {
                                log("Adding route for Ipv6 $address")
                                builder.addRoute(address, 128)
                            } else if (address is Inet4Address && useIpv4) {
                                log("Adding route for Ipv4 $address")
                                builder.addRoute(address, 32)
                            }
                        }
                    }
                    if(!it.address.addressCreator.startedResolve && !it.address.addressCreator.isCurrentlyResolving()) it.address.addressCreator.resolveOrGetResultOrNull(true, true)
                }
            }
        } else log("Not intercepting traffic towards known DNS servers.")
        builder.setSession(getString(R.string.app_name))
        if (useIpv4) {
            builder.addDnsServer(dummyServerIpv4)
            builder.addRoute(dummyServerIpv4, 32)
            dhcpDnsServer.forEach {
                if (it is Inet4Address) {
                    builder.addRoute(it, 32)
                    }
                }
            } else if (deviceHasIpv4 && allowIpv4Traffic) builder.allowFamily(OsConstants.AF_INET) // If not allowing no IPv4 connections work anymore.

        if (useIpv6) {
            builder.addDnsServer(dummyServerIpv6)
            builder.addRoute(dummyServerIpv6, 128)
            dhcpDnsServer.forEach {
                if(it is Inet6Address) {
                    builder.addRoute(it, 128)
                }
            }
        } else if(deviceHasIpv6 && allowIpv6Traffic) builder.allowFamily(OsConstants.AF_INET6)
        builder.setBlocking(true)

        log("Applying disallowed packages.")
        val userBypass = getPreferences().userBypassPackages
        val defaultBypass = getPreferences().defaultBypassPackages
        if(getPreferences().isBypassBlacklist || userBypass.size == 0) {
            log("Mode is set to blacklist, bypassing ${userBypass.size + defaultBypass.size} packages.")
            for (defaultBypassPackage in CombinedIterator(userBypass.iterator(), defaultBypass.iterator())) {
                if (isPackageInstalled(defaultBypassPackage)) { //TODO Check what is faster: catching the exception, or checking ourselves
                    builder.addDisallowedApplication(defaultBypassPackage)
                } else log("Package $defaultBypassPackage not installed, thus not bypassing")
            }
            packageBypassAmount = userBypass.size
        } else {
            log("Mode is set to whitelist, whitelisting ${userBypass.size} packages.")
            for (pkg in userBypass) {
                if(!defaultBypass.contains(pkg)) {
                    if (isPackageInstalled(pkg)) { //TODO Check what is faster: catching the exception, or checking ourselves
                        builder.addAllowedApplication(pkg)
                        packageBypassAmount++
                    } else log("Package $pkg not installed, thus not bypassing")
                } else log("Not whitelisting $pkg, it is blacklisted by default.")
            }
        }
        return builder
    }

    private fun hasDeviceIpv4Address(): Boolean {
        val mgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        for (network in mgr.allNetworks) {
            if(network == null) continue
            val info = mgr.getNetworkInfo(network) ?: continue
            val capabilities = mgr.getNetworkCapabilities(network) ?: continue
            if (info.isConnected && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                val linkProperties = mgr.getLinkProperties(network) ?: continue
                log("Checking for IPv4 address in connected non-VPN network ${info.typeName}")
                for (linkAddress in linkProperties.linkAddresses) {
                    if (linkAddress.address is Inet4Address && !linkAddress.address.isLoopbackAddress) {
                        log("IPv4 address found.")
                        return true
                    }
                }
            }
        }
        log("No IPv4 addresses found.")
        return false
    }

    private fun hasDeviceIpv6Address(): Boolean {
        val mgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        for (network in mgr.allNetworks) {
            if(network == null) continue
            val info = mgr.getNetworkInfo(network) ?: continue
            val capabilities = mgr.getNetworkCapabilities(network) ?: continue
            if (info.isConnected && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                val linkProperties = mgr.getLinkProperties(network) ?: continue
                log("Checking for IPv6 address in connected non-VPN network ${info.typeName}")
                for (linkAddress in linkProperties.linkAddresses) {
                    if (linkAddress.address is Inet6Address && !linkAddress.address.isLoopbackAddress) {
                        log("IPv6 address found.")
                        return true
                    }
                }
            }
        }
        log("No IPv6 addresses found.")
        return false
    }

    private fun getDhcpDnsServers():List<InetAddress> {
        val mgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        for (network in mgr.allNetworks) {
            if(network != null) continue
            val info = mgr.getNetworkInfo(network) ?: continue
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
        return if(userServerConfig != null) {
            userServerConfig!!.let {config ->
                return if(config is HttpsDnsServerInformation) DnsServerConfiguration(config.serverConfigurations.values.toList(), null)
                else {
                    DnsServerConfiguration(null, config.servers.map {
                        it.address as TLSUpstreamAddress
                    })
                }
            }
        }
        else {
            val config = getPreferences().dnsServerConfig
            if(config.hasTlsServer()) {
                DnsServerConfiguration(null, config.servers.map {
                    it.address as TLSUpstreamAddress
                })
            } else {
                DnsServerConfiguration((config as HttpsDnsServerInformation).serverConfigurations.map {
                    it.value
                }, null)
            }
        }
    }

    override fun run() {
        log("run() called")
        log("Starting with config: $serverConfig")

        log("Creating handle.")
        val handle:DnsHandle
        if(serverConfig.httpsConfiguration != null) {
            handle = ProxyHttpsHandler(
                serverConfig.httpsConfiguration!!,
                connectTimeout = 5000,
                queryCountCallback = {
                    setNotificationText()
                    updateNotification(it)
                },
                nullRouteKeweon = getPreferences().isUsingKeweon() && getPreferences().nullTerminateKeweon
            )
        } else {
            handle = ProxyTlsHandler(serverConfig.tlsConfiguration!!,
                connectTimeout = 2000,
                queryCountCallback = {
                    setNotificationText()
                    updateNotification(it)
                },
                nullRouteKeweon = getPreferences().isUsingKeweon() && getPreferences().nullTerminateKeweon)
        }
        log("Handle created, creating DNS proxy")
        handle.ipv6Enabled = getPreferences().enableIpv6 && (getPreferences().forceIpv6 || hasDeviceIpv6Address())
        handle.ipv4Enabled =
            !handle.ipv6Enabled || (getPreferences().enableIpv4 && (getPreferences().forceIpv4 || hasDeviceIpv4Address()))

        dnsProxy = SmokeProxy(handle, createProxyBypassHandlers(), createDnsCache(), createQueryLogger(), createLocalResolver())
        log("DnsProxy created, creating VPN proxy")
        vpnProxy = VPNTunnelProxy(dnsProxy!!, vpnService = this, coroutineScope = CoroutineScope(
            newFixedThreadPoolContext(1, "proxy-pool")), logger = object:com.frostnerd.vpntunnelproxy.Logger {
            override fun logException(ex: Exception, terminal: Boolean, level: Level) {
                if(terminal) log(ex)
                else log(Logger.stacktraceToString(ex), "VPN-LIBRARY, $level")
            }

            override fun logMessage(message: String, level: Level) {
                if(level >= Level.INFO) {
                    log(message, "VPN-LIBRARY, $level")
                }
            }
        })

        log("VPN proxy creating, trying to run...")
        fileDescriptor?.let {
            vpnProxy?.run(it)
        } ?: kotlin.run {
            recreateVpn(false, null)
            return
        }
        log("VPN proxy started.")
        currentTrafficStats = vpnProxy?.trafficStats
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(BROADCAST_VPN_ACTIVE))
    }

    private fun createQueryLogger(): QueryListener? {
        return if(getPreferences().loggingEnabled || getPreferences().queryLoggingEnabled) {
            com.frostnerd.smokescreen.util.proxy.QueryListener(this)
        } else null
    }


    /**
     * Creates bypass handlers for each network and its associated search domains
     * Requests for .*SEARCHDOMAIN won't use doh and are sent to the DNS servers of the network they originated from.
     */
    private fun createProxyBypassHandlers(): MutableList<DnsHandle> {
        val bypassHandlers = mutableListOf<DnsHandle>()
        if(getPreferences().bypassSearchdomains) {
            log("Creating bypass handlers for search domains of connected networks.")
            val mgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            for (network in mgr.allNetworks) {
                if(network != null) continue
                val networkInfo = mgr.getNetworkInfo(network) ?: continue
                if (networkInfo.isConnected && !mgr.isVpnNetwork(network)) {
                    val linkProperties = mgr.getLinkProperties(network) ?: continue
                    if (!linkProperties.domains.isNullOrBlank()) {
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
        if(getPreferences().pauseOnCaptivePortal) {
            val dhcpServers = getDhcpDnsServers()
            if(!dhcpServers.isEmpty()) bypassHandlers.add(CaptivePortalUdpDnsHandle(targetDnsServer = { dhcpServers.first() }))
        }
        bypassHandlers.add(NoConnectionDnsHandle(getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager, NoConnectionDnsHandle.Behavior.DROP_PACKETS) {
            log("Connection changed to connected=$it", "NoConnectionDnsHandle-Listener")
            connectedToANetwork = it
            if(!it) showNoConnectionNotification()
            else hideNoConnectionNotification()
        })
        return bypassHandlers
    }

    private fun createDnsCache():SimpleDnsCache? {
        val dnsCache: SimpleDnsCache?
        dnsCache = if (getPreferences().useDnsCache) {
            log("Creating DNS Cache.")
            val cacheControl: CacheControl = if (!getPreferences().useDefaultDnsCacheTime) {
                val cacheTime = getPreferences().customDnsCacheTime.toLong()
                val nxDomainCacheTime = getPreferences().nxDomainCacheTime.toLong()
                object : CacheControl {
                    override suspend fun getTtl(
                        answerMessage: DnsMessage,
                        dnsName: DnsName,
                        type: Record.TYPE,
                        record: Record<*>
                    ): Long {
                        return if(answerMessage.responseCode == DnsMessage.RESPONSE_CODE.NX_DOMAIN) nxDomainCacheTime else cacheTime
                    }

                    override suspend fun getTtl(question: Question, record: Record<*>): Long = cacheTime


                    override fun shouldCache(question: Question): Boolean = true
                }
            } else DefaultCacheControl(getPreferences().minimumCacheTime.toLong())
            val onClearCache:((currentCache:Map<String, Map<Record.TYPE, Map<Record<*>, Long>>>) -> Unit)? = if(getPreferences().keepDnsCacheAcrossLaunches) {
                { cache ->
                    log("Persisting current cache to Database.")
                    getDatabase().cachedResponseDao().deleteAll()
                    var persisted = 0
                    val entries = mutableListOf<CachedResponse>()
                    for(entry in cache) {
                        for(cachedType in entry.value) {
                            val recordsToPersist:MutableMap<Record<*>, Long> = mutableMapOf()
                            for(cachedRecord in cachedType.value) {
                                if(cachedRecord.value > System.currentTimeMillis()) {
                                    recordsToPersist[cachedRecord.key] = cachedRecord.value
                                    persisted++
                                }
                            }

                            if(!recordsToPersist.isEmpty()) {
                                entries.add(createPersistedCacheEntry(entry.key, cachedType.key, recordsToPersist))
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
            SimpleDnsCache(cacheControl, CacheStrategy(getPreferences().maxCacheSize), onClearCache = onClearCache)
        } else {
            log("Not creating DNS cache, is disabled.")
            null
        }
        if(dnsCache != null) log("Cache created.")

        // Restores persisted cache
        if(dnsCache != null && getPreferences().keepDnsCacheAcrossLaunches) {
            log("Restoring old cache")
            var restored = 0
            var tooOld = 0
            GlobalScope.launch {
                for (cachedResponse in getDatabase().cachedResponseRepository().getAllAsync(GlobalScope)) {
                    val records = mutableMapOf<Record<*>, Long>()
                    for (record in cachedResponse.records) {
                        if(record.value > System.currentTimeMillis()) {
                            val bytes = Base64.decode(record.key, Base64.NO_WRAP)
                            val stream = DataInputStream(ByteArrayInputStream(bytes))
                            records[Record.parse(stream, bytes)] = record.value
                            restored++
                        } else tooOld++
                    }
                    dnsCache.addToCache(DnsName.from(cachedResponse.dnsName), cachedResponse.type, records)
                }
                getDatabase().cachedResponseDao().deleteAll()
            }
            log("$restored old records restored, deleting persisted cache. $tooOld records were too old.")
            log("Persisted cache deleted.")
        }
        return dnsCache
    }

    private fun createLocalResolver():LocalResolver? {
        if(getPreferences().dnsRulesEnabled) {
            return object:LocalResolver(false) {
                private val dao = getDatabase().dnsRuleDao()
                private val resolveResults = mutableMapOf<Question, String>()
                private val wwwRegex = Regex("^www\\.")
                private val useUserRules = getPreferences().customHostsEnabled

                override suspend fun canResolve(question: Question): Boolean {
                    return if(question.type != Record.TYPE.A && question.type != Record.TYPE.AAAA) {
                        false
                    } else {
                        val uniformQuestion = question.name.toString().replace(wwwRegex, "")
                        val resolveResult = dao.findRuleTarget(uniformQuestion, question.type, useUserRules)
                        if (resolveResult != null) {
                            resolveResults[question] = resolveResult
                            true
                        } else false
                    }
                }

                override suspend fun resolve(question: Question): List<Record<*>> {
                    val result = resolveResults.remove(question)
                    return result?.let {
                        val data = if(question.type == Record.TYPE.A) {
                            A(it)
                        } else {
                            AAAA(it)
                        }
                        listOf(Record(question.name.toString(), question.type, question.clazz.value, 9999, data))
                    } ?: throw IllegalStateException()
                }

                override fun cleanup() {}

            }
        } else {
            return null
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
            entity.records[Base64.encodeToString(record.key.toByteArray(), Base64.NO_WRAP)] = record.value
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
    STOP, RESTART, PAUSE_RESUME
}

data class DnsServerConfiguration(val httpsConfiguration:List<ServerConfiguration>?, val tlsConfiguration:List<TLSUpstreamAddress>?) {

    fun forEachAddress(block:(isHttps:Boolean, UpstreamAddress) -> Unit) {
        httpsConfiguration?.forEach {
            block(true, it.urlCreator.address)
        }
        tlsConfiguration?.forEach {
            block(false, it)
        }
    }
}