package com.frostnerd.smokescreen.activity

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.frostnerd.lifecyclemanagement.BaseActivity
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.fragment.showLogExportDialog

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
class ErrorDialogActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_error_title)
            .setMessage(R.string.dialog_error_text)
            .setPositiveButton(R.string.all_yes) { dialog, _ ->
                showLogExportDialog {
                    finish()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.all_no) { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setCancelable(false).create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    override fun getConfiguration(): Configuration {
        return Configuration.withDefaults()
    }

}