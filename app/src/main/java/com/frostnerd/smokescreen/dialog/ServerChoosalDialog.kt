package com.frostnerd.smokescreen.dialog

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import com.frostnerd.dnstunnelproxy.DEFAULT_DNSERVER_CAPABILITIES
import com.frostnerd.encrypteddnstunnelproxy.*
import com.frostnerd.lifecyclemanagement.BaseDialog
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.database.getDatabase
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
    onEntrySelected: (primaryServer: ServerConfiguration, secondaryServer: ServerConfiguration?, customServer: Boolean) -> Unit
) :
    BaseDialog(context, context.getPreferences().theme.dialogStyle) {
    private var populationJob: Job? = null
    private var customServers = false
    private var primaryServer: ServerConfiguration
    private var secondaryServer: ServerConfiguration? = null

    init {
        val view = layoutInflater.inflate(R.layout.dialog_server_configuration, null, false)
        setTitle(R.string.dialog_title_serverconfiguration)
        setView(view)

        customServers = context.getPreferences().areCustomServers
        primaryServer = context.getPreferences().primaryServerConfig
        secondaryServer = context.getPreferences().secondaryServerConfig

        setButton(
            DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.cancel)
        ) { _, _ -> }
        setButton(
            DialogInterface.BUTTON_POSITIVE, context.getString(R.string.ok)
        ) { _, _ ->
            onEntrySelected.invoke(primaryServer, secondaryServer, customServers)
        }

        setOnShowListener {
            addKnownServers()

            addServer.setOnClickListener {
                NewServerDialog(context) {info ->
                    knownServersGroup.addView(
                        createButtonForUserConfiguration(
                            context.getPreferences().addUserServerConfiguration(
                                info
                            )
                        )
                    )
                }.show()
            }
            knownServersGroup.setOnCheckedChangeListener { group, _ ->
                val button = view.findViewById(group.checkedRadioButtonId) as RadioButton
                val payload = button.tag

                if (payload is UserServerConfiguration) {
                    customServers = true
                    primaryServer = payload.serverInformation.servers.first()
                        .createServerConfiguration(payload.serverInformation)
                    secondaryServer = payload.serverInformation.servers.getOrNull(1)
                        ?.createServerConfiguration(payload.serverInformation)
                } else {
                    val configs = payload as Set<HttpsDnsServerConfiguration>
                    customServers = false
                    primaryServer = configs.first().createServerConfiguration()
                    secondaryServer = if (configs.size > 1) {
                        configs.last().createServerConfiguration()
                    } else {
                        null
                    }
                }
            }
        }
    }

    private fun addKnownServers() {
        populationJob = GlobalScope.launch {
            val buttons = mutableListOf<RadioButton>()
            AbstractHttpsDNSHandle.waitUntilKnownServersArePopulated { knownServers ->
                for ((_, serverInfo) in knownServers.toSortedMap(compareByDescending {
                    knownServers[it]!!.name
                })) {
                    if (!serverInfo.hasCapability(DEFAULT_DNSERVER_CAPABILITIES.BLOCK_ADS) || !context.resources.getBoolean(
                            R.bool.hide_adblocking_servers
                        )
                    ) {
                        buttons.add(0, createButtonForKnownConfiguration(serverInfo.name, serverInfo))
                    }
                }
            }
            context.getPreferences().userServers.forEach {
                buttons.add(createButtonForUserConfiguration(it))
            }
            launch(Dispatchers.Main) {
                progress.visibility = View.GONE
                for (button in buttons) {
                    knownServersGroup.addView(button)
                }
                checkCurrentConfiguration()
                populationJob = null
            }
        }
    }

    private fun createButtonForKnownConfiguration(name: String, serverInfo: HttpsDnsServerInformation): RadioButton {
        val button = RadioButton(context)
        val configs = serverInfo.serverConfigurations.keys

        button.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        button.setTextColor(context.getPreferences().theme.getTextColor(context))
        if (configs.size == 1) button.text = "$name (${configs.first().address.getUrl(true)})"
        else button.text = "$name (${configs.first().address.FQDN}, ${configs.last().address.getUrl(true)})"

        button.tag = configs
        return button
    }

    private fun createButtonForUserConfiguration(userConfiguration: UserServerConfiguration): RadioButton {
        val button = RadioButton(context)
        button.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        button.setTextColor(context.getPreferences().theme.getTextColor(context))

        val primaryConfig = userConfiguration.serverInformation.servers.first()
        val secondaryConfig = userConfiguration.serverInformation.servers.getOrNull(1)

        if (secondaryConfig == null) button.text =
                "${userConfiguration.serverInformation.name} (${primaryConfig.address.getUrl(true)})"
        else button.text =
                "${userConfiguration.serverInformation.name} (${primaryConfig.address.getUrl(true)}, ${secondaryConfig.address.getUrl()})"


        button.tag = userConfiguration
        button.setOnLongClickListener {
            showUserConfigDeleteDialog(userConfiguration, button)
            true
        }
        return button
    }

    private fun showUserConfigDeleteDialog(userConfiguration: UserServerConfiguration, button: RadioButton) {
        AlertDialog.Builder(context, context.getPreferences().theme.dialogStyle)
            .setTitle(R.string.dialog_deleteconfig_title)
            .setMessage(context.getString(R.string.dialog_deleteconfig_text, userConfiguration.serverInformation.name))
            .setNegativeButton(R.string.all_no) { _, _ -> }
            .setPositiveButton(R.string.all_yes) { _, _ ->
                context.getPreferences().removeUserServerConfiguration(userConfiguration)

                if (button.isChecked) {
                    context.getPreferences().primaryServerConfig =
                            AbstractHttpsDNSHandle.KNOWN_DNS_SERVERS[0]!!.serverConfigurations.values.first()
                    primaryServer = context.getPreferences().primaryServerConfig

                    val config = AbstractHttpsDNSHandle.KNOWN_DNS_SERVERS[0]!!.serverConfigurations.values.last()
                    if (config != primaryServer) context.getPreferences().secondaryServerConfig = config

                    secondaryServer = context.getPreferences().secondaryServerConfig
                    customServers = false
                }
                knownServersGroup.removeView(button)
            }.show()
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