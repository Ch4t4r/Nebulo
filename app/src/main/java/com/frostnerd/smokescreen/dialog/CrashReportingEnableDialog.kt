package com.frostnerd.smokescreen.dialog

import android.content.Context
import android.content.DialogInterface
import com.frostnerd.lifecyclemanagement.BaseDialog
import com.frostnerd.smokescreen.BuildConfig
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.SmokeScreen
import com.frostnerd.smokescreen.getPreferences
import io.sentry.Sentry

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
class CrashReportingEnableDialog(
    context: Context, showTesterText: Boolean = BuildConfig.VERSION_NAME.let {
        it.contains("alpha", true) || it.contains("beta", true)
    },
    onConsentGiven:(() -> Unit)? = null
) : BaseDialog(context, context.getPreferences().theme.dialogStyle) {

    init {
        val view = layoutInflater.inflate(R.layout.dialog_crashreporting, null, false)
        setTitle(R.string.dialog_crashreporting_title)
        setMessage(context.getString(if (showTesterText) R.string.dialog_crashreporting_message else R.string.dialog_crashreporting_message_notester))
        setView(view)
        setButton(DialogInterface.BUTTON_POSITIVE, context.getString(R.string.dialog_crashreporting_positive)) { dialog, _ ->
            context.getPreferences().crashReportingEnabled = true
            context.getPreferences().crashReportingConsent = true
            context.getPreferences().crashReportingConsentAsked = true
            (context.applicationContext as SmokeScreen).initSentry(true)
            onConsentGiven?.invoke()
            dialog.dismiss()
        }
        setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(R.string.dialog_crashreporting_negative)) { dialog, _ ->
            context.getPreferences().crashReportingEnabled = false
            context.getPreferences().crashReportingConsent = false
            context.getPreferences().crashReportingConsentAsked = true
            Sentry.close()
            dialog.dismiss()
        }
    }

    override fun destroy() {
    }
}