package com.frostnerd.smokescreen.dialog

import android.content.Context
import android.content.DialogInterface
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.RadioButton
import co.metalab.asyncawait.async
import com.frostnerd.dnstunnelproxy.DEFAULT_DNSERVER_CAPABILITIES
import com.frostnerd.encrypteddnstunnelproxy.AbstractHttpsDNSHandle
import com.frostnerd.encrypteddnstunnelproxy.HttpsUpstreamAddress
import com.frostnerd.lifecyclemanagement.BaseDialog
import com.frostnerd.materialedittext.MaterialEditText
import com.frostnerd.smokescreen.R
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
class ServerChoosalDialog(context: Context, callback:OnServersChosen) : BaseDialog(context, context.getPreferences().getTheme().dialogStyle) {
    private val knownServersMap = hashMapOf<String, HttpsUpstreamAddress>()
    private var customUrl = false
    private var primaryServerUrl:String
    private var secondaryServerUrl:String? = null

    private var oldPrimaryServerUrl:String
    private var oldSecondaryServerUrl:String?

    companion object {
        val SERVER_URL_REGEX = Regex("^(?:https://)?([a-z0-9][a-z0-9-.]*[a-z0-9])(/[a-z0-9-.]+)*(/)?$", RegexOption.IGNORE_CASE)
    }

    init {
        val view = layoutInflater.inflate(R.layout.dialog_server_configuration, null, false)
        setTitle(R.string.dialog_title_serverconfiguration)
        setView(view)

        customUrl = context.getPreferences().isCustomServerUrl()
        primaryServerUrl = context.getPreferences().getServerURl()
        secondaryServerUrl = context.getPreferences().getSecondaryServerURl()
        oldPrimaryServerUrl = primaryServerUrl
        oldSecondaryServerUrl = secondaryServerUrl

        setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.cancel)
        ) { _, _ -> }
        setButton(DialogInterface.BUTTON_POSITIVE, context.getString(R.string.ok)
        ) { _, _ ->
            if(customUrl) {
                primaryServerUrl = primaryServer.text.toString()
                secondaryServerUrl = if(!secondaryServer.text.isNullOrBlank()) {
                    secondaryServer.text.toString()
                } else null
            } else secondaryServerUrl = null
            callback.serversChosen(primaryServerUrl, secondaryServerUrl, customUrl)
        }

        setOnShowListener {
            addKnownServers()
            setCustomServerSyntaxCheck()

            if(customUrl) {
                primaryServerWrap.visibility = View.VISIBLE
                primaryServer.setText(primaryServerUrl)
                secondaryServer.setText(secondaryServerUrl)
                customServerOption.isChecked = true
            } else {
                primaryServerWrap.isEnabled = false
                primaryServer.isEnabled = false
            }
        }
    }

    private fun setCustomServerSyntaxCheck() {
        primaryServer.addTextChangedListener(object:TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val valid = !s.isNullOrBlank() && SERVER_URL_REGEX.matches(s!!.toString())
                if(valid) {
                    if(secondaryServerWrap.visibility == View.GONE) {
                        secondaryServerWrap.visibility = View.VISIBLE
                    }
                    primaryServerWrap.indicatorState = MaterialEditText.IndicatorState.UNDEFINED
                }else if(!valid) {
                    primaryServerWrap.indicatorState = MaterialEditText.IndicatorState.INCORRECT
                }
                primaryServerUrl = s?.toString() ?: primaryServerUrl
                setOkButtonState()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        secondaryServer.addTextChangedListener(object:TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                secondaryServerUrl = s?.toString()
                val valid = s.isNullOrEmpty() || SERVER_URL_REGEX.matches(s!!.toString())
                secondaryServerWrap.indicatorState = if(valid) MaterialEditText.IndicatorState.UNDEFINED else MaterialEditText.IndicatorState.INCORRECT
                setOkButtonState()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setOkButtonState() {
        val enableButton = !customUrl || (!primaryServerUrl.isBlank() && SERVER_URL_REGEX.matches(primaryServerUrl) &&
                (secondaryServerUrl.isNullOrBlank() || SERVER_URL_REGEX.matches(secondaryServerUrl!!)))

        getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = enableButton
        getButton(DialogInterface.BUTTON_POSITIVE).visibility = if(enableButton) View.VISIBLE else View.INVISIBLE
    }

    private fun addKnownServers() {
        context.async {
            await {
                for ((name, serverInfo) in AbstractHttpsDNSHandle.KNOWN_DNS_SERVERS) {
                    if (!serverInfo.hasCapability(DEFAULT_DNSERVER_CAPABILITIES.BLOCK_ADS)) {
                        knownServersMap[name] = serverInfo.servers[0].address
                    }
                }
            }
            for (s in knownServersMap.keys.asSequence().sortedDescending()) {
                val button = RadioButton(context)

                button.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                button.setTextColor(context.getPreferences().getTheme().getTextColor(context))
                button.text = "$s (${knownServersMap[s]!!.FQDN})"
                button.setOnCheckedChangeListener { _, isChecked ->
                    if(isChecked) {
                        customUrl = false
                        primaryServerUrl = knownServersMap[s]!!.getUrl()
                        secondaryServerUrl = null
                    }
                    setOkButtonState()
                }
                knownServersGroup.addView(button, 0)
                if(!customUrl && knownServersMap[s]!!.getUrl() == primaryServerUrl) button.isChecked = true
            }
            customServerOption.setOnCheckedChangeListener { _, isChecked ->
                primaryServerWrap.isEnabled = isChecked
                secondaryServerWrap.isEnabled = isChecked
                primaryServer.isEnabled = isChecked
                secondaryServer.isEnabled = isChecked

                if (isChecked) {
                    primaryServerWrap.visibility = View.VISIBLE

                    oldSecondaryServerUrl = secondaryServerUrl
                    oldPrimaryServerUrl = primaryServerUrl

                    primaryServerUrl = primaryServer.text.toString()
                    secondaryServerUrl = secondaryServer.text.toString()

                    customUrl = true
                    primaryServer.requestFocus()
                    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

                    val service = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                    service?.showSoftInput(primaryServer, InputMethodManager.SHOW_IMPLICIT)
                } else {
                    primaryServerWrap.visibility = View.GONE
                    primaryServerUrl = oldPrimaryServerUrl
                    secondaryServerUrl = oldSecondaryServerUrl
                }
                setOkButtonState()
            }
        }
    }

    override fun destroy() {

    }

    interface OnServersChosen {
        fun serversChosen(primaryServerUrl:String, secondaryServerUrl:String?, customServers:Boolean)
    }
}