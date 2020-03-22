package com.frostnerd.smokescreen.dialog

import android.content.Context
import android.content.DialogInterface
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.frostnerd.dnstunnelproxy.Decision
import com.frostnerd.dnstunnelproxy.DnsServerConfiguration
import com.frostnerd.dnstunnelproxy.DnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.*
import com.frostnerd.encrypteddnstunnelproxy.tls.AbstractTLSDnsHandle
import com.frostnerd.encrypteddnstunnelproxy.tls.TLS
import com.frostnerd.encrypteddnstunnelproxy.tls.TLSUpstreamAddress
import com.frostnerd.lifecyclemanagement.BaseDialog
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.log
import com.frostnerd.smokescreen.util.preferences.UserServerConfiguration
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.android.synthetic.main.dialog_new_server.*
import kotlinx.android.synthetic.main.dialog_new_server.view.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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
    onServerAdded: (serverInfo: DnsServerInformation<*>) -> Unit,
    server: UserServerConfiguration? = null
) : BaseDialog(context, context.getPreferences().theme.dialogStyle) {
    companion object {
        // Hostpart has to begin with a character or number
        // Then has to either:
        //  - Consist of numbers, characters and dashes AND ends with a character
        //  - End with a character or number
        //  => Host part has to be at least 2 characters long
        // Host can optionally end with a dot
        //  - If there is a dot there has to be either a number or a char after it
        private val dohAddressPart = "(?:[a-z0-9](?:(?:[a-z0-9-]*[a-z]*[a-z0-9-]*[a-z0-9])|[a-z0-9])(?:.(?=[a-z0-9])|))*"
        val TLS_REGEX = Regex("^\\s*($dohAddressPart)(?::[1-9][0-9]{0,4})?\\s*$", RegexOption.IGNORE_CASE)

        fun isUrl(s:String): Boolean {
            return s.toHttpUrlOrNull() != null || "https://$s".toHttpUrlOrNull() != null
        }
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

                    if (dnsOverHttps && !primary.startsWith("http")) primary = "https://$primary"
                    if (dnsOverHttps && secondary != null && !secondary.startsWith("http")) secondary = "https://$secondary"
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
                    dnsOverHttps = position == 0
                    setHintAndTitle(view, dnsOverHttps, title)
                    primaryServer.text = primaryServer.text
                    secondaryServer.text = secondaryServer.text
                }
            }
            if(server != null) {
                serverName.setText(server.serverInformation.name)
                primaryServer.setText(server.serverInformation.servers[0].address.formatToString())
                if(server.serverInformation.servers.size > 1) {
                    secondaryServer.setText(server.serverInformation.servers[1].address.formatToString())
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

        val parsedUrl = url.toHttpUrl()
        val host = parsedUrl.host
        val port = parsedUrl.port
        val path = parsedUrl.pathSegments.takeIf {
            it.isNotEmpty() && (it.size > 1 || !it.contains("")) // Non-empty AND contains something other than "" (empty string used when there is no path)
        }?.joinToString(separator = "/")

        return AbstractHttpsDNSHandle.waitUntilKnownServersArePopulated { allServer ->
            if(path != null) emptyList()
            else allServer.values.filter {
                it.servers.any { server ->
                    server.address.host == host && (server.address.port == port)
                }
            }
        }.firstOrNull()?.servers?.firstOrNull {
            it.address.host == host
        }?.address ?: if (path != null) HttpsUpstreamAddress(host, port, path)
        else HttpsUpstreamAddress(host, port)
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

    private fun addUrlTextWatcher(input: TextInputLayout, editText: TextInputEditText, emptyAllowed: Boolean) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                var valid = (emptyAllowed && s.isBlank())
                valid = valid || (!s.isBlank() && dnsOverHttps && isUrl(s.toString()))
                valid = valid || (!s.isBlank() && !dnsOverHttps && TLS_REGEX.matches(s.toString()))

                input.error = if (valid) {
                    null
                } else if(dnsOverHttps) context.getString(R.string.error_invalid_url)
                else context.getString(R.string.error_invalid_host)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

        })
    }

    private fun inputsValid(): Boolean = serverNameWrap.error == null &&
            primaryServerWrap.error == null &&
            secondaryServerWrap.error == null

    override fun destroy() {}
}