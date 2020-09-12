package com.frostnerd.smokescreen.dialog

import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.webkit.URLUtil
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
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
    private val githubNameRegex = Regex("^https://raw.githubusercontent.com/([^/]+).*$", RegexOption.IGNORE_CASE)
    private val domainNameRegex = Regex("^https://([^/]{2,}).*$", RegexOption.IGNORE_CASE)
    private var userModifiedName = false


    init {
        val view = layoutInflater.inflate(R.layout.dialog_new_hostsource, null, false)
        setView(view)
        setTitle(if(hostSource != null) R.string.dialog_newhostsource_edit_title else R.string.dialog_newhostsource_title)
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
        view.name.doOnTextChanged { _, _, _, _ -> view.nameTil.error = null }
        setOnShowListener {
            if (hostSource != null) {
                view.url.setText(hostSource.source)
                view.name.setText(hostSource.name)
                view.whitelist.isChecked = hostSource.whitelistSource
            }
            view.url.addTextChangedListener(object: TextWatcher {
                private var previousText:String = ""

                override fun afterTextChanged(s: Editable?) {
                    view.urlTil.error = null
                    val alteredString = if(s.isNullOrBlank()) "" else if(s.contains("://")) s.toString() else "https://$s"
                    if(URLUtil.isValidUrl(alteredString)) {
                        if(view.name.text.isNullOrBlank() || !userModifiedName) {
                            val githubMatcher = githubNameRegex.matchEntire(alteredString)
                            val domainMatcher = domainNameRegex.matchEntire(alteredString)
                            if(githubMatcher != null) {
                                view.name.setText(githubMatcher.groupValues[1])
                            } else if(domainMatcher != null) {
                                val fqdn = domainMatcher.groupValues[1]
                                val domain = fqdn.split(".").let {
                                    if(it.size >= 2) {
                                        if(it.size >= 3 && it[it.size - 2].length <= 2){
                                            it[it.size - 3] + "." + it[it.size - 2] + "." + it[it.size - 1] //E.g. mycoolsize.co.nz
                                        } else it[it.size - 2] + "." + it[it.size - 1] // E.g. mycoolsite.com
                                    } else it[0] //eg localhost
                                }
                                view.name.setText(domain)
                            } else if(URLUtil.isContentUrl(alteredString)) {
                                val text = try {
                                    context.contentResolver.query(Uri.parse(alteredString), null, null, null, null).let {
                                        if(it?.moveToFirst() == false) null to it
                                        else it?.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME)) to it
                                   }.let {
                                       it.second?.close()
                                       it.first
                                   }
                                } catch (e: SecurityException) {
                                    view.url.setText(previousText)
                                    null
                                }
                                if(text != null) view.name.setText(text)
                            }
                        }
                    }
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    previousText = s.toString()
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

            })
            view.name.setOnFocusChangeListener { _, _ ->
                userModifiedName = true
            }
            getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                var valid = true
                if (name.text.isNullOrBlank()) {
                    nameTil.error = context.getString(R.string.dialog_newhostsource_error_name_empty)
                    valid = false
                } else {
                    nameTil.error = null
                }

                val alteredUrl = url.text.let {
                    when {
                        it.isNullOrBlank() -> ""
                        it.contains("://") -> it.toString()
                        else -> "https://$it"
                    }
                }
                if (alteredUrl.isBlank() || !URLUtil.isValidUrl(alteredUrl)) {
                    urlTil.error = context.getString(R.string.error_invalid_url)
                    valid = false
                } else {
                    urlTil.error = null
                }
                if (valid) {
                    val newSource = hostSource?.copy(
                        name = name.text.toString(),
                        source = alteredUrl,
                        whitelistSource = whitelist.isChecked
                    ) ?: HostSource(name.text.toString(), alteredUrl, whitelist.isChecked)
                    onSourceCreated(newSource)
                    dismiss()
                }
            }
        }
    }
}