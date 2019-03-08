package com.frostnerd.smokescreen.dialog.serverchoosaldialog

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import com.frostnerd.dnstunnelproxy.DEFAULT_DNSERVER_CAPABILITIES
import com.frostnerd.dnstunnelproxy.DnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.AbstractHttpsDNSHandle
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerConfiguration
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.ServerConfiguration
import com.frostnerd.lifecyclemanagement.BaseDialog
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.dialog.NewServerDialog
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.util.preferences.UserServerConfiguration
import kotlinx.android.synthetic.main.dialog_server_configuration.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
class ServerChoosalDialog(
    context: Context,
    onEntrySelected: (config:DnsServerInformation<*>, customServer: Boolean) -> Unit
) :
    BaseDialog(context, context.getPreferences().theme.dialogStyle) {
    private var populationJob: Job? = null
    private var customServers = false
    private var server: DnsServerInformation<*>

    init {
        val view = layoutInflater.inflate(R.layout.dialog_server_configuration, null, false)
        setTitle(R.string.dialog_title_serverconfiguration)
        setView(view)

        customServers = context.getPreferences().areCustomServers
        server = context.getPreferences().dnsServerConfig

        setButton(
            DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.cancel)
        ) { _, _ -> }
        setButton(
            DialogInterface.BUTTON_POSITIVE, context.getString(R.string.ok)
        ) { _, _ ->
            onEntrySelected.invoke(server, customServers)
        }

        setOnShowListener {
            addKnownServers()

            addServer.setOnClickListener {
                NewServerDialog(context, title = null) { info ->
                    knownServersGroup.addView(
                        createButtonForUserConfiguration(
                            context.getPreferences().addUserServerConfiguration(
                                info
                            )
                        )
                    )
                }.show()
            }

        }
    }

    private fun checkCurrentConfiguration() {
        for (id: Int in 0 until knownServersGroup.childCount) {
            val child = knownServersGroup.getChildAt(id) as RadioButton
            val payload = child.tag
            if (payload is UserServerConfiguration) {
                if (!customServers) continue
                if (primaryServer.urlCreator.baseUrl != payload.serverInformation.servers.first().createServerConfiguration(
                        payload.serverInformation
                    ).urlCreator.baseUrl
                ) continue
                if (secondaryServer?.urlCreator?.baseUrl != payload.serverInformation.servers.getOrNull(1)?.createServerConfiguration(
                        payload.serverInformation
                    )?.urlCreator?.baseUrl
                ) continue
                child.isChecked = true
                break
            } else {
                val configs = payload as Set<HttpsDnsServerConfiguration>
                val primaryCandidate = configs.first().createServerConfiguration()
                val secondaryCandidate = if (configs.size > 1) {
                    configs.last().createServerConfiguration()
                } else {
                    null
                }

                if (primaryCandidate == primaryServer && secondaryCandidate == secondaryServer) {
                    child.isChecked = true
                    break
                }
            }
        }
    }

    override fun destroy() {
        populationJob?.cancel()
    }
}