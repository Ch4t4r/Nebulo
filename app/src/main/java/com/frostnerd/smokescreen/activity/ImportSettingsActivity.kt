package com.frostnerd.smokescreen.activity

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.frostnerd.preferenceskt.preferenceexport.PreferenceExport
import com.frostnerd.smokescreen.Logger
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.database.entities.HostSource
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.log
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/*
 * Copyright (C) 2020 Daniel Wolf (Ch4t4r)
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
class ImportSettingsActivity:AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getPreferences().theme.dialogStyle)
        super.onCreate(savedInstanceState)
        try {
            log("Beginning settings import from ${intent?.data}")
            intent?.data?.let {
                readFromUri(it)
            }?.let {
                importFromString(it)
            }
            Toast.makeText(this, R.string.settings_import_succeeded, Toast.LENGTH_LONG).show()
        } catch (ex:Throwable) {
            log("Settings import failed")
            log(Logger.stacktraceToString(ex))
            Toast.makeText(this, R.string.settings_import_failed, Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun readFromUri(uri: Uri): String? {
        val inputStream: InputStream? = if (uri.scheme == "content" || uri.schemeSpecificPart == "file") {
            val resolver = contentResolver
            resolver.openInputStream(uri)
        } else if (uri.scheme == "https" || uri.scheme == "http") {
            val url = URL(uri.toString())
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.readTimeout = 5000
            connection.connectTimeout = 5000
            connection.inputStream
        } else null
        return inputStream?.readBytes()?.decodeToString()
    }

    private fun importFromString(serialized:String) {
        val lines = serialized.split("\n")
        val dao = getDatabase().hostSourceDao()
        lines.subList(1, lines.size - 2).forEach {
            val split = it.split(";-;") // Name, enabled, source, whitelist
            val foundSource = dao.findBySource(split[2])
            if(foundSource == null) {
                dao.insert(HostSource(split[0], split[2], split[3].toBoolean()).apply {
                    enabled = split[1].toBoolean()
                })
            } else {
                foundSource.enabled = split[1].toBoolean()
                foundSource.whitelistSource = split[3].toBoolean()
                foundSource.name = split[0]
                dao.update(foundSource)
            }
        }
        val editor = getPreferences().sharedPreferences.edit()
        PreferenceExport.import(lines.last(), editor)
        editor.apply()
    }
}