package com.frostnerd.smokescreen.util

import android.app.Activity
import android.content.Context

interface AppUpdater {

    fun checkAndTriggerUpdate(context: Activity, requestCode: Int)
}