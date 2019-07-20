package com.frostnerd.smokescreen.dialog

import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.webkit.URLUtil
import androidx.appcompat.app.AlertDialog
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.database.entities.HostSource
import com.frostnerd.smokescreen.getPreferences
import kotlinx.android.synthetic.main.dialog_new_hostsource.*
import kotlinx.android.synthetic.main.dialog_new_hostsource.view.*

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
class NewHostSourceDialog(
    context: Context,
    onSourceCreated: (HostSource) -> Unit,
    showFileChooser: (fileChosen: (uri: Uri) -> Unit) -> Unit,
    hostSource: HostSource? = null
) : AlertDialog(context, context.getPreferences().theme.dialogStyle) {

    init {
        val view = layoutInflater.inflate(R.layout.dialog_new_hostsource, null, false)
        setView(view)
        setTitle(R.string.dialog_newhostsource_title)
        setButton(DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok)) { dialog, _ ->
            dialog.dismiss()
        }
        setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(android.R.string.cancel)) { dialog, _ ->
            dialog.dismiss()
        }
        view.name.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) nameTil.error = null
        }
        view.url.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) urlTil.error = null
        }
        view.chooseFile.setOnClickListener {
            showFileChooser {
                view.url.setText(it.toString())
            }
        }
        setOnShowListener {
            if (hostSource != null) {
                view.url.setText(hostSource.source)
                view.name.setText(hostSource.name)
                view.whitelist.isChecked = hostSource.whitelistSource
            }
            getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                var valid = true
                if (name.text.isNullOrBlank()) {
                    nameTil.error = context.getString(R.string.dialog_newhostsource_error_name_empty)
                    valid = false
                } else {
                    nameTil.error = null
                }
                if (url.text.isNullOrBlank() || !URLUtil.isValidUrl(url.text.toString())) {
                    urlTil.error = context.getString(R.string.error_invalid_url)
                    valid = false
                } else {
                    urlTil.error = null
                }
                if (valid) {
                    val newSource = hostSource?.copy(
                        name = name.text.toString(),
                        source = url.text.toString(),
                        whitelistSource = whitelist.isChecked
                    ) ?: HostSource(name.text.toString(), url.text.toString(), whitelist.isChecked)
                    onSourceCreated(newSource)
                    dismiss()
                }
            }
        }
    }
}