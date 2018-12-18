package com.frostnerd.smokescreen

import android.app.Application
import kotlin.system.exitProcess

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */
class SmokeScreen : Application() {
    private var defaultUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null
    val customUncaughtExceptionHandler: Thread.UncaughtExceptionHandler =
        Thread.UncaughtExceptionHandler { t, e ->
            log(e)
            closeLogger()
            defaultUncaughtExceptionHandler?.uncaughtException(t, e)
            exitProcess(0)
        }

    override fun onCreate() {
        defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(customUncaughtExceptionHandler)
        super.onCreate()
    }

}