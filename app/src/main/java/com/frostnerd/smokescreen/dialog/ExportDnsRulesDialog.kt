package com.frostnerd.smokescreen.dialog

import android.content.Context
import android.content.DialogInterface
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.getPreferences
import kotlinx.android.synthetic.main.dialog_export_dnsrules.view.*

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
class ExportDnsRulesDialog(context: Context,
                           beginExport: (exportFromSources:Boolean, exportUserRules:Boolean, type:ExportType) -> Unit) :
androidx.appcompat.app.AlertDialog(context, context.getPreferences().theme.dialogStyle) {

    init {
        val view = layoutInflater.inflate(R.layout.dialog_export_dnsrules, null, false)
        setView(view)
        setTitle(R.string.dialog_exportdnsrules_title)
        view.exportSources.setOnCheckedChangeListener { _, isChecked ->
            view.exportUserRules.isEnabled = isChecked
        }
        view.exportUserRules.setOnCheckedChangeListener { _, isChecked ->
            view.exportSources.isEnabled = isChecked
        }
        setButton(DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok)) { dialog, _ ->
            dialog.dismiss()
            beginExport(
                view.exportSources.isChecked,
                view.exportUserRules.isChecked,
                if(view.nonwhitelist.isChecked) ExportType.NON_WHITELIST else ExportType.WHITELIST
            )
        }
        setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(android.R.string.cancel)) { dialog, _ ->
            dialog.dismiss()
        }
    }
}

enum class ExportType {
    WHITELIST, NON_WHITELIST
}