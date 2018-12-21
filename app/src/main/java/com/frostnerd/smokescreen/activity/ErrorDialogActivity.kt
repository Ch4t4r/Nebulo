package com.frostnerd.smokescreen.activity

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.fragment.showLogExportDialog

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 * <p>
 * development@frostnerd.com
 */
class ErrorDialogActivity: AppCompatActivity() {

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

}