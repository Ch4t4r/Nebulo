package com.frostnerd.smokescreen.dialog

import android.content.Context
import android.content.DialogInterface
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import com.frostnerd.dnstunnelproxy.Decision
import com.frostnerd.encrypteddnstunnelproxy.*
import com.frostnerd.lifecyclemanagement.BaseDialog
import com.frostnerd.materialedittext.MaterialEditText
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.log
import kotlinx.android.synthetic.main.dialog_new_server.*

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
    title:String? = null,
    onServerAdded: (serverInfo: HttpsDnsServerInformation) -> Unit
) : BaseDialog(context, context.getPreferences().theme.dialogStyle) {

    companion object {
        val SERVER_URL_REGEX =
            Regex("^\\s*(?:https://)?([a-z0-9][a-z0-9-.]*[a-z0-9])(?::[1-9][0-9]{0,4})?(/[a-z0-9-.]+)*(/)?\\s*$", RegexOption.IGNORE_CASE)
    }

    init {
        val view = layoutInflater.inflate(R.layout.dialog_new_server, null, false)
        if(title != null) setTitle(title)
        else setTitle(R.string.dialog_newserver_title)
        setView(view)

        setButton(
            DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.cancel)
        ) { _, _ -> }

        setButton(
            DialogInterface.BUTTON_POSITIVE, context.getString(R.string.ok)
        ) { _, _ -> }

        setOnShowListener {
            addUrlTextWatcher(primaryServerWrap, primaryServer, false)
            addUrlTextWatcher(secondaryServerWrap, secondaryServer, true)

            getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                if (inputsValid()) {
                    val name = serverName.text.toString()
                    var primary = primaryServer.text.toString().trim()
                    var secondary = if (secondaryServer.text.isNullOrBlank()) null else secondaryServer.text.toString().trim()

                    if (primary.startsWith("https")) primary = primary.replace("https://", "")
                    if (secondary != null && secondary.startsWith("https")) secondary = secondary.replace("https://", "")
                    invokeCallback(name, primary, secondary, onServerAdded)
                    dismiss()
                } else {
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    vibrator.vibrate(250)
                }
            }
        }
    }

    private fun invokeCallback(name:String, primary:String, secondary:String?, onServerAdded:(HttpsDnsServerInformation) -> Unit) {
        val requestType = mapOf(RequestType.WIREFORMAT_POST to ResponseType.WIREFORMAT)
        val serverInfo = mutableListOf<HttpsDnsServerConfiguration>()
        serverInfo.add(HttpsDnsServerConfiguration(address = createUpstreamAddress(primary), requestTypes = requestType, experimental = false))
        if(!secondary.isNullOrBlank()) serverInfo.add(HttpsDnsServerConfiguration(address = createUpstreamAddress(secondary), requestTypes = requestType, experimental = false))
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
    }

    private fun createUpstreamAddress(url:String): HttpsUpstreamAddress {
        context.log("Creating HttpsUpstreamAddress for `$url`")
        var host = ""
        var port:Int? = null
        var path:String? = null
        if(url.contains(":")) {
            host = url.split(":")[0]
            port = url.split(":")[1].split("/")[0].toInt()
            if(port > 65535) port = null
        }
        if(url.contains("/")) {
            path = url.split("/")[1]
            if(host == "") host = url.split("/")[0]
        }
        if(host == "") host = url
        return if(port != null && path != null) HttpsUpstreamAddress(host, port, path)
        else if(port != null) HttpsUpstreamAddress(host, port)
        else if(path != null) HttpsUpstreamAddress(host, urlPath = path)
        else HttpsUpstreamAddress(host)
    }

    fun addUrlTextWatcher(materialEditText: MaterialEditText, editText: EditText, emptyAllowed: Boolean) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                val valid = (emptyAllowed && s.isBlank()) || SERVER_URL_REGEX.matches(s.toString())

                materialEditText.indicatorState = if (valid) {
                    if (s.isBlank()) MaterialEditText.IndicatorState.UNDEFINED
                    else MaterialEditText.IndicatorState.CORRECT
                } else MaterialEditText.IndicatorState.INCORRECT
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

        })
    }

    fun inputsValid(): Boolean = serverNameWrap.indicatorState != MaterialEditText.IndicatorState.INCORRECT &&
            primaryServerWrap.indicatorState != MaterialEditText.IndicatorState.INCORRECT &&
            secondaryServerWrap.indicatorState != MaterialEditText.IndicatorState.INCORRECT


    override fun destroy() {}
}