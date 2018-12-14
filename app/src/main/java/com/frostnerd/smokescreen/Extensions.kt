package com.frostnerd.smokescreen

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.frostnerd.smokescreen.util.preferences.AppSettings
import com.frostnerd.smokescreen.util.preferences.AppSettingsSharedPreferences
import com.frostnerd.smokescreen.util.preferences.fromSharedPreferences

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */

fun Context.registerReceiver(intentFilter: IntentFilter, receiver: (intent: Intent?) -> Unit): BroadcastReceiver {
    val actualReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            receiver(intent)
        }
    }
    this.registerReceiver(actualReceiver, intentFilter)
    return actualReceiver
}

fun Context.startForegroundServiceCompat(intent: Intent) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(intent)
    } else startService(intent)
}

fun Context.registerReceiver(filteredActions: List<String>, receiver: (intent: Intent?) -> Unit): BroadcastReceiver {
    val filter = IntentFilter()
    for (filteredAction in filteredActions) {
        filter.addAction(filteredAction)
    }

    val actualReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            receiver(intent)
        }
    }
    this.registerReceiver(actualReceiver, filter)
    return actualReceiver
}

fun Context.registerLocalReceiver(intentFilter: IntentFilter, receiver: (intent: Intent?) -> Unit): BroadcastReceiver {
    val actualReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            receiver(intent)
        }
    }
    LocalBroadcastManager.getInstance(this).registerReceiver(actualReceiver, intentFilter)
    return actualReceiver
}

fun Context.registerLocalReceiver(
    filteredActions: List<String>,
    receiver: (intent: Intent?) -> Unit
): BroadcastReceiver {
    val filter = IntentFilter()
    for (filteredAction in filteredActions) {
        filter.addAction(filteredAction)
    }

    val actualReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            receiver(intent)
        }
    }
    LocalBroadcastManager.getInstance(this).registerReceiver(actualReceiver, filter)
    return actualReceiver
}

fun Context.unregisterLocalReceiver(receiver: BroadcastReceiver) {
    LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
}

fun Context.getPreferences(): AppSettingsSharedPreferences {
    return AppSettings.fromSharedPreferences(this)
}

fun Array<*>.toStringArray(): Array<String> {
    val stringArray = arrayOfNulls<String>(size)
    for ((index, value) in withIndex()) {
        stringArray[index] = value.toString()
    }
    return stringArray as Array<String>
}

fun IntArray.toStringArray(): Array<String> {
    val stringArray = arrayOfNulls<String>(size)
    for ((index, value) in withIndex()) {
        stringArray[index] = value.toString()
    }
    return stringArray as Array<String>
}

fun Activity.restart() {
    val intent = intent
        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    finish()
    startActivity(intent)
}

fun Context.showEmailChooser(chooserTitle: String, subject: String, recipent: String, text: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", recipent, null))
    intent.putExtra(Intent.EXTRA_SUBJECT, subject)
    intent.putExtra(Intent.EXTRA_EMAIL, recipent)
    intent.putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(intent, chooserTitle))
}