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
import com.frostnerd.smokescreen.database.entities.UserServerConfiguration
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.getPreferences
import kotlinx.android.synthetic.main.dialog_server_configuration.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */
class ServerChoosalDialog(context: Context, onEntrySelected: (primaryServer:ServerConfiguration, secondaryServer:ServerConfiguration?, customServer:Boolean) -> Unit) :
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
                NewServerDialog(context) { name, primaryServer, secondaryServer:String? ->
                    val userServerConfiguration = UserServerConfiguration(name=name, primaryServerUrl = primaryServer, secondaryServerUrl = secondaryServer)
                    context.getDatabase().userServerConfigurationDao().insert(userServerConfiguration)
                    knownServersGroup.addView(createButtonForUserConfiguration(userServerConfiguration))
                }.show()
            }
            knownServersGroup.setOnCheckedChangeListener { group, _ ->
                val button = view.findViewById(group.checkedRadioButtonId) as RadioButton
                val payload = button.tag

                if(payload is UserServerConfiguration) {
                    customServers = true
                    primaryServer = ServerConfiguration.createSimpleServerConfig(payload.primaryServerUrl)
                    secondaryServer = if(payload.secondaryServerUrl != null) {
                        ServerConfiguration.createSimpleServerConfig(payload.secondaryServerUrl!!)
                    } else null
                } else {
                    val configs = payload as Set<HttpsDnsServerConfiguration>
                    customServers = false
                    primaryServer = configs.first().createServerConfiguration()
                    secondaryServer = if(configs.size > 1) {
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
            for ((_, serverInfo) in AbstractHttpsDNSHandle.KNOWN_DNS_SERVERS.toSortedMap(compareByDescending {
                AbstractHttpsDNSHandle.KNOWN_DNS_SERVERS[it]!!.name
            })) {
                if (!serverInfo.hasCapability(DEFAULT_DNSERVER_CAPABILITIES.BLOCK_ADS) || !context.resources.getBoolean(R.bool.hide_adblocking_servers)) {
                    buttons.add(0, createButtonForKnownConfiguration(serverInfo.name, serverInfo))
                }
            }
            context.getDatabase().userServerConfigurationDao().getAll().forEach {
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

    private fun createButtonForKnownConfiguration(name:String, serverInfo:HttpsDnsServerInformation): RadioButton {
        val button = RadioButton(context)
        val configs = serverInfo.serverConfigurations.keys

        button.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        button.setTextColor(context.getPreferences().theme.getTextColor(context))
        if(configs.size == 1) button.text = "$name (${configs.first().address.FQDN})"
        else button.text = "$name (${configs.first().address.FQDN}, ${configs.last().address.FQDN})"

        button.tag = configs
        return button
    }

    private fun createButtonForUserConfiguration(userConfiguration:UserServerConfiguration):RadioButton {
        val button = RadioButton(context)
        button.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        button.setTextColor(context.getPreferences().theme.getTextColor(context))

        if(userConfiguration.secondaryServerUrl == null) button.text = "${userConfiguration.name} (${userConfiguration.primaryServerUrl})"
        else button.text = "${userConfiguration.name} (${userConfiguration.primaryServerUrl}, ${userConfiguration.secondaryServerUrl})"

        button.tag = userConfiguration
        button.setOnLongClickListener {
            showUserConfigDeleteDialog(userConfiguration, button)
            true
        }
        return button
    }

    private fun showUserConfigDeleteDialog(userConfiguration: UserServerConfiguration, button:RadioButton) {
        AlertDialog.Builder(context, context.getPreferences().theme.dialogStyle)
            .setTitle(R.string.dialog_deleteconfig_title)
            .setMessage(context.getString(R.string.dialog_deleteconfig_text, userConfiguration.name))
            .setNegativeButton(R.string.all_no) { _, _ -> }
            .setPositiveButton(R.string.all_yes) { _, _ ->
                context.getDatabase().userServerConfigurationDao().delete(userConfiguration)

                if(button.isChecked) {
                    context.getPreferences().primaryServerConfig = AbstractHttpsDNSHandle.KNOWN_DNS_SERVERS[0]!!.serverConfigurations.values.first()
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
        for (id:Int in 0 until knownServersGroup.childCount) {
            val child = knownServersGroup.getChildAt(id) as RadioButton
            val payload = child.tag
            if(payload is UserServerConfiguration) {
                if(!customServers) continue
                if(primaryServer.urlCreator.baseUrl != payload.primaryServerUrl) continue
                if(secondaryServer?.urlCreator?.baseUrl != payload.secondaryServerUrl) continue
                child.isChecked = true
                break
            } else {
                val configs = payload as Set<HttpsDnsServerConfiguration>
                val primaryCandidate = configs.first().createServerConfiguration()
                val secondaryCandidate = if(configs.size > 1) {
                    configs.last().createServerConfiguration()
                } else {
                    null
                }

                if(primaryCandidate == primaryServer && secondaryCandidate == secondaryServer) {
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