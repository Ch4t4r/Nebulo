package com.frostnerd.smokescreen.dialog

import android.content.Context
import android.content.DialogInterface
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import com.frostnerd.dnstunnelproxy.Decision
import com.frostnerd.dnstunnelproxy.DnsServerConfiguration
import com.frostnerd.dnstunnelproxy.DnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.*
import com.frostnerd.encrypteddnstunnelproxy.tls.AbstractTLSDnsHandle
import com.frostnerd.encrypteddnstunnelproxy.tls.TLS
import com.frostnerd.encrypteddnstunnelproxy.tls.TLSUpstreamAddress
import com.frostnerd.lifecyclemanagement.BaseDialog
import com.frostnerd.materialedittext.MaterialEditText
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.log
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.android.synthetic.main.dialog_new_server.*
import kotlinx.android.synthetic.main.dialog_new_server.view.*

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
class NewServerDialog(
    context: Context,
    title: String? = null,
    var dnsOverHttps: Boolean,
    onServerAdded: (serverInfo: DnsServerInformation<*>) -> Unit
) : BaseDialog(context, context.getPreferences().theme.dialogStyle) {
    private var validationRegex = NewServerDialog.SERVER_URL_REGEX

    companion object {
        val SERVER_URL_REGEX =
            Regex(
                "^\\s*(?:https://)?([a-z0-9][a-z0-9-.]*[a-z0-9])(?::[1-9][0-9]{0,4})?(/[a-z0-9-.]+)*(/)?\\s*$",
                RegexOption.IGNORE_CASE
            )
        val TLS_REGEX = Regex("^\\s*([a-z0-9][a-z0-9-.]*[a-z0-9])(?::[1-9][0-9]{0,4})?\\s*$", RegexOption.IGNORE_CASE)
    }

    init {
        val view = layoutInflater.inflate(R.layout.dialog_new_server, null, false)
        setHintAndTitle(view,dnsOverHttps, title)
        setView(view)

        setButton(
            DialogInterface.BUTTON_NEUTRAL, context.getString(android.R.string.cancel)
        ) { _, _ -> }

        setButton(
            DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok)
        ) { _, _ -> }

        setOnShowListener {
            addUrlTextWatcher(primaryServerWrap, primaryServer, false)
            addUrlTextWatcher(secondaryServerWrap, secondaryServer, true)
            serverName.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    serverNameWrap.error = if (s.isNullOrBlank()) context.getString(R.string.error_invalid_servername)
                    else null
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }
            })
            serverNameWrap.error = context.getString(R.string.error_invalid_servername)
            getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                if (inputsValid()) {
                    val name = serverName.text.toString()
                    var primary = primaryServer.text.toString().trim()
                    var secondary =
                        if (secondaryServer.text.isNullOrBlank()) null else secondaryServer.text.toString().trim()

                    if (primary.startsWith("https")) primary = primary.replace("https://", "")
                    if (secondary != null && secondary.startsWith("https")) secondary =
                        secondary.replace("https://", "")
                    invokeCallback(name, primary, secondary, onServerAdded)
                    dismiss()
                } else {
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    vibrator.vibrate(250)
                }
            }
            val spinnerAdapter = ArrayAdapter<String>(
                context, android.R.layout.simple_spinner_item,
                arrayListOf(
                    context.getString(R.string.dialog_serverconfiguration_https),
                    context.getString(R.string.dialog_serverconfiguration_tls)
                )
            )
            spinnerAdapter.setDropDownViewResource(R.layout.item_tasker_action_spinner_dropdown_item)
            serverType.adapter = spinnerAdapter
            serverType.setSelection(if (dnsOverHttps) 0 else 1)
            serverType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                    validationRegex = if (position == 0) NewServerDialog.SERVER_URL_REGEX else NewServerDialog.TLS_REGEX
                    dnsOverHttps = position == 0
                    setHintAndTitle(view, dnsOverHttps, title)
                    primaryServer.text = primaryServer.text
                    secondaryServer.text = secondaryServer.text
                }
            }
        }
    }

    private fun setHintAndTitle(view:View, dnsOverHttps: Boolean, titleOverride:String?) {
        if (dnsOverHttps) {
            if(titleOverride == null) setTitle(R.string.dialog_newserver_title_https)
            view.primaryServer.setHint(R.string.dialog_newserver_primaryserver_hint)
            view.secondaryServer.apply {
                if(isFocused || error != null) setHint(R.string.dialog_newserver_secondaryserver_hint)
            }
        }else {
            if(titleOverride == null) setTitle(R.string.dialog_newserver_title_tls)
            view.primaryServer.setHint(R.string.dialog_newserver_primaryserver_hint_dot)
            view.secondaryServer.apply {
                if(isFocused || error != null) setHint(R.string.dialog_newserver_secondaryserver_hint_dot)
            }
        }
        if(titleOverride != null) setTitle(titleOverride)
    }

    private fun invokeCallback(
        name: String,
        primary: String,
        secondary: String?,
        onServerAdded: (DnsServerInformation<*>) -> Unit
    ) {
        if (dnsOverHttps) {
            val requestType = mapOf(RequestType.WIREFORMAT_POST to ResponseType.WIREFORMAT)
            val serverInfo = mutableListOf<HttpsDnsServerConfiguration>()
            serverInfo.add(
                HttpsDnsServerConfiguration(
                    address = createHttpsUpstreamAddress(primary),
                    requestTypes = requestType,
                    experimental = false
                )
            )
            if (!secondary.isNullOrBlank()) serverInfo.add(
                HttpsDnsServerConfiguration(
                    address = createHttpsUpstreamAddress(
                        secondary
                    ), requestTypes = requestType, experimental = false
                )
            )
            onServerAdded.invoke(
                HttpsDnsServerInformation(
                    name,
                    HttpsDnsServerSpecification(
                        Decision.UNKNOWN,
                        Decision.UNKNOWN,
                        Decision.UNKNOWN,
                        Decision.UNKNOWN
                    ),
                    serverInfo,
                    emptyList()
                )
            )
        } else {
            val serverInfo = mutableListOf<DnsServerConfiguration<TLSUpstreamAddress>>()
            serverInfo.add(
                DnsServerConfiguration(
                    address = createTlsUpstreamAddress(primary),
                    experimental = false,
                    supportedProtocols = listOf(TLS),
                    preferredProtocol = TLS
                )
            )
            if (!secondary.isNullOrBlank()) serverInfo.add(
                DnsServerConfiguration(
                    address = createTlsUpstreamAddress(
                        secondary
                    ), experimental = false, supportedProtocols = listOf(TLS), preferredProtocol = TLS
                )
            )
            onServerAdded.invoke(
                DnsServerInformation(
                    name,
                    HttpsDnsServerSpecification(
                        Decision.UNKNOWN,
                        Decision.UNKNOWN,
                        Decision.UNKNOWN,
                        Decision.UNKNOWN
                    ),
                    serverInfo,
                    emptyList()
                )
            )
        }
    }

    private fun createHttpsUpstreamAddress(url: String): HttpsUpstreamAddress {
        context.log("Creating HttpsUpstreamAddress for `$url`")
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

        return AbstractHttpsDNSHandle.waitUntilKnownServersArePopulated { allServer ->
            if(path != null) emptyList()
            else allServer.values.filter {
                it.servers.any { server ->
                    server.address.host == host && (port == null || server.address.port == port)
                }
            }
        }.firstOrNull()?.servers?.firstOrNull {
            it.address.host == host
        }?.address ?: if (port != null && path != null) HttpsUpstreamAddress(host, port, path)
        else if (port != null) HttpsUpstreamAddress(host, port)
        else if (path != null) HttpsUpstreamAddress(host, urlPath = path)
        else HttpsUpstreamAddress(host)
    }

    private fun createTlsUpstreamAddress(host: String): TLSUpstreamAddress {
        context.log("Creating TLSUpstreamAddress for `$host`")
        val parsedHost:String
        var port: Int? = null
        if (host.contains(":")) {
            parsedHost = host.split(":")[0]
            port = host.split(":")[1].split("/")[0].toInt()
            if (port > 65535) port = null
        } else parsedHost = host
        return AbstractTLSDnsHandle.waitUntilKnownServersArePopulated { allServer ->
            allServer.values.filter {
                it.servers.any { server ->
                    server.address.host == parsedHost && (port == null || server.address.port == port)
                }
            }
        }.firstOrNull()?.servers?.firstOrNull {
            it.address.host == parsedHost
        }?.address ?: if (port != null) TLSUpstreamAddress(parsedHost, port)
        else TLSUpstreamAddress(parsedHost)
    }

    fun addUrlTextWatcher(input: TextInputLayout, editText: TextInputEditText, emptyAllowed: Boolean) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                val valid =
                    (emptyAllowed && s.isBlank()) || (dnsOverHttps && SERVER_URL_REGEX.matches(s.toString())) || (!dnsOverHttps && TLS_REGEX.matches(
                        s.toString()
                    ))

                input.error = if (valid) {
                    null
                } else context.getString(R.string.error_invalid_url)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

        })
    }

    fun inputsValid(): Boolean = serverNameWrap.error == null &&
            primaryServerWrap.error == null &&
            secondaryServerWrap.error == null

    override fun destroy() {}
}