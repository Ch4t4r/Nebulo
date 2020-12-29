package com.frostnerd.smokescreen.tasker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.quic.QuicUpstreamAddress
import com.frostnerd.lifecyclemanagement.BaseActivity
import com.frostnerd.smokescreen.*
import com.frostnerd.smokescreen.activity.BackgroundVpnConfigureActivity
import com.frostnerd.smokescreen.dialog.NewServerDialog
import com.frostnerd.smokescreen.dialog.ServerChoosalDialog
import com.frostnerd.smokescreen.util.ServerType
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.android.synthetic.main.activity_tasker_configure.*

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
class ConfigureActivity : BaseActivity() {
    private val type:ServerType
    get() {
        return when(serverType.selectedItemPosition) {
            0 -> ServerType.DOH
            1 -> ServerType.DOT
            2 -> ServerType.DOQ
            else -> error("")
        }
    }

    override fun getConfiguration(): Configuration {
        return Configuration.withDefaults()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasker_configure)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        createLayout()
        applyOldConfiguration()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            saveConfiguration()
            finish()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun applyOldConfiguration() {
        if (intent != null && intent.extras != null) {
            val settings = intent.extras!!.getBundle(TaskerHelper.EXTRAS_BUNDLE_KEY)
            if (settings != null) {
                when (settings.getString(TaskerHelper.DATA_KEY_ACTION, "start")) {
                    "stop" -> {
                        actionType.setSelection(1)
                    }
                    "start" -> {
                        actionType.setSelection(0)
                        startIfRunning.isChecked = settings.getBoolean(TaskerHelper.DATA_KEY_STARTIFRUNNING, true)
                        useCustomServer.isChecked =
                            settings.containsKey(TaskerHelper.DATA_KEY_PRIMARYSERVER) || settings.containsKey(
                                BackgroundVpnConfigureActivity.extraKeyServerConfig
                            )
                        if (useCustomServer.isChecked) {
                            if (settings.containsKey(TaskerHelper.DATA_KEY_PRIMARYSERVER)) {
                                serverType.setSelection(0)
                                primaryServer.setText(settings.getString(TaskerHelper.DATA_KEY_PRIMARYSERVER))
                                secondaryServer.setText(settings.getString(TaskerHelper.DATA_KEY_SECONDARYSERVER))
                            } else {
                                val info = BackgroundVpnConfigureActivity.readServerInfoFromIntent(settings)
                                if (info != null) {
                                    when(info.type) {
                                        ServerType.DOH -> {
                                            serverType.setSelection(0)
                                            val httpsInfo = info as HttpsDnsServerInformation
                                            val configs = httpsInfo.serverConfigurations.values.toTypedArray()
                                            primaryServer.setText(configs.getOrNull(0)?.urlCreator?.address?.getUrl(true))
                                            secondaryServer.setText(configs.getOrNull(1)?.urlCreator?.address?.getUrl(true))
                                        }
                                        ServerType.DOT -> {
                                            serverType.setSelection(1)
                                            primaryServer.setText(info.servers.getOrNull(0)?.address?.host)
                                            secondaryServer.setText(info.servers.getOrNull(1)?.address?.host)
                                        }
                                        ServerType.DOQ -> {
                                            serverType.setSelection(2)
                                            primaryServer.setText(info.servers.getOrNull(0)?.address?.host)
                                            secondaryServer.setText((info.servers.getOrNull(1)?.address as? QuicUpstreamAddress)?.getUrl(true))
                                        }
                                    }
                                }
                            }
                        }
                        setHints()
                    }
                }
            }
        }
    }

    private fun createLayout() {
        useCustomServer.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                serverConfigWrap.visibility = View.GONE
                serverType.visibility = View.GONE
            } else {
                serverType.visibility = View.VISIBLE
                serverConfigWrap.visibility = View.VISIBLE
            }
        }
        val adapter: ArrayAdapter<CharSequence> = ArrayAdapter.createFromResource(
            this,
            R.array.tasker_action_values,
            R.layout.item_tasker_action_spinner_item
        )
        adapter.setDropDownViewResource(R.layout.item_tasker_action_spinner_dropdown_item)
        actionType.adapter = adapter
        actionType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) {
                    startConfigWrap.visibility = View.VISIBLE
                } else {
                    startConfigWrap.visibility = View.GONE
                }
            }
        }
        val typeAdapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            arrayListOf(
                this.getString(R.string.dialog_serverconfiguration_https),
                this.getString(R.string.dialog_serverconfiguration_tls),
                this.getString(R.string.dialog_serverconfiguration_quic)
            ).let {
                if(BuildConfig.SHOW_DOQ) it
                else it.subList(0, 2)
            }
        )
        typeAdapter.setDropDownViewResource(R.layout.item_tasker_action_spinner_dropdown_item)
        serverType.adapter = typeAdapter
        serverType.setSelection(0)
        serverType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                primaryServer.text = primaryServer.text
                secondaryServer.text = secondaryServer.text
                setHints()
            }
        }
        selectServer.setOnClickListener {
            ServerChoosalDialog(this@ConfigureActivity, null, ServerType.from(serverType.selectedItemPosition)) {
                val typePosition = when(it.type) {
                    ServerType.DOH -> 0
                    ServerType.DOT -> 1
                    ServerType.DOQ -> 2
                }
                if(serverType.selectedItemPosition != typePosition) serverType.setSelection(typePosition)
                primaryServer.setText(it.servers.first().address.formatToString())
                if(it.servers.size > 1) secondaryServer.setText(it.servers.last().address.formatToString())
                else secondaryServer.setText("")
            }.show()
        }
        addUrlTextWatcher(primaryServerWrap, primaryServer, false)
        addUrlTextWatcher(secondaryServerWrap, secondaryServer, true)
        setHints()
    }

    private fun setHints() {
        val type = ServerType.from(serverType.selectedItemPosition)
        primaryServer.hint = if(primaryServer.hasFocus() || primaryServerWrap.error != null) {
            when(type) {
                ServerType.DOH -> getString(R.string.dialog_newserver_primaryserver_hint)
                ServerType.DOT -> getString(R.string.dialog_newserver_primaryserver_hint_dot)
                ServerType.DOQ -> getString(R.string.dialog_newserver_primaryserver_hint_doq)
            }
        } else null
        secondaryServer.hint = if(secondaryServer.hasFocus()) {
            when(type) {
                ServerType.DOH -> getString(R.string.dialog_newserver_secondaryserver_hint)
                ServerType.DOT -> getString(R.string.dialog_newserver_secondaryserver_hint_dot)
                ServerType.DOQ -> getString(R.string.dialog_newserver_secondaryserver_hint_doq)
            }
        } else null
    }

    private fun addUrlTextWatcher(input: TextInputLayout, editText: TextInputEditText, emptyAllowed: Boolean) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                var valid = (emptyAllowed && s.isBlank())
                valid = valid || (!s.isBlank() && (type == ServerType.DOH || type == ServerType.DOQ) && NewServerDialog.isValidDoH(s.toString()))
                valid = valid || (!s.isBlank() && (type == ServerType.DOT) && NewServerDialog.isValidDot(s.toString()))

                input.error = if (valid) {
                    null
                } else getString(R.string.error_invalid_url)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

        })
        editText.setOnFocusChangeListener { _, _ ->
            setHints()
        }
    }

    override fun onBackPressed() {
        saveConfiguration()
        super.onBackPressed()
    }

    private fun saveConfiguration() {
        var valid = true
        val action = if (actionType.selectedItemPosition == 0) "start" else "stop"
        val resultIntent = Intent()
        val settings = Bundle()
        settings.putString(TaskerHelper.DATA_KEY_ACTION, action)
        resultIntent.putExtra(
            TaskerHelper.EXTRAS_BLURB_KEY,
            resources.getStringArray(R.array.tasker_action_values)[actionType.selectedItemPosition]
        )
        if (action == "start") {
            settings.putBoolean(TaskerHelper.DATA_KEY_STARTIFRUNNING, startIfRunning.isChecked)
            if (useCustomServer.isChecked) {
                if (primaryServerWrap.error == null &&
                    secondaryServerWrap.error == null
                ) {
                    var primary = primaryServer.text.toString()
                    var secondary = if (secondaryServer.text.isNullOrBlank()) null else secondaryServer.text.toString()
                    if (primary.startsWith("https")) primary = primary.replace("https://", "")
                    if (secondary != null && secondary.startsWith("https")) secondary =
                        secondary.replace("https://", "")
                    val mode:String

                    if (serverType.selectedItemPosition == 1) {
                        mode = getString(R.string.tasker_mode_dot)
                        BackgroundVpnConfigureActivity.writeServerInfoToIntent(
                            tlsServerFromHosts(
                                primary,
                                secondary
                            ), settings
                        )
                    } else {
                        mode = getString(R.string.tasker_mode_doh)
                        BackgroundVpnConfigureActivity.writeServerInfoToIntent(
                            HttpsDnsServerInformation.fromServerUrls(
                                primary,
                                secondary
                            ), settings
                        )
                    }

                    if (secondary != null) {
                        resultIntent.putExtra(
                            TaskerHelper.EXTRAS_BLURB_KEY,
                            getString(R.string.tasker_start_app_custom_urls, primary, secondary, mode)
                        )
                    } else {
                        resultIntent.putExtra(
                            TaskerHelper.EXTRAS_BLURB_KEY,
                            getString(R.string.tasker_start_app_custom_url, primary, mode)
                        )
                    }
                    settings.putBoolean(TaskerHelper.DATA_KEY_STARTIFRUNNING, startIfRunning.isChecked)
                } else {
                    valid = false
                }
            }
        }
        if (valid) {
            resultIntent.putExtra(TaskerHelper.EXTRAS_BUNDLE_KEY, settings)
            setResult(Activity.RESULT_OK, resultIntent)
        } else setResult(Activity.RESULT_CANCELED)
    }
}