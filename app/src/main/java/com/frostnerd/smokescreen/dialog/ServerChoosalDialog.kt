package com.frostnerd.smokescreen.dialog

import android.app.AlertDialog
import android.content.DialogInterface
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.frostnerd.dnstunnelproxy.DEFAULT_DNSERVER_CAPABILITIES
import com.frostnerd.dnstunnelproxy.DnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.AbstractHttpsDNSHandle
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.tls.AbstractTLSDnsHandle
import com.frostnerd.lifecyclemanagement.BaseDialog
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.hasTlsServer
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
    onEntrySelected: (config: DnsServerInformation<*>) -> Unit
) :
    BaseDialog(context, context.getPreferences().theme.dialogStyle) {
    private var populationJob: Job? = null
    private var currentSelectedServer: DnsServerInformation<*>
    lateinit var defaultConfig: List<DnsServerInformation<*>>
    lateinit var userConfig: List<UserServerConfiguration>

    init {
        val view = layoutInflater.inflate(R.layout.dialog_server_configuration, null, false)
        setTitle(R.string.dialog_serverconfiguration_title)
        setView(view)

        currentSelectedServer = context.getPreferences().dnsServerConfig

        setButton(
            DialogInterface.BUTTON_NEUTRAL, context.getString(android.R.string.cancel)
        ) { _, _ -> }
        setButton(
            DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok)
        ) { _, _ ->
            onEntrySelected.invoke(currentSelectedServer)
        }
        val isCurrentServerTls = currentSelectedServer.hasTlsServer()
        loadServerData(isCurrentServerTls)

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
        spinner.setSelection(if (isCurrentServerTls) 1 else 0)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadServerData(position == 1)
                knownServersGroup.removeAllViews()
                addKnownServers()
            }
        }
        view.findViewById<RadioGroup>(R.id.knownServersGroup).setOnCheckedChangeListener { group, _ ->
            val button = view.findViewById(group.checkedRadioButtonId) as RadioButton
            val payload = button.tag

            currentSelectedServer = if (payload is UserServerConfiguration) {
                payload.serverInformation
            } else {
                payload as DnsServerInformation<*>
            }
            markCurrentSelectedServer()
        }
        view.findViewById<Button>(R.id.addServer).setOnClickListener {
            NewServerDialog(context, title = null, dnsOverHttps = spinner.selectedItemPosition == 0) { info ->
                val config = createButtonForUserConfiguration(
                    context.getPreferences().addUserServerConfiguration(
                        info
                    )
                )
                if (info.hasTlsServer() == defaultConfig.any { it.hasTlsServer() }) knownServersGroup.addView(
                    config
                )
            }.show()
        }
        addKnownServers()
    }

    private fun loadServerData(tls: Boolean) {
        if (tls) {
            defaultConfig = AbstractTLSDnsHandle.waitUntilKnownServersArePopulated {
                it.values.toList()
            }
            userConfig = context.getPreferences().userServers.filter {
                !it.isHttpsServer()
            }.toList()
        } else {
            defaultConfig = AbstractHttpsDNSHandle.waitUntilKnownServersArePopulated {
                it.values.toList()
            }
            userConfig = context.getPreferences().userServers.filter {
                it.isHttpsServer()
            }.toList()
        }
    }

    private fun addKnownServers() {
        populationJob = GlobalScope.launch {
            val buttons = mutableListOf<RadioButton>()
            defaultConfig.sortedByDescending {
                it.name
            }.filter {
                !it.hasCapability(DEFAULT_DNSERVER_CAPABILITIES.BLOCK_ADS) || !context.resources.getBoolean(
                    R.bool.hide_adblocking_servers
                )
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
            }
        }
    }

    fun markCurrentSelectedServer() {
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
        val button = RadioButton(context)

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
        return button
    }

    private fun createButtonForUserConfiguration(userConfiguration: UserServerConfiguration): RadioButton {
        val button = RadioButton(context)
        button.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        button.setTextColor(context.getPreferences().theme.getTextColor(context))

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
            showUserConfigDeleteDialog(userConfiguration, button)
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
                        if (userConfiguration.isHttpsServer()) AbstractHttpsDNSHandle.KNOWN_DNS_SERVERS[0]!! else AbstractTLSDnsHandle.KNOWN_DNS_SERVERS[0]!!
                    markCurrentSelectedServer()
                    context.getPreferences().dnsServerConfig = currentSelectedServer
                }
                knownServersGroup.removeView(button)
            }.show()
    }

    override fun destroy() {
        populationJob?.cancel()
    }
}