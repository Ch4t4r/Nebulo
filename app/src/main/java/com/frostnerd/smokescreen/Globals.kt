package com.frostnerd.smokescreen

import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.Color
import android.text.Html
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import kotlinx.android.synthetic.main.dialog_privacypolicy.view.*
import androidx.annotation.ColorInt
import okhttp3.internal.toHexString






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

fun showPrivacyPolicyDialog(context: Context) {
    val dialog = AlertDialog.Builder(context, context.getPreferences().theme.dialogStyle)
    dialog.setTitle(R.string.about_privacypolicy)
    val view = LayoutInflater.from(context).inflate(R.layout.dialog_privacypolicy, null, false)
    dialog.setView(view)
    view.webView.loadUrl("file:///android_asset/privacy_policy.html")
    dialog.setNeutralButton(R.string.all_close, null)
    dialog.show()
}

fun showInfoTextDialog(context:Context, title:String, text:String,
                       positiveButton:Pair<String, (DialogInterface, Int) -> Unit>? = null,
                       negativeButton:Pair<String, (DialogInterface, Int) -> Unit>? = null,
                       neutralButton:Pair<String, ((DialogInterface, Int) -> Unit)?>? = context.getString(android.R.string.ok) to null): AlertDialog {
    val stringWithLinks = SpannableString(text)
    Linkify.addLinks(stringWithLinks, Linkify.ALL)

    val span = Html.fromHtml(stringWithLinks.toString().replace("\n", "<br>"))

    val dialogBuilder = AlertDialog.Builder(context, context.getPreferences().theme.dialogStyle)
        .setTitle(title)
        .setMessage(span)
    if(neutralButton != null) dialogBuilder.setNeutralButton(neutralButton.first, neutralButton.second)
    if(positiveButton != null) dialogBuilder.setPositiveButton(positiveButton.first, positiveButton.second)
    if(negativeButton != null) dialogBuilder.setNegativeButton(negativeButton.first, negativeButton.second)

    val dialog = dialogBuilder.show()
    val textView = dialog.findViewById<TextView>(android.R.id.message)
    textView?.movementMethod = LinkMovementMethod.getInstance()
    textView?.linksClickable = true
    textView?.setLinkTextColor(Color.parseColor("#64B5F6"))
    return dialog
}

fun isPackageInstalled(context: Context, packageName: String): Boolean {
    val packageManager = context.packageManager
    val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
    val list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return list.size > 0
}

fun colorToHexString(@ColorInt color:Int):String {
    return String.format("#%06X", 0xFFFFFF and color)
}

@ColorInt
fun opaqueColor(@ColorInt color: Int, opactiy: Int): Int {
    val alpha = opactiy - opactiy % 5
    return Color.parseColor("#" + alpha.toHexString() + colorToHexString(color).replace("#", ""))
}


interface BackpressFragment {
    fun onBackPressed():Boolean
}