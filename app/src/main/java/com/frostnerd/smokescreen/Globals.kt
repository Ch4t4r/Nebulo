package com.frostnerd.smokescreen

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.widget.TextView


/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */

fun showInfoTextDialog(context:Context, title:String, text:String): androidx.appcompat.app.AlertDialog {
    val stringWithLinks = SpannableString(text)
    Linkify.addLinks(stringWithLinks, Linkify.ALL)

    val dialog = androidx.appcompat.app.AlertDialog.Builder(context, context.getPreferences().theme.dialogStyle)
        .setTitle(title)
        .setMessage(stringWithLinks)
        .setNeutralButton(R.string.ok, null)
        .show()
    val textView = dialog.findViewById<TextView>(android.R.id.message)
    textView?.movementMethod = LinkMovementMethod.getInstance()
    textView?.setLinkTextColor(Color.parseColor("#64B5F6"))
    return dialog
}

fun isPackageInstalled(context: Context, packageName: String): Boolean {
    val packageManager = context.packageManager
    val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
    val list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return list.size > 0
}

interface BackpressFragment {
    fun onBackPressed():Boolean
}