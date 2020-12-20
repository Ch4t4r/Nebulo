package com.frostnerd.smokescreen.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.Window
import com.frostnerd.dnstunnelproxy.DnsServerInformation
import com.frostnerd.dnstunnelproxy.json.DnsServerInformationTypeAdapter
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformationTypeAdapter
import com.frostnerd.encrypteddnstunnelproxy.HttpsUpstreamAddress
import com.frostnerd.encrypteddnstunnelproxy.quic.QuicUpstreamAddress
import com.frostnerd.encrypteddnstunnelproxy.tls.TLSUpstreamAddress
import com.frostnerd.lifecyclemanagement.BaseActivity
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.service.DnsVpnService
import com.frostnerd.smokescreen.toJson
import com.frostnerd.smokescreen.type
import com.frostnerd.smokescreen.util.LanguageContextWrapper
import com.frostnerd.smokescreen.util.ServerType

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
class BackgroundVpnConfigureActivity : BaseActivity() {

    override fun getConfiguration(): Configuration {
        return Configuration.withDefaults()
    }

    companion object {
        const val extraKeyServerConfig = "server_config"
        private const val extraKeyServerType = "config_type"
        private const val VPN_REQUEST_CODE = 1

        fun prepareVpn(context: Context, serverInfo:DnsServerInformation<*>? = null) {
            if(context.getPreferences().runWithoutVpn) DnsVpnService.startVpn(context, serverInfo)
            else {
                val vpnIntent = try {
                    VpnService.prepare(context).apply {
                        this?.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                    }
                } catch (ex:NullPointerException) { // Caused by VpnService.prepare(), maybe Android Bug?
                    Intent()
                }

                if (vpnIntent == null) {
                    DnsVpnService.startVpn(context, serverInfo)
                } else {
                    val intent = Intent(context, BackgroundVpnConfigureActivity::class.java)
                    if(serverInfo != null) {
                        writeServerInfoToIntent(serverInfo, intent)
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        }

        fun writeServerInfoToIntent(info:DnsServerInformation<*>, intent:Intent) {
            intent.putExtra(extraKeyServerConfig, info.toJson())
            intent.putExtra(extraKeyServerType, info.type)
        }

        fun writeServerInfoToIntent(info:DnsServerInformation<*>, bundle:Bundle) {
            bundle.putString(extraKeyServerConfig, info.toJson())
            bundle.putSerializable(extraKeyServerType, info.type)
        }

        fun readServerInfoFromIntent(intent:Intent?):DnsServerInformation<*>? {
            if(intent == null) return null
            if(intent.extras?.containsKey(extraKeyServerConfig) == true) {
                if(intent.extras?.containsKey(extraKeyServerType) == true) {
                    val typeRaw = intent.extras!!.getSerializable(extraKeyServerType)!!
                    val type = (typeRaw.takeIf { it is ServerType } as? ServerType) ?: ServerType.from(typeRaw as String)
                    return serverInfoFromJson(intent.extras!!.getString(extraKeyServerConfig)!!, type)
                }
            }
            return null
        }

        fun readServerInfoFromIntent(bundle:Bundle?):DnsServerInformation<*>? {
            if(bundle == null) return null
            if(bundle.containsKey(extraKeyServerConfig)) {
                if(bundle.containsKey(extraKeyServerType)) {
                    val typeRaw =  bundle.getSerializable(extraKeyServerType)!!
                    val type = (typeRaw.takeIf { it is ServerType } as? ServerType) ?: ServerType.from(typeRaw as String)
                    return serverInfoFromJson(bundle.getString(extraKeyServerConfig)!!, type)
                }
            }
            return null
        }

        private fun serverInfoFromJson(json:String, type:ServerType):DnsServerInformation<*> {
            TLSUpstreamAddress
            HttpsUpstreamAddress
            QuicUpstreamAddress
            return when(type) {
                ServerType.DOH -> HttpsDnsServerInformationTypeAdapter().fromJson(json)
                ServerType.DOT -> DnsServerInformationTypeAdapter().fromJson(json)
                ServerType.DOQ -> DnsServerInformationTypeAdapter().fromJson(json)
        }
    }
}

    private var requestTime: Long = -1

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageContextWrapper.attachFromSettings(this, newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setTheme(getPreferences().theme.dialogStyle)
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        actionBar?.hide()

        val vpnIntent = if(getPreferences().runWithoutVpn) {
            null
        } else {
            VpnService.prepare(this).apply {
                this?.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
        }
        if (vpnIntent == null) {
            startService()
            finish()
        } else {
            requestTime = System.currentTimeMillis()
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                startService()
                finish()
            } else if (resultCode == Activity.RESULT_CANCELED) {
                if (System.currentTimeMillis() - requestTime <= 500) { // Most likely the system denied the request automatically
                    showPermissionDenialDialog()
                } else {
                    finish()
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun startService() {
        DnsVpnService.startVpn(this, readServerInfoFromIntent(intent))
    }

    private fun showPermissionDenialDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this, getPreferences().theme.dialogStyle)
            .setTitle(getString(R.string.app_name) + " - " + getString(R.string.information))
            .setMessage(R.string.dialog_vpn_permission_denied_message)
            .setPositiveButton(R.string.open_app) { _, _ ->
                val intent = PinActivity.openAppIntent(this)
                startActivity(intent)
                finish()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.cancel()
                finish()
            }
            .setOnDismissListener {
                finish()
            }
            .setOnCancelListener {
                finish()
            }.show()
    }
}