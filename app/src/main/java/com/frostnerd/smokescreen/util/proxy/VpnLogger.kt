package com.frostnerd.smokescreen.util.proxy

import android.content.Context
import com.frostnerd.smokescreen.BuildConfig
import com.frostnerd.smokescreen.compareTo
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.log
import com.frostnerd.vpntunnelproxy.FutureAnswer
import com.frostnerd.vpntunnelproxy.Logger
import java.util.logging.Level
import kotlin.random.Random

class VpnLogger(private val context: Context): Logger() {
    private val tag = (0..5).map { Random.nextInt('a'.toInt(), 'z'.toInt()).toChar() }.joinToString(separator = "")
    private val minLogLevel =
        if (BuildConfig.DEBUG || context.getPreferences().advancedLogging) Level.FINE
        else if (context.getPreferences().loggingEnabled) Level.INFO
        else Level.OFF

    override fun logMessage(message: () -> String, level: Level) {
        if (level >= minLogLevel) {
            context.log(message(), "$tag, VPN-LIBRARY, $level")
        }
    }

    override fun failedRequest(question: FutureAnswer, reason: Throwable?) {
        if (reason != null && Level.INFO >= minLogLevel) context.log(
            "A request failed: " + com.frostnerd.smokescreen.Logger.stacktraceToString(reason),
            "$tag, VPN-LIBRARY"
        )
    }

    override fun logException(ex: Exception, terminal: Boolean, level: Level) {
        if (terminal) context.log(ex)
        else if(Level.INFO >= minLogLevel) context.log(com.frostnerd.smokescreen.Logger.stacktraceToString(ex), "$tag, VPN-LIBRARY, $level")
    }

    override fun logMessage(message: String, level: Level) {
        if (level >= minLogLevel) {
            context.log(message, "$tag, VPN-LIBRARY, $level")
        }
    }
}
