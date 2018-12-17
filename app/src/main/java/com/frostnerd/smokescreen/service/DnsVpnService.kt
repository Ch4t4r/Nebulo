package com.frostnerd.smokescreen.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import com.frostnerd.dnstunnelproxy.DnsServerInformation
import com.frostnerd.networking.NetworkUtil
import com.frostnerd.smokescreen.R
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.frostnerd.encrypteddnstunnelproxy.AbstractHttpsDNSHandle
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.ServerConfiguration
import com.frostnerd.encrypteddnstunnelproxy.createSimpleServerConfig
import com.frostnerd.smokescreen.BuildConfig
import com.frostnerd.smokescreen.activity.BackgroundVpnConfigureActivity
import com.frostnerd.smokescreen.activity.MainActivity
import com.frostnerd.smokescreen.util.Notifications
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.util.proxy.ProxyHandler
import com.frostnerd.smokescreen.util.proxy.SmokeProxy
import com.frostnerd.vpntunnelproxy.TrafficStats
import com.frostnerd.vpntunnelproxy.VPNTunnelProxy
import java.io.Serializable


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
    private var secondaryServer: ServerConfiguration? = null

    companion object {
        const val BROADCAST_VPN_ACTIVE = BuildConfig.APPLICATION_ID + ".VPN_ACTIVE"
        const val BROADCAST_VPN_INACTIVE = BuildConfig.APPLICATION_ID + ".VPN_INACTIVE"
        var currentTrafficStats: TrafficStats? = null
            private set

        fun sendCommand(context: Context, command: Command) {
            context.startService(Intent(context, DnsVpnService::class.java).putExtra("command", command))
        }
    }


    override fun onCreate() {
        super.onCreate()
        notificationBuilder = NotificationCompat.Builder(this, Notifications.servicePersistentNotificationChannel(this))
        notificationBuilder.setContentTitle(getString(R.string.app_name))
        notificationBuilder.setSmallIcon(R.mipmap.ic_launcher_round)
        notificationBuilder.setOngoing(true)
        notificationBuilder.setAutoCancel(false)
        notificationBuilder.setUsesChronometer(true)
        notificationBuilder.setContentIntent(
            PendingIntent.getActivity(
                this, 1,
                Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        updateNotification(0)
    }

    private fun updateNotification(queryCount: Int) {
        notificationBuilder.setSubText(getString(R.string.notification_main_subtext, queryCount))
        startForeground(1, notificationBuilder.build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.hasExtra("command")) {
            val command = intent.getSerializableExtra("command") as Command

            when (command) {
                Command.STOP -> {
                    destroy()
                    stopForeground(true)
                    stopSelf()
                }
                Command.RESTART -> {
                    recreateVpn()
                }
            }
        }
        if(!destroyed) {
            if (!this::primaryServer.isInitialized) {
                if (intent != null) {
                    primaryServer = if (intent.hasExtra(BackgroundVpnConfigureActivity.extraKeyPrimaryUrl))
                        ServerConfiguration.createSimpleServerConfig(
                            intent.getStringExtra(BackgroundVpnConfigureActivity.extraKeyPrimaryUrl)
                        )
                    else getPreferences().primaryServerConfig

                    secondaryServer = if (intent.hasExtra(BackgroundVpnConfigureActivity.extraKeySecondaryUrl))
                        ServerConfiguration.createSimpleServerConfig(intent.getStringExtra(BackgroundVpnConfigureActivity.extraKeySecondaryUrl))
                    else getPreferences().secondaryServerConfig
                } else {
                    primaryServer = getPreferences().primaryServerConfig
                    secondaryServer = getPreferences().secondaryServerConfig
                }
                val text = if (secondaryServer != null) {
                    getString(
                        R.string.notification_main_text_with_secondary,
                        primaryServer.urlCreator.baseUrl,
                        secondaryServer!!.urlCreator.baseUrl,
                        getPreferences().totalBypassPackageCount
                    )
                } else {
                    getString(
                        R.string.notification_main_text,
                        primaryServer.urlCreator.baseUrl,
                        getPreferences().totalBypassPackageCount
                    )
                }
                notificationBuilder.setStyle(NotificationCompat.BigTextStyle(notificationBuilder).bigText(text))
            }
            updateNotification(0)
            establishVpn()
            return Service.START_STICKY
        } else {
            return Service.START_NOT_STICKY
        }
    }

    private fun establishVpn() {
        if (fileDescriptor == null) {
            fileDescriptor = createBuilder().establish()
            run()
        }
    }

    private fun recreateVpn() {
        destroy()
        establishVpn()
    }

    private fun destroy() {
        if (!destroyed) {
            vpnProxy?.stop()
            fileDescriptor?.close()
            vpnProxy = null
            fileDescriptor = null
            destroyed = true;
        }
        currentTrafficStats = null
    }

    override fun onDestroy() {
        super.onDestroy()
        destroy()
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(BROADCAST_VPN_INACTIVE))
    }

    private fun createBuilder(): Builder {
        val builder = Builder()

        val dummyServerIpv4 = getPreferences().dummyDnsAddressIpv4
        val dummyServerIpv6 = getPreferences().dummyDnsAddressIpv6

        builder.addAddress("192.168.0.10", 24)
        builder.addAddress(NetworkUtil.randomLocalIPv6Address(), 48)
        if (getPreferences().catchKnownDnsServers) {
            for (server in DnsServerInformation.waitUntilKnownServersArePopulated(-1)!!.values) {
                for (ipv4Server in server.getIpv4Servers()) {
                    builder.addRoute(ipv4Server.address.address, 32)
                }
                for (ipv6Server in server.getIpv6Servers()) {
                    builder.addRoute(ipv6Server.address.address, 128)
                }
            }
        }
        builder.setSession(getString(R.string.app_name))
        builder.addDnsServer(dummyServerIpv4)
        builder.addDnsServer(dummyServerIpv6)
        builder.addRoute(dummyServerIpv4, 32)
        builder.addRoute(dummyServerIpv6, 128)
        builder.allowFamily(OsConstants.AF_INET)
        builder.allowFamily(OsConstants.AF_INET6)
        builder.setBlocking(true)

        for (defaultBypassPackage in getPreferences().bypassPackagesIterator) {
            if (isPackageInstalled(defaultBypassPackage)) {
                builder.addDisallowedApplication(defaultBypassPackage)
            }
        }
        return builder
    }

    override fun run() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            e.printStackTrace()
            prev.uncaughtException(t, e)
        }

        val list = mutableListOf<ServerConfiguration>()
        list.add(primaryServer)
        if (secondaryServer != null) list.add(secondaryServer!!)

        handle = ProxyHandler(
            list,
            connectTimeout = 500,
            queryCountCallback = ::updateNotification
        )
        dnsProxy = SmokeProxy(handle!!, this)
        vpnProxy = VPNTunnelProxy(dnsProxy!!)

        vpnProxy!!.run(fileDescriptor!!)
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