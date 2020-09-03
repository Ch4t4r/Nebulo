package com.frostnerd.smokescreen

import android.app.Activity
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.*
import android.hardware.fingerprint.FingerprintManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.frostnerd.dnstunnelproxy.Decision
import com.frostnerd.dnstunnelproxy.DnsServerConfiguration
import com.frostnerd.dnstunnelproxy.DnsServerInformation
import com.frostnerd.dnstunnelproxy.DnsServerInformationTypeAdapter
import com.frostnerd.encrypteddnstunnelproxy.*
import com.frostnerd.encrypteddnstunnelproxy.tls.TLS
import com.frostnerd.encrypteddnstunnelproxy.tls.TLSUpstreamAddress
import com.frostnerd.smokescreen.util.RequestCodes
import com.frostnerd.general.service.isServiceRunning
import com.frostnerd.smokescreen.service.DnsVpnService
import com.frostnerd.smokescreen.util.preferences.AppSettings
import com.frostnerd.smokescreen.util.preferences.AppSettingsSharedPreferences
import com.frostnerd.smokescreen.util.preferences.VpnServiceState
import com.frostnerd.smokescreen.util.preferences.fromSharedPreferences
import com.frostnerd.smokescreen.util.proxy.IpTablesPacketRedirector
import io.sentry.android.core.BuildInfoProvider
import io.sentry.android.core.util.RootChecker
import io.sentry.core.NoOpLogger
import leakcanary.LeakSentry
import java.net.Inet4Address
import java.net.Inet6Address
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

fun Context.canUseFingerprintAuthentication(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
    val mgr = getSystemService(Context.FINGERPRINT_SERVICE) as? FingerprintManager
    if(mgr == null || !mgr.isHardwareDetected) return false
    else if(!mgr.hasEnrolledFingerprints()) return false
    val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    if(!keyguard.isKeyguardSecure) return false
    return true
}

fun Context.registerReceiver(intentFilter: IntentFilter, receiver: (intent: Intent?) -> Unit): BroadcastReceiver {
    val actualReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            receiver(intent)
        }
    }
    this.registerReceiver(actualReceiver, intentFilter)
    return actualReceiver
}

fun Context.startForegroundServiceCompat(intent: Intent) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(intent)
    } else startService(intent)
}

fun Context.tryUnregisterReceiver(receiver: BroadcastReceiver) {
    try {
        unregisterReceiver(receiver)
    } catch (e: Exception) {
    }
}

fun Context.registerReceiver(filteredActions: List<String>, receiver: (intent: Intent?) -> Unit): BroadcastReceiver {
    val filter = IntentFilter()
    for (filteredAction in filteredActions) {
        filter.addAction(filteredAction)
    }

    val actualReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            receiver(intent)
        }
    }
    this.registerReceiver(actualReceiver, filter)
    return actualReceiver
}

fun Context.registerLocalReceiver(intentFilter: IntentFilter, receiver: (intent: Intent?) -> Unit): BroadcastReceiver {
    val actualReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            receiver(intent)
        }
    }
    LocalBroadcastManager.getInstance(this).registerReceiver(actualReceiver, intentFilter)
    return actualReceiver
}

fun Context.registerLocalReceiver(
    filteredActions: List<String>,
    receiver: (intent: Intent?) -> Unit
): BroadcastReceiver {
    val filter = IntentFilter()
    for (filteredAction in filteredActions) {
        filter.addAction(filteredAction)
    }

    val actualReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            receiver(intent)
        }
    }
    LocalBroadcastManager.getInstance(this).registerReceiver(actualReceiver, filter)
    return actualReceiver
}

fun AppCompatActivity.registerLocalReceiver(
    filteredActions: List<String>,
    unregisterOnDestroy:Boolean,
    receiver: (intent: Intent?) -> Unit
): BroadcastReceiver {
    val filter = IntentFilter()
    for (filteredAction in filteredActions) {
        filter.addAction(filteredAction)
    }

    val actualReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            receiver(intent)
        }
    }
    val mgr = LocalBroadcastManager.getInstance(this)
    mgr.registerReceiver(actualReceiver, filter)
    if(unregisterOnDestroy) lifecycle.addObserver(object:LifecycleObserver {
        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        fun onDestroy() {
            mgr.unregisterReceiver(actualReceiver)
        }
    })
    return actualReceiver
}

fun Context.sendLocalBroadcast(intent: Intent) {
    LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
}

fun Context.unregisterLocalReceiver(receiver: BroadcastReceiver?) {
    if(receiver != null) LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
}

fun Context.getPreferences(): AppSettingsSharedPreferences {
    return AppSettings.fromSharedPreferences(this)
}

fun Fragment.getPreferences(): AppSettingsSharedPreferences {
    return AppSettings.fromSharedPreferences(context!!)
}

fun Context.isAppBatteryOptimized(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
    val pwrm = getSystemService(Context.POWER_SERVICE) as PowerManager
    return !pwrm.isIgnoringBatteryOptimizations(packageName)
}

fun <T:Activity>Activity.restart(activityClass:Class<T>? = null, exitProcess:Boolean = false) {
    val intent = (if(activityClass != null) Intent(this, activityClass) else intent)
        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    if(exitProcess) {
        finish()
        val pendingIntent = PendingIntent.getActivity(this, RequestCodes.RESTART_WHOLE_APP, intent, PendingIntent.FLAG_CANCEL_CURRENT)
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).setExact(AlarmManager.RTC, System.currentTimeMillis() + 800, pendingIntent)
        kotlin.system.exitProcess(0)
    } else {
        finish()
        startActivity(intent)
    }
}

fun Context.showEmailChooser(chooserTitle: String, subject: String, recipent: String, text: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", recipent, null))
    intent.putExtra(Intent.EXTRA_SUBJECT, subject)
    intent.putExtra(Intent.EXTRA_EMAIL, recipent)
    intent.putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(intent, chooserTitle))
}

fun ConnectivityManager.isVpnNetwork(network: Network): Boolean {
    val capabilities = getNetworkCapabilities(network)
    return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
}

fun Context.hasDeviceIpv4Address(): Boolean {
    val mgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    var hasNetwork = false
    for (network in mgr.allNetworks) {
        if(network == null) continue
        val info = try {
            mgr.getNetworkInfo(network)
        } catch (ex:NullPointerException) {
            // Android seems to love to throw NullPointerException with getNetworkInfo() - completely out of our control.
            log("Exception when trying to determine IPv4 capability: $ex")
            null
        } ?: continue
        val capabilities = mgr.getNetworkCapabilities(network) ?: continue
        if (info.isConnected && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
            val linkProperties = mgr.getLinkProperties(network) ?: continue
            hasNetwork = true
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
    return !hasNetwork
}

fun Context.hasDeviceIpv6Address(): Boolean {
    val mgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    var hasNetwork = false
    for (network in mgr.allNetworks) {
        if(network == null) continue
        val info =  try {
            mgr.getNetworkInfo(network)
        } catch (ex:NullPointerException) {
            // Android seems to love to throw NullPointerException with getNetworkInfo() - completely out of our control.
            log("Exception when trying to determine IPv6 capability: $ex")
            null
        } ?: continue
        val capabilities = mgr.getNetworkCapabilities(network) ?: continue
        if (info.isConnected && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
            val linkProperties = mgr.getLinkProperties(network) ?: continue
            hasNetwork = true
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
    return !hasNetwork
}

fun Context.isDeviceRooted():Boolean {
    return RootChecker(this, BuildInfoProvider(), NoOpLogger.getInstance()).isDeviceRooted
}

fun Context.clearPreviousIptablesRedirect(forceClear:Boolean = false) {
    if(forceClear || !isServiceRunning(DnsVpnService::class.java) || getPreferences().vpnServiceState == VpnServiceState.STOPPED) {
        val ipv4 = getPreferences().lastIptablesRedirectAddress?.split(":")?.let {
            it[0] to it[1].toInt()
        }
        val ipv6 = getPreferences().lastIptablesRedirectAddressIPv6?.split("]:")?.let {
            it[0].subSequence(1, it[0].length).toString() to it[1].toInt()
        }
        val port = ipv4?.second ?: ipv6?.second ?: return  // Neither IPv4 nor IPv6 present if null
        // Always pass true for disableIpv6IfIp6TablesFails to always drop the rule
        IpTablesPacketRedirector(port, ipv4?.first, ipv6?.first, true, logger).endForward()
        getPreferences().apply {
            edit {
                lastIptablesRedirectAddress = null
                lastIptablesRedirectAddressIPv6 = null
            }
        }
    }
}

operator fun Level.compareTo(otherLevel:Level):Int {
    return this.intValue() - otherLevel.intValue()
}

fun DnsServerInformation<*>.hasTlsServer():Boolean {
    return this.servers.any {
        it.address is TLSUpstreamAddress
    }
}

fun DnsServerInformation<*>.hasHttpsServer():Boolean {
    return this.servers.any {
        it is HttpsUpstreamAddress
    }
}

fun DnsServerInformation<*>.toJson():String {
    return if(hasTlsServer()) {
        DnsServerInformationTypeAdapter().toJson(this)
    } else {
        HttpsDnsServerInformationTypeAdapter().toJson(this as HttpsDnsServerInformation)
    }
}

fun HttpsDnsServerInformation.Companion.fromServerUrls(primaryUrl:String, secondaryUrl:String?): HttpsDnsServerInformation {
    val serverInfo = mutableListOf<HttpsDnsServerConfiguration>()
    val requestType = mapOf(RequestType.WIREFORMAT_POST to ResponseType.WIREFORMAT)
    serverInfo.add(
        HttpsDnsServerConfiguration(address = createHttpsUpstreamAddress(primaryUrl), experimental = false, requestTypes = requestType)
    )
    if(secondaryUrl != null)
        serverInfo.add(
            HttpsDnsServerConfiguration(address = createHttpsUpstreamAddress(secondaryUrl), experimental = false, requestTypes = requestType)
        )
    return HttpsDnsServerInformation(
        "shortcutServer",
        specification = HttpsDnsServerSpecification(
            Decision.UNKNOWN,
            Decision.UNKNOWN,
            Decision.UNKNOWN,
            Decision.UNKNOWN
        ),
        servers = serverInfo,
        capabilities = emptyList()
    )
}

fun tlsServerFromHosts(primaryHost:String, secondaryHost:String?): DnsServerInformation<TLSUpstreamAddress> {
    val serverInfo = mutableListOf<DnsServerConfiguration<TLSUpstreamAddress>>()
    serverInfo.add(
        DnsServerConfiguration(address = createTlsUpstreamAddress(primaryHost), experimental = false, preferredProtocol = TLS, supportedProtocols = listOf(TLS))
    )
    if(secondaryHost != null)
        serverInfo.add(
            DnsServerConfiguration(address = createTlsUpstreamAddress(secondaryHost), experimental = false, preferredProtocol = TLS, supportedProtocols = listOf(TLS))
        )
    return DnsServerInformation(
        "shortcutServer",
        specification = HttpsDnsServerSpecification(
            Decision.UNKNOWN,
            Decision.UNKNOWN,
            Decision.UNKNOWN,
            Decision.UNKNOWN
        ),
        servers = serverInfo,
        capabilities = emptyList()
    )
}

private fun createHttpsUpstreamAddress(url: String): HttpsUpstreamAddress {
    var host = ""
    var port: Int? = null
    var path: String? = null
    if (url.contains(":")) {
        host = url.split(":")[0]
        port = url.split(":")[1].split("/")[0].toInt()
        if (port > 65535) port = null
    }
    if (url.contains("/")) {
        path = url.split("/")[1]
        if (host == "") host = url.split("/")[0]
    }
    if (host == "") host = url
    return if (port != null && path != null) HttpsUpstreamAddress(host, port, path)
    else if (port != null) HttpsUpstreamAddress(host, port)
    else if (path != null) HttpsUpstreamAddress(host, urlPath = path)
    else HttpsUpstreamAddress(host)
}

private fun createTlsUpstreamAddress(host: String): TLSUpstreamAddress {
    var parsedHost = ""
    var port: Int? = null
    if (host.contains(":")) {
        parsedHost = host.split(":")[0]
        port = host.split(":")[1].split("/")[0].toInt()
        if (port > 65535) port = null
    } else parsedHost = host
    return if (port != null) TLSUpstreamAddress(parsedHost, port)
    else TLSUpstreamAddress(parsedHost)
}

fun LeakSentry.watchIfEnabled(watchedInstance: Any) {
    if(BuildConfig.LEAK_DETECTION) {
        refWatcher.watch(watchedInstance)
    }
}

fun LeakSentry.watchIfEnabled(watchedInstance: Any, name:String) {
    if(BuildConfig.LEAK_DETECTION) {
        refWatcher.watch(watchedInstance, name)
    }
}

fun String.equalsAny(vararg options:String, ignoreCase:Boolean = false):Boolean {
    return options.any {
        it.equals(this, ignoreCase)
    }
}

val Context.isPrivateDnsActive: Boolean
    get() = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        false
    } else {
        (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).let {
            if (it.activeNetwork == null) false
            else it.getLinkProperties(it.activeNetwork)?.isPrivateDnsActive ?: false
        }
    }

fun Context.tryOpenBrowser(withLink:String) {
    try {
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(withLink)
            )
        )
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, R.string.error_no_webbrowser_installed, Toast.LENGTH_LONG).show()
    }
}