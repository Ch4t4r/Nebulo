package com.frostnerd.smokescreen.dialog

import android.app.AlertDialog
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.frostnerd.dnstunnelproxy.DEFAULT_DNSERVER_CAPABILITIES
import com.frostnerd.dnstunnelproxy.DnsServerInformation
import com.frostnerd.dnstunnelproxy.TransportProtocol
import com.frostnerd.encrypteddnstunnelproxy.AbstractHttpsDNSHandle
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.tls.AbstractTLSDnsHandle
import com.frostnerd.lifecyclemanagement.BaseDialog
import com.frostnerd.smokescreen.*
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
    context: AppCompatActivity,
    selectedServer:DnsServerInformation<*>?,
    showTls:Boolean = selectedServer?.hasTlsServer() ?: true,
    onEntrySelected: (config: DnsServerInformation<*>) -> Unit
) :
    BaseDialog(context, context.getPreferences().theme.dialogStyle) {
    private var populationJob: Job? = null
    private var currentSelectedServer: DnsServerInformation<*>?
    private lateinit var defaultConfig: List<DnsServerInformation<*>>
    private lateinit var userConfig: List<UserServerConfiguration>

    constructor(context: AppCompatActivity,
                    onEntrySelected: (config: DnsServerInformation<*>) -> Unit):this(context, context.getPreferences().dnsServerConfig, onEntrySelected=onEntrySelected)

    init {
        val view = layoutInflater.inflate(R.layout.dialog_server_configuration, null, false)
        setTitle(R.string.dialog_serverconfiguration_title)
        setView(view)

        currentSelectedServer = selectedServer

        setButton(
            DialogInterface.BUTTON_NEUTRAL, context.getString(android.R.string.cancel)
        ) { _, _ -> }
        setButton(
            DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok)
        ) { _, _ ->
            currentSelectedServer?.apply(onEntrySelected)
        }
        loadServerData(showTls)

        val spinnerAdapter = ArrayAdapter<String>(
            context, android.R.layout.simple_spinner_item,
            arrayListOf(
                context.getString(R.string.dialog_serverconfiguration_https),
                context.getString(R.string.dialog_serverconfiguration_tls)
            )
        )
        spinnerAdapter.setDropDownViewResource(R.layout.item_tasker_action_spinner_dropdown_item)
        val spinner = view.findViewById<Spinner>(R.id.spinner)
        spinner.adapter = spinnerAdapter
        if(showTls) spinner.setSelection(1)
        view.findViewById<RadioGroup>(R.id.knownServersGroup).setOnCheckedChangeListener { group, _ ->
            val button = view.findViewById(group.checkedRadioButtonId) as RadioButton
            val payload = button.tag

            currentSelectedServer = if (payload is UserServerConfiguration) {
                payload.serverInformation
            } else {
                payload as DnsServerInformation<*>
            }
        }
        view.findViewById<Button>(R.id.addServer).setOnClickListener {
            NewServerDialog(context, title = null, dnsOverHttps = spinner.selectedItemPosition == 0, server = null, onServerAdded = { info ->
                val config = createButtonForUserConfiguration(
                    context.getPreferences().addUserServerConfiguration(
                        info
                    )
                )
                if (info.hasTlsServer() == defaultConfig.any { it.hasTlsServer() }) knownServersGroup.addView(
                    config
                )
            }).show()
        }
        addKnownServers {
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    loadServerData(position == 1)
                    knownServersGroup.removeAllViews()
                    addKnownServers()
                }
            }
        }
    }

    private fun loadServerData(tls: Boolean) {
        if (tls) {
            val hiddenServers = context.getPreferences().removedDefaultDoTServers
            defaultConfig = AbstractTLSDnsHandle.waitUntilKnownServersArePopulated { servers ->
                servers.filter {
                    it.key !in hiddenServers
                }.values.toList()
            }
            userConfig = context.getPreferences().userServers.filter {
                !it.isHttpsServer()
            }.toList()
        } else {
            val hiddenServers = context.getPreferences().removedDefaultDoHServers
            defaultConfig = AbstractHttpsDNSHandle.waitUntilKnownServersArePopulated {servers ->
                servers.filter {
                    it.key !in hiddenServers
                }.values.toList()
            }
            userConfig = context.getPreferences().userServers.filter {
                it.isHttpsServer()
            }.toList()
        }
    }

    private fun addKnownServers(then:(() -> Unit)? = null) {
        val previousJob = populationJob
        populationJob = GlobalScope.launch {
            previousJob?.cancel()
            previousJob?.join()
            val hasIpv4 = context.hasDeviceIpv4Address()
            val hasIpv6 = context.hasDeviceIpv6Address()
            val buttons = mutableListOf<RadioButton>()
            defaultConfig.sortedByDescending {
                it.name
            }.filter {
                !it.hasCapability(DEFAULT_DNSERVER_CAPABILITIES.BLOCK_ADS) || !context.resources.getBoolean(
                    R.bool.hide_adblocking_servers
                )
            }.filter {
                it.servers.all { server ->
                    hasIpv4 == (TransportProtocol.IPV4 in server.supportedTransportProtocols)
                            || hasIpv6 == (TransportProtocol.IPV6 in server.supportedTransportProtocols)
                }
            }.forEach {
                buttons.add(0, createButtonForKnownConfiguration(it))
            }
            userConfig.forEach {
                buttons.add(createButtonForUserConfiguration(it))
            }
            launch(Dispatchers.Main) {
                progress.visibility = View.GONE
                for (button in buttons) {
                    knownServersGroup.addView(button)
                }
                markCurrentSelectedServer()
                populationJob = null
                then?.invoke()
            }
        }
    }

    private fun markCurrentSelectedServer() {
        val currentSelectedServer = this.currentSelectedServer ?: return
        for (id in 0 until knownServersGroup.childCount) {
            val child = knownServersGroup.getChildAt(id) as RadioButton
            val payload = child.tag
            val info =
                if (payload is UserServerConfiguration) {
                    payload.serverInformation
                } else {
                    payload as DnsServerInformation<*>
                }
            if (info.hasTlsServer() != currentSelectedServer.hasTlsServer()) continue
            if (info.name != currentSelectedServer.name) continue
            if (info.servers.size < currentSelectedServer.servers.size) continue
            val primaryMatches: Boolean
            val secondaryMatches: Boolean
            if (info.hasTlsServer()) {
                primaryMatches = info.servers[0].address.host == currentSelectedServer.servers[0].address.host
                secondaryMatches =
                    currentSelectedServer.servers.size == 1 || (info.servers[1].address.host == currentSelectedServer.servers[1].address.host)
            } else {
                val httpsInfo = info as HttpsDnsServerInformation
                val currentHttpsInfo = currentSelectedServer as HttpsDnsServerInformation
                primaryMatches =
                    httpsInfo.serverConfigurations.values.first().urlCreator.address.getUrl() == currentHttpsInfo.serverConfigurations.values.first().urlCreator.address.getUrl()
                secondaryMatches = currentHttpsInfo.serverConfigurations.size == 1 ||
                        httpsInfo.serverConfigurations.values.toTypedArray()[1].urlCreator.address.getUrl() == currentHttpsInfo.serverConfigurations.values.toTypedArray()[1].urlCreator.address.getUrl()
            }
            if (primaryMatches && secondaryMatches) {
                child.isChecked = true
                break
            }
        }
    }

    private fun createButtonForKnownConfiguration(info: DnsServerInformation<*>): RadioButton {
        val button = LayoutInflater.from(context).inflate(R.layout.radiobutton, null, false) as RadioButton

        val name = info.name
        val primaryServer: String
        val secondaryServer: String?
        if (info.hasTlsServer()) {
            primaryServer = info.servers[0].address.formatToString()
            secondaryServer = info.servers.getOrNull(1)?.address?.formatToString()
        } else {
            val configs = (info as HttpsDnsServerInformation).servers
            primaryServer = configs[0].address.getUrl(true)
            secondaryServer = configs.getOrNull(1)?.address?.getUrl(true)
        }
        button.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        button.setTextColor(context.getPreferences().theme.getTextColor(context))
        if (secondaryServer == null) button.text = "$name ($primaryServer)"
        else button.text = "$name ($primaryServer, $secondaryServer)"

        button.tag = info
        button.setOnLongClickListener {
            showDefaultConfigDeleteDialog(info, button)
            true
        }
        return button
    }

    private fun createButtonForUserConfiguration(userConfiguration: UserServerConfiguration, reuseButton:RadioButton? = null): RadioButton {
        val button = reuseButton ?: (LayoutInflater.from(context).inflate(R.layout.radiobutton, null, false) as RadioButton).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setTextColor(context.getPreferences().theme.getTextColor(context))
        }

        val info = userConfiguration.serverInformation
        val primaryServer: String
        val secondaryServer: String?
        if (info.hasTlsServer()) {
            primaryServer = info.servers[0].address.formatToString()
            secondaryServer = info.servers.getOrNull(1)?.address?.formatToString()
        } else {
            val configs = (info as HttpsDnsServerInformation).servers
            primaryServer = configs[0].address.getUrl(true)
            secondaryServer = configs.getOrNull(1)?.address?.getUrl(true)
        }


        if (secondaryServer == null) button.text =
            "${userConfiguration.serverInformation.name} ($primaryServer)"
        else button.text =
            "${userConfiguration.serverInformation.name} ($primaryServer, $secondaryServer)"


        button.tag = userConfiguration
        button.setOnLongClickListener {
            AlertDialog.Builder(context, context.getPreferences().theme.dialogStyle)
                .setTitle(R.string.dialog_editdelete_title)
                .setPositiveButton(R.string.dialog_editdelete_edit) { dialog, _ ->
                    showUserConfigEditDialog(userConfiguration, button)
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.dialog_editdelete_delete) { dialog, _ ->
                    showUserConfigDeleteDialog(userConfiguration, button)
                    dialog.dismiss()
                }.show()
            true
        }
        return button
    }

    private fun showUserConfigDeleteDialog(userConfiguration: UserServerConfiguration, button: RadioButton) {
        AlertDialog.Builder(context, context.getPreferences().theme.dialogStyle)
            .setTitle(R.string.dialog_deleteconfig_title)
            .setMessage(
                context.getString(
                    R.string.dialog_deleteconfig_text,
                    userConfiguration.serverInformation.name
                )
            )
            .setNegativeButton(R.string.all_no) { _, _ -> }
            .setPositiveButton(R.string.all_yes) { _, _ ->
                context.getPreferences().removeUserServerConfiguration(userConfiguration)

                if (button.isChecked) {
                    currentSelectedServer =
                        if (userConfiguration.isHttpsServer()) AbstractHttpsDNSHandle.KNOWN_DNS_SERVERS.minBy { it.key }!!.value else AbstractTLSDnsHandle.KNOWN_DNS_SERVERS.minBy { it.key }!!.value
                    markCurrentSelectedServer()
                    context.getPreferences().dnsServerConfig = currentSelectedServer!!
                }
                knownServersGroup.removeView(button)
            }.show()
    }

    private fun showUserConfigEditDialog(userConfiguration: UserServerConfiguration, button: RadioButton) {
        NewServerDialog(context, null, userConfiguration.isHttpsServer(), server = userConfiguration, onServerAdded = {
            context.getPreferences().userServers.toMutableSet().apply {
                remove(userConfiguration)
                context.getPreferences().userServers = this
            }
            val newConfig = context.getPreferences().addUserServerConfiguration(it)
            if(button.isChecked) {
                currentSelectedServer = newConfig.serverInformation
                context.getPreferences().dnsServerConfig = newConfig.serverInformation
            }
            loadServerData(spinner.selectedItemPosition == 1)
            knownServersGroup.removeAllViews()
            addKnownServers()
        }).show()
    }

    private fun showDefaultConfigDeleteDialog(config:DnsServerInformation<*>, button: RadioButton) {
        AlertDialog.Builder(context, context.getPreferences().theme.dialogStyle)
            .setTitle(R.string.dialog_deleteconfig_title)
            .setMessage(
                context.getString(
                    R.string.dialog_deleteconfig_text,
                    config.name
                )
            )
            .setNegativeButton(R.string.all_no) { _, _ -> }
            .setPositiveButton(R.string.all_yes) { _, _ ->
                val isHttps = spinner.selectedItemPosition == 0
                if(isHttps) {
                    context.getPreferences().removedDefaultDoHServers = context.getPreferences().removedDefaultDoHServers + AbstractHttpsDNSHandle.KNOWN_DNS_SERVERS.keys.find {
                        AbstractHttpsDNSHandle.KNOWN_DNS_SERVERS[it] == config
                    }!!
                } else {
                    context.getPreferences().removedDefaultDoTServers = context.getPreferences().removedDefaultDoTServers + AbstractTLSDnsHandle.KNOWN_DNS_SERVERS.keys.find {
                        AbstractTLSDnsHandle.KNOWN_DNS_SERVERS[it] == config
                    }!!
                }

                if (button.isChecked) {
                    currentSelectedServer =
                        if (isHttps) AbstractHttpsDNSHandle.KNOWN_DNS_SERVERS.minBy { it.key }!!.value else AbstractTLSDnsHandle.KNOWN_DNS_SERVERS.minBy { it.key }!!.value
                    markCurrentSelectedServer()
                    context.getPreferences().dnsServerConfig = currentSelectedServer!!
                }
                knownServersGroup.removeView(button)
            }.show()
    }

    override fun destroy() {
        populationJob?.cancel()
    }
}