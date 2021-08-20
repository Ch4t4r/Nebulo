package com.frostnerd.smokescreen

import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.Color
import android.text.Html
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.AndroidRuntimeException
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import com.frostnerd.dnstunnelproxy.KnownDnsServers
import com.frostnerd.encrypteddnstunnelproxy.AbstractHttpsDNSHandle
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.QuicEngine
import com.frostnerd.encrypteddnstunnelproxy.quic.AbstractQuicDnsHandle
import com.frostnerd.encrypteddnstunnelproxy.quic.QuicUpstreamAddress
import com.frostnerd.encrypteddnstunnelproxy.tls.AbstractTLSDnsHandle
import kotlinx.android.synthetic.main.dialog_privacypolicy.view.*
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.internal.toHexString
import java.net.InetAddress
import java.util.*


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

fun showPrivacyPolicyDialog(context: Context) {
    val dialog = AlertDialog.Builder(context, context.getPreferences().theme.dialogStyle)
    dialog.setTitle(R.string.about_privacypolicy)
    val view = LayoutInflater.from(context).inflate(R.layout.dialog_privacypolicy, null, false)
    dialog.setView(view)
    view.webView.loadUrl("file:///android_asset/privacy_policy.html")
    dialog.setNeutralButton(R.string.all_close, null)
    dialog.show()
}

fun showInfoTextDialogWithClose(
    context: Context,
    title: String,
    text: String,
    withDialog: (AlertDialog.() -> Unit)? = null,
) = showInfoTextDialog(context, title, text, neutralButton = null, positiveButton = context.getString(R.string.all_close) to null, withDialog = withDialog)

fun showInfoTextDialog(context:Context,
                       title:String,
                       text:String,
                       positiveButton:Pair<String, ((DialogInterface, Int) -> Unit)?>? = null,
                       negativeButton:Pair<String, ((DialogInterface, Int) -> Unit)?>? = null,
                       neutralButton:Pair<String, ((DialogInterface, Int) -> Unit)?>? = context.getString(android.R.string.ok) to null,
                       withDialog: (AlertDialog.() -> Unit)? = null,
                        linkifyText:Boolean = false) {
    try {
        val stringWithLinks = SpannableString(text)

        val dialogBuilder = AlertDialog.Builder(context, context.getPreferences().theme.dialogStyle)
                            .setTitle(title)
        if(linkifyText) {
            Linkify.addLinks(stringWithLinks, Linkify.ALL)
            val span = Html.fromHtml(stringWithLinks.toString().replace("\n", "<br>"))
            dialogBuilder.setMessage(span)
        } else {
            dialogBuilder.setMessage(text)
        }

        if(neutralButton != null) dialogBuilder.setNeutralButton(neutralButton.first, neutralButton.second)
        if(positiveButton != null) dialogBuilder.setPositiveButton(positiveButton.first, positiveButton.second)
        if(negativeButton != null) dialogBuilder.setNegativeButton(negativeButton.first, negativeButton.second)

        val dialog = dialogBuilder.show()
        if(linkifyText) {
            val textView = dialog.findViewById<TextView>(android.R.id.message)
            textView?.movementMethod = LinkMovementMethod.getInstance()
            textView?.linksClickable = true
            textView?.setLinkTextColor(Color.parseColor("#64B5F6"))
        }
        withDialog?.invoke(dialog)
    } catch (ex: Exception) {
        if (ex is AndroidRuntimeException ||
            ex.message?.let {
                it.contains("webview", true) ||
                        it.contains("donor package", true)
            } == true
        ) Toast.makeText(context, R.string.error_webview_missing, Toast.LENGTH_LONG)
            .show()
        else throw ex
    }
}

fun isPackageInstalled(context: Context, packageName: String): Boolean {
    val packageManager = context.packageManager
    val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
    val list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return list.size > 0
}

fun colorToHexString(@ColorInt color:Int):String {
    return String.format(Locale.ROOT, "#%06X", 0xFFFFFF and color)
}

@ColorInt
fun opaqueColor(@ColorInt color: Int, opactiy: Int): Int {
    val alpha = opactiy - opactiy % 5
    return Color.parseColor("#" + alpha.toHexString() + colorToHexString(color).replace("#", ""))
}

interface BackpressFragment {
    fun onBackPressed():Boolean
}

fun loadKnownDNSServers() {
    AbstractHttpsDNSHandle // Loads the known servers.
    AbstractTLSDnsHandle
    AbstractQuicDnsHandle
    KnownDnsServers
}

fun createQuicEngineIfInstalled(context: Context, quicOnly:Boolean, vararg addresses: QuicUpstreamAddress): QuicEngine? {
    return if (QuicEngineImpl.providerInstalled) {
        QuicEngineImpl(context, quicOnly, *addresses)
    } else null
}

private fun httpClientWithoutDNS(): OkHttpClient {
    return OkHttpClient.Builder().dns(object: Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return emptyList()
        }
    }).build()
}

fun okhttpClientWithDoh(context: Context, forceDefaultFallback: Boolean = false): OkHttpClient {
    val fallback = context.getPreferences().fallbackDns as HttpsDnsServerInformation?

    if(fallback == null || forceDefaultFallback) {
        val dns = DnsOverHttps.Builder().client(httpClientWithoutDNS())
            .url("https://9.9.9.9/dns-query".toHttpUrl())
            .bootstrapDnsHosts(
                InetAddress.getByName("9.9.9.9"),
                InetAddress.getByName("149.112.112.112"),
                InetAddress.getByName("2620:fe::9"),
                InetAddress.getByName("2620:fe::fe")
            ).build()
        return OkHttpClient.Builder().dns(dns).build()
    } else {
        val url = fallback.servers.first().address.getUrl(false)
        val hasKnownIPAddresses = fallback.servers.first().address.hasStaticAddresses

        val dns = if(hasKnownIPAddresses) {
            val addressCreator = fallback.servers.first().address.addressCreator
            val addresses = addressCreator.resolveOrGetResultOrNull()?.toMutableList() ?: mutableListOf()
            if (addresses.isNullOrEmpty() && addressCreator.hostAddress != null) {
                addresses.add(InetAddress.getByName(addressCreator.hostAddress))
            }

            if (addresses.isNullOrEmpty()) {
                DnsOverHttps.Builder().client(okhttpClientWithDoh(context, true))
                    .url(url.toHttpUrl()).build()
            } else {
                DnsOverHttps.Builder().client(httpClientWithoutDNS()).url(url.toHttpUrl())
                    .bootstrapDnsHosts(addresses)
                    .build()
            }
        } else {
            DnsOverHttps.Builder().client(okhttpClientWithDoh(context, true)).url(url.toHttpUrl()).build()
        }
        return OkHttpClient.Builder().dns(dns).build()
    }
}