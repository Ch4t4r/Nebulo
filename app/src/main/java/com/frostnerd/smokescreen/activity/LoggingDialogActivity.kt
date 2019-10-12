package com.frostnerd.smokescreen.activity

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.frostnerd.lifecyclemanagement.BaseActivity
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.fragment.showLogExportDialog
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.util.LanguageContextWrapper
import com.frostnerd.smokescreen.util.Notifications

/*
 * Copyright (C) {YEAR} Daniel Wolf (Ch4t4r)
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

class LoggingDialogActivity : BaseActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageContextWrapper.attachFromSettings(this, newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(Notifications.ID_APP_CRASH)
        if (getPreferences().loggingEnabled) {
            showLogExportDialog {
                finish()
            }
        } else {
            showEnableLoggingDialog()
        }
    }

    private fun showEnableLoggingDialog() {
        val dialog = AlertDialog.Builder(this).setTitle(R.string.title_enable_logging)
            .setMessage(R.string.dialog_enable_logging)
            .setPositiveButton(R.string.all_yes) { dialog, _ ->
                dialog.dismiss()
                getPreferences().loggingEnabled = true
                showLogExportDialog {
                    finish()
                }
            }
            .setCancelable(false)
            .setNegativeButton(R.string.all_no) { dialog, _ ->
                dialog.dismiss()
                finish()
            }.create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    override fun getConfiguration(): Configuration {
        return Configuration.withDefaults()
    }

}