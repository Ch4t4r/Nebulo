package com.frostnerd.smokescreen.activity

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.frostnerd.dnstunnelproxy.DnsServerInformation
import com.frostnerd.dnstunnelproxy.DnsServerInformationTypeAdapter
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformationTypeAdapter
import com.frostnerd.general.readJsonArray
import com.frostnerd.general.readJsonObject
import com.frostnerd.lifecyclemanagement.BaseActivity
import com.frostnerd.smokescreen.Logger
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.dialog.ServerImportDialog
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.util.LanguageContextWrapper
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL

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
class ServerImportActivity : BaseActivity() {
    override fun getConfiguration(): Configuration {
        return Configuration.withDefaults()
    }

    private var importJob: Job? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageContextWrapper.attachFromSettings(this, newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getPreferences().theme.dialogStyle)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialogactivity_server_import)
        supportActionBar?.hide()
        actionBar?.hide()

        if (intent != null) {
            val data = intent.data
            if (data != null) {
                importJob = GlobalScope.launch {
                    var exception: java.lang.Exception? = null
                    val couldImport: Boolean = try {
                        importServerFromUri(data)
                    } catch (e: Exception) {
                        exception = e
                        e.printStackTrace()
                        false
                    }

                    if (!couldImport) {
                        launch(Dispatchers.Main) {
                            AlertDialog.Builder(this@ServerImportActivity, getPreferences().theme.dialogStyle)
                                .setTitle(R.string.dialog_serverimportfailed_title)
                                .setMessage(
                                    if (exception != null) getString(
                                        R.string.dialog_serverimportfailed_text_exception,
                                        Logger.stacktraceToString(exception)
                                    ) else getString(R.string.dialog_serverimportfailed_text)
                                )
                                .setNeutralButton(android.R.string.ok) { dialog, _ ->
                                    dialog.cancel()
                                }
                                .setOnCancelListener {
                                    finish()
                                }
                                .setOnDismissListener {
                                    finish()
                                }.show()
                        }
                    }
                    importJob = null
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        importJob?.cancel()
    }

    private suspend fun importServerFromUri(uri: Uri): Boolean {
        return withContext(Dispatchers.Default) {
            val inputStream: InputStream?
            if (uri.scheme == "content" || uri.schemeSpecificPart == "file") {
                val resolver = contentResolver
                inputStream = resolver.openInputStream(uri)

            } else if (uri.scheme == "https" || uri.scheme == "http") {
                val url = URL(uri.toString())
                val connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.readTimeout = 5000
                connection.connectTimeout = 5000
                inputStream = connection.inputStream
            } else inputStream = null
            if (inputStream != null) {
                val stream = InputStreamReader(inputStream)
                val rtrn = readJson(readStreamFully(stream))
                stream.close()
                rtrn
            } else false
        }
    }

    private fun readStreamFully(reader:InputStreamReader): String {
        val bufferedReader = BufferedReader(reader)
        return bufferedReader.useLines {
            it.joinToString(separator = "\n")
        }
    }

    private fun readJson(jsonString: String): Boolean {
        val servers = mutableListOf<DnsServerInformation<*>>()
        val httpsAdapter = HttpsDnsServerInformationTypeAdapter()
        val tlsAdapter = DnsServerInformationTypeAdapter()
        val serverTypes = mutableListOf<Boolean>() //True when the server is DoH
        val reader = JsonReader(StringReader(jsonString))
        val readServerType:(String, JsonToken) -> Unit = { nameLowerCase: String, _: JsonToken ->
            if (nameLowerCase == "type") serverTypes.add(reader.nextString() == "doh")
            else reader.skipValue()

        }
        if (!reader.readJsonArray {
                val previousSize = serverTypes.size
                if (reader.readJsonObject(block = readServerType) && serverTypes.size == previousSize) serverTypes.add(
                    true
                )
            }){
            val previousSize = serverTypes.size
            if (reader.readJsonObject(block = readServerType) && serverTypes.size == previousSize) serverTypes.add(true)
        }
        val actualReader = JsonReader(StringReader(jsonString))
        val readServer = { https: Boolean ->
            val server = if (https) httpsAdapter.read(actualReader) else tlsAdapter.read(actualReader)
            if (server != null) {
                servers.add(server)
                true
            } else false
        }
        var index = 0
        val isArray = actualReader.readJsonArray(false) {
            readServer(serverTypes[index++])
        }
        if (!isArray) {
            if (actualReader.peek() == JsonToken.BEGIN_OBJECT && !readServer(serverTypes[0])) return false
            return false
        }

        return if (servers.isEmpty()) false
        else {
            GlobalScope.launch(Dispatchers.Main) {
                val dialog = ServerImportDialog(this@ServerImportActivity, servers)
                dialog.setOnDismissListener {
                    finish()
                }
                dialog.show()
            }
            true
        }
    }
}