package com.frostnerd.smokescreen.dialog

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.getPreferences

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
class BatteryOptimizationInfoDialog(context:Context) :AlertDialog(context, context.getPreferences().theme.dialogStyle) {
    private val moreInfoLink = "https://dontkillmyapp.com?app=Nebulo"

    init {
        setTitle(R.string.dialog_batteryoptimization_title)
        setMessage(context.getString(R.string.dialog_servicekilled_message))
        setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(android.R.string.ok)) { dialog, _ ->
            dialog.dismiss()
        }
        setButton(DialogInterface.BUTTON_POSITIVE, context.getString(R.string.dialog_servicekilled_more_info)) { dialog, _ ->
            val i = Intent(Intent.ACTION_VIEW)
            i.data = Uri.parse(moreInfoLink)
            try {
                context.startActivity(i)
            } catch (e: ActivityNotFoundException) { Toast.makeText(context, R.string.error_no_webbrowser_installed, Toast.LENGTH_LONG).show() }
            dialog.dismiss()
        }
        setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(R.string.dialog_batteryoptimization_ignore)) { dialog, _ ->
            context.getPreferences().ignoreServiceKilled = true
            dialog.dismiss()
        }
    }
}