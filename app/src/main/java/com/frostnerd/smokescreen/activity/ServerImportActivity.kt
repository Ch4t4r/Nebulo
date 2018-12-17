package com.frostnerd.smokescreen.activity

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.util.JsonReader
import android.util.JsonToken
import android.view.Window
import androidx.appcompat.app.AlertDialog
import com.frostnerd.lifecyclemanagement.BaseActivity
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.database.DatabaseHelper
import com.frostnerd.smokescreen.database.entities.UserServerConfiguration
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.showInfoTextDialog
import java.io.InputStreamReader
import java.lang.Exception
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

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getPreferences().theme.dialogStyle)
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()
        actionBar?.hide()

        var couldImport = false
        if (intent != null) {
            val data = intent.data
            if (data != null) {
                couldImport = try {
                    importServerFromUri(data)
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }
        if (!couldImport) {
            AlertDialog.Builder(this, getPreferences().theme.dialogStyle)
                .setTitle(R.string.dialog_serverimportfailed_title)
                .setMessage(R.string.dialog_serverimportfailed_text)
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
            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                reader.beginObject()
                val config = ImportedServerConfiguration()
                while (reader.hasNext() && reader.peek() == JsonToken.NAME) {
                    val name = reader.nextName()
                    when (name.toLowerCase()) {
                        "name" -> config.name = reader.nextString()
                        "primary" -> config.primaryUrl = reader.nextString()
                        "secondary" -> config.secondaryUrl = reader.nextString()
                        else -> reader.skipValue()
                    }
                }

                if (config.isValid()) {
                    importServer(config)
                    return true
                }
            }
            reader.close()
        }
        return false
    }

    private fun importServer(config: ImportedServerConfiguration) {
        val dialog = AlertDialog.Builder(this, getPreferences().theme.dialogStyle)
            .setTitle(getString(R.string.dialog_serverimport_title, config.name))
            .setMessage(
                getString(
                    R.string.dialog_serverimport_text,
                    config.name,
                    config.primaryUrl,
                    config.secondaryUrl ?: ""
                )
            )
            .setCancelable(false)
            .setNegativeButton(R.string.all_no) { dialog, _ ->
                dialog.cancel()
                finish()
            }
            .setPositiveButton(R.string.all_yes) { dialog, _ ->
                dialog.cancel()
                val configToSave = UserServerConfiguration(config.name!!, config.primaryUrl!!, config.secondaryUrl)
                DatabaseHelper.getInstance(this@ServerImportActivity).insert(configToSave)

                val intent = Intent(this, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
                finish()
            }.create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    private inner class ImportedServerConfiguration {
        var name: String? = null
        var primaryUrl: String? = null
        var secondaryUrl: String? = null

        fun isValid(): Boolean {
            var valid = name != null && primaryUrl != null
            valid = valid && isValidHttpsUrl(primaryUrl)
            valid = valid && (secondaryUrl == null || isValidHttpsUrl(secondaryUrl))
            return valid
        }

        private fun isValidHttpsUrl(possibleUrl: String?): Boolean {
            if (possibleUrl == null) return false

            try {
                return URL(possibleUrl).protocol == "https"
            } catch (e: Exception) {

            }
            return false
        }

        override fun toString(): String {
            return "ImportedServerConfiguration(name=$name, primaryUrl=$primaryUrl, secondaryUrl=$secondaryUrl)"
        }


    }
}