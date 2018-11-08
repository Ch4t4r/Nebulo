package com.frostnerd.smokescreen.dialog

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import co.metalab.asyncawait.async
import com.frostnerd.dnstunnelproxy.DEFAULT_DNSERVER_CAPABILITIES
import com.frostnerd.encrypteddnstunnelproxy.*
import com.frostnerd.lifecyclemanagement.BaseDialog
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.database.DatabaseHelper
import com.frostnerd.smokescreen.database.entities.UserServerConfiguration
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.getPreferences
import kotlinx.android.synthetic.main.dialog_server_configuration.*

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

        setOnShowListener {_ ->
            addKnownServers()

            addServer.setOnClickListener {
                NewServerDialog(context) { name, primaryServer, secondaryServer:String? ->
                    val userServerConfiguration = UserServerConfiguration(name, primaryServer, secondaryServer)
                    context.getDatabase().insert(userServerConfiguration)
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
        async {
            val buttons = mutableListOf<RadioButton>()
            await {
                for ((name, serverInfo) in AbstractHttpsDNSHandle.KNOWN_DNS_SERVERS) {
                    if (!serverInfo.hasCapability(DEFAULT_DNSERVER_CAPABILITIES.BLOCK_ADS)) {
                        buttons.add(0, createButtonForKnownConfiguration(name, serverInfo))
                    }
                }
                context.getDatabase().getAll(UserServerConfiguration::class.java).forEach {
                    buttons.add(createButtonForUserConfiguration(it))
                }
            }

            progress.visibility = View.GONE
            for (button in buttons) {
                knownServersGroup.addView(button)
            }
            checkCurrentConfiguration()
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
            showUserConfigDeleteDialog(userConfiguration)
            true
        }
        return button
    }

    private fun showUserConfigDeleteDialog(userConfiguration: UserServerConfiguration) {
        AlertDialog.Builder(context, context.getPreferences().theme.dialogStyle)
            .setTitle(R.string.dialog_deleteconfig_title)
            .setMessage(context.getString(R.string.dialog_deleteconfig_text, userConfiguration.name))
            .setNegativeButton(R.string.all_no) { _, _ -> }
            .setPositiveButton(R.string.all_yes) { _, _ ->
                context.getDatabase().delete(userConfiguration)
                knownServersGroup.removeAllViews()

                if(primaryServer.urlCreator.baseUrl == userConfiguration.primaryServerUrl) {
                    // TODO Reset to default.
                }

                progress.visibility = View.VISIBLE
                addKnownServers()
            }.show()
    }

    private fun checkCurrentConfiguration() {
        println(knownServersGroup.childCount)
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

                println(primaryCandidate == primaryServer)
                println(secondaryCandidate)
                if(primaryCandidate == primaryServer && secondaryCandidate == secondaryServer) {
                    child.isChecked = true
                    break
                }
            }
        }
    }

    override fun destroy() {

    }
}