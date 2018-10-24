package com.frostnerd.smokescreen.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import com.frostnerd.dnstunnelproxy.DnsServerInformation
import com.frostnerd.networking.NetworkUtil
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.util.Preferences
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.frostnerd.encrypteddnstunnelproxy.AbstractHttpsDNSHandle
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.ServerConfiguration
import com.frostnerd.smokescreen.BuildConfig
import com.frostnerd.smokescreen.util.Notifications
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.util.proxy.ProxyHandler
import com.frostnerd.smokescreen.util.proxy.SmokeProxy
import com.frostnerd.vpntunnelproxy.AsyncVPNTunnelProxy
import com.frostnerd.vpntunnelproxy.SyncScheduler
import com.frostnerd.vpntunnelproxy.ThreadScheduler
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
    private var vpnProxy: AsyncVPNTunnelProxy? = null
    private var destroyed = false
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var serverUrl: String
    private var secondaryServerUrl: String? = null

    companion object {
        const val BROADCAST_VPN_ACTIVE = BuildConfig.APPLICATION_ID + ".VPN_ACTIVE"
        const val BROADCAST_VPN_INACTIVE = BuildConfig.APPLICATION_ID + ".VPN_INACTIVE"

        fun sendCommand(context: Context, command: Command) {
            context.startService(Intent(context, DnsVpnService::class.java).putExtra("command", command))
        }
    }


    override fun onCreate() {
        super.onCreate()
        fileDescriptor = createBuilder().establish()
        notificationBuilder = NotificationCompat.Builder(this, Notifications.getDefaultNotificationChannelId(this))
        notificationBuilder.setContentTitle(getString(R.string.app_name))
        notificationBuilder.setSmallIcon(R.mipmap.ic_launcher_round)

        serverUrl = getPreferences().getServerURl()
        secondaryServerUrl = getPreferences().getSecondaryServerURl()

        val text = if (secondaryServerUrl != null) {
            getString(R.string.notification_main_text_with_secondary, serverUrl, secondaryServerUrl)
        } else {
            getString(R.string.notification_main_text, serverUrl)
        }
        notificationBuilder.setStyle(NotificationCompat.BigTextStyle(notificationBuilder).bigText(text))
        updateNotification(0)

        Thread(this).start()
    }

    private fun updateNotification(queryCount:Int) {
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
            }
        }
        return Service.START_STICKY
    }

    private fun destroy() {
        println("Destroying....")
        if (!destroyed) {
            vpnProxy?.stop()
            fileDescriptor?.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(BROADCAST_VPN_INACTIVE))
    }

    private fun createBuilder(): Builder {
        val builder = Builder()

        val dummyServerIpv4 = Preferences.getInstance(this).dummyDnsAddressIpv4()
        val dummyServerIpv6 = Preferences.getInstance(this).dummyDnsAddressIpv6()

        builder.addAddress("192.168.0.10", 24)
        builder.addAddress(NetworkUtil.randomLocalIPv6Address(), 48)
        if (Preferences.getInstance(this).catchKnownDnsServers()) {
            for (server in DnsServerInformation.KNOWN_DNS_SERVERS.values) {
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

        for (defaultBypassPackage in Preferences.getInstance(this).defaultBypassPackages()) {
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

        val serverConfiguration:ServerConfiguration
        var foundConfig = AbstractHttpsDNSHandle.findKnownServerByUrl(serverUrl)
        serverConfiguration = foundConfig?.serverConfigurations?.values?.first() ?: ServerConfiguration.createSimpleServerConfig(serverUrl)
        val secondaryConfig:ServerConfiguration?

        if(!secondaryServerUrl.isNullOrEmpty()) {
            foundConfig = AbstractHttpsDNSHandle.findKnownServerByUrl(serverUrl)
            secondaryConfig = foundConfig?.serverConfigurations?.values?.first() ?: ServerConfiguration.createSimpleServerConfig(secondaryServerUrl!!)
        } else secondaryConfig = null

        val list = mutableListOf<ServerConfiguration>()
        list.add(serverConfiguration)
        if(secondaryConfig != null) list.add(secondaryConfig)

        handle = ProxyHandler(
            list,
            connectTimeout = 500,
            scheduler = SyncScheduler()
        , queryCountCallback = ::updateNotification
        )
        dnsProxy = SmokeProxy(handle!!, this)
        vpnProxy = AsyncVPNTunnelProxy(dnsProxy!!, ThreadScheduler())


        vpnProxy!!.run(fileDescriptor!!)
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

fun AbstractHttpsDNSHandle.Companion.findKnownServerByUrl(url:String): HttpsDnsServerInformation? {
    for (info in AbstractHttpsDNSHandle.KNOWN_DNS_SERVERS.values) {
        for (server in info.servers) {
            if(server.address.getUrl().contains(url, true)) return info
        }
    }
    return null
}

enum class Command : Serializable {
    STOP
}