package com.frostnerd.smokescreen.dialog

import android.content.Context
import android.content.DialogInterface
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import com.frostnerd.lifecyclemanagement.BaseDialog
import com.frostnerd.materialedittext.MaterialEditText
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.getPreferences
import kotlinx.android.synthetic.main.dialog_new_server.*

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */
class NewServerDialog(
    context: Context,
    onServerAdded: (name: String, primaryServerUrl: String, secondaryServerUrl: String?) -> Unit
) : BaseDialog(context, context.getPreferences().getTheme().dialogStyle) {

    companion object {
        val SERVER_URL_REGEX =
            Regex("^(?:https://)?([a-z0-9][a-z0-9-.]*[a-z0-9])(/[a-z0-9-.]+)*(/)?$", RegexOption.IGNORE_CASE)
    }

    init {
        val view = layoutInflater.inflate(R.layout.dialog_new_server, null, false)
        setTitle(R.string.dialog_newserver_title)
        setView(view)

        setButton(
            DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.cancel)
        ) { _, _ -> }

        setButton(
            DialogInterface.BUTTON_POSITIVE, context.getString(R.string.ok)
        ) { _, _ -> }

        setOnShowListener { _ ->
            addUrlTextWatcher(primaryServerWrap, primaryServer, false)
            addUrlTextWatcher(secondaryServerWrap, secondaryServer, true)

            getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                if (inputsValid()) {
                    val name = serverName.text.toString()
                    val primary = primaryServer.text.toString()
                    val secondary = if (secondaryServer.text.isNullOrBlank()) null else secondaryServer.text.toString()

                    onServerAdded.invoke(name, primary, secondary)
                    dismiss()
                } else {
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    vibrator.vibrate(250)
                }
            }
        }
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