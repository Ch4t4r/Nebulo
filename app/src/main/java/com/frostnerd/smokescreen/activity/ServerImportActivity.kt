package com.frostnerd.smokescreen.activity

import android.net.Uri
import android.os.Bundle
import android.view.Window
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
import java.io.InputStreamReader

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

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getPreferences().theme.dialogStyle)
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()
        actionBar?.hide()

        var couldImport = false
        var exception:java.lang.Exception? = null
        if (intent != null) {
            val data = intent.data
            if (data != null) {
                couldImport = try {
                    importServerFromUri(data)
                } catch (e: Exception) {
                    exception = e
                    e.printStackTrace()
                    false
                }
            }
        }
        if (!couldImport) {
            AlertDialog.Builder(this, getPreferences().theme.dialogStyle)
                .setTitle(R.string.dialog_serverimportfailed_title)
                .setMessage(if(exception != null) getString(R.string.dialog_serverimportfailed_text_exception, Logger.stacktraceToString(exception)) else getString(R.string.dialog_serverimportfailed_text))
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

    private fun importServerFromUri(uri: Uri): Boolean {
        val resolver = contentResolver
        val inputStream = resolver.openInputStream(uri)
        if (inputStream != null) {
            val reader = JsonReader(InputStreamReader(inputStream))
            val rtrn = readJson(reader)
            reader.close()
            return rtrn
        }
        return false
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
            val dialog = ServerImportDialog(this, servers)
            dialog.setOnDismissListener {
                finish()
            }
            dialog.show()
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