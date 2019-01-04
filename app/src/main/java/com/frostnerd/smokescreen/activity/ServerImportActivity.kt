package com.frostnerd.smokescreen.activity

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformationTypeAdapter
import com.frostnerd.lifecyclemanagement.BaseActivity
import com.frostnerd.smokescreen.Logger
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.dialog.ServerImportDialog
import com.frostnerd.smokescreen.getPreferences
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 * <p>
 * development@frostnerd.com
 */
class ServerImportActivity : BaseActivity() {
    override fun getConfiguration(): Configuration {
        return Configuration.withDefaults()
    }
    private var importJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getPreferences().theme.dialogStyle)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialogactivity_server_import)
        supportActionBar?.hide()
        actionBar?.hide()

        if (intent != null) {
            val data = intent.data
            if (data != null) {
                importJob =  GlobalScope.launch {
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
                                .setNeutralButton(R.string.ok) { dialog, _ ->
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
        return GlobalScope.async {
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
            if(inputStream != null) {
                val reader = JsonReader(InputStreamReader(inputStream))
                val rtrn = readJson(reader)
                reader.close()
                rtrn
            } else false
        }.await()
    }

    private fun readJson(reader: JsonReader): Boolean {
        val servers = mutableListOf<HttpsDnsServerInformation>()
        val adapter = HttpsDnsServerInformationTypeAdapter()
        val readServer = {
            val server = adapter.read(reader)
            if (server != null) {
                servers.add(server)
                true
            } else false
        }
        if (readJsonArray(reader, false, readServer))
        else if (reader.peek() == JsonToken.BEGIN_OBJECT) {
            if (!readServer()) return false
        } else return false

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

    private fun readJsonArray(reader: JsonReader, skip: Boolean = true, nextEntry: () -> Any): Boolean {
        if (reader.peek() == JsonToken.BEGIN_ARRAY) {
            reader.beginArray()
            while (reader.peek() != JsonToken.END_ARRAY) {
                nextEntry()
            }
            reader.endArray()
            return true
        } else if (skip) reader.skipValue()
        return false
    }
}