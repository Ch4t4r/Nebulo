package com.frostnerd.smokescreen

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.fragment.app.Fragment
import com.frostnerd.smokescreen.database.AppDatabase
import com.frostnerd.smokescreen.database.EXECUTED_MIGRATIONS
import com.frostnerd.smokescreen.util.preferences.Crashreporting
import io.sentry.core.Sentry
import io.sentry.core.SentryEvent
import io.sentry.core.SentryLevel
import io.sentry.core.protocol.Message
import leakcanary.LeakSentry
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.withLock

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

private val publishedExceptions = mutableMapOf<Throwable, Set<StackTraceElement>>()
private fun Context.logErrorSentry(e: Throwable, extras: Map<String, String>? = null) {
    if(getPreferences().crashreportingType == Crashreporting.OFF) return
    if (publishedExceptions.any {
            it.value.all { elem ->
                e.stackTrace.contains(elem)
            }
        } || publishedExceptions.put(e, e.stackTrace.toHashSet()) != null) return
    else {
        EXECUTED_MIGRATIONS.sortedBy { it.first }.joinToString {
            "${it.first} -> ${it.second}"
        }.takeIf { it.isNotBlank() }?.apply {
            Sentry.setExtra("database_migrations", this)
        }
        if (e is OutOfMemoryError) {
            Sentry.captureEvent(SentryEvent(e).apply {
                message = Message().apply {
                    this.message = e.message
                }
                level = SentryLevel.ERROR
                setExtra("retainedInstanceCount", LeakSentry.refWatcher.retainedInstanceCount)
            })
        } else if (getPreferences().crashreportingType == Crashreporting.FULL && extras != null && extras.isNotEmpty()) {
            // Extra data is only passed when not in data-saving mode.
            Sentry.captureEvent(SentryEvent(e).apply {
                message = Message().apply {
                    this.message = e.message
                }
                level = SentryLevel.ERROR
                extras.forEach { (key, value) ->
                    setTag(key, value)
                }
                setExtra("retainedInstanceCount", LeakSentry.refWatcher.retainedInstanceCount)
            })
        } else {
            Sentry.captureException(e)
        }
    }
}

fun Context.log(text: String, tag: String? = this::class.java.simpleName, vararg formatArgs: Any) {
    if (Logger.isEnabled(this)) {
        Logger.getInstance(this).log(text, tag, formatArgs)
    }
}

fun Context.log(
    text: String,
    tag: String? = this::class.java.simpleName,
    intent: Intent?,
    vararg formatArgs: Any
) {
    if (Logger.isEnabled(this)) {
        Logger.getInstance(this).log(text, tag, intent, formatArgs)
    }
}

fun Context.log(e: Throwable, extras: Map<String, String>? = null) {
    if(SmokeScreen.sentryReady) logErrorSentry(e, extras)
    getPreferences().lastCrashTimeStamp = System.currentTimeMillis()
    if (Logger.isEnabled(this)) {
        Logger.getInstance(this).log(e)
    } else {
        val stackTrace = Logger.stacktraceToString(e)
        val errorFile =
            File(
                Logger.getLogDir(this).parentFile,
                "${Logger.logFileNameTimeStampFormatter.format(System.currentTimeMillis())}.err"
            )

        if (errorFile.createNewFile()) {
            val writer = BufferedWriter(FileWriter(errorFile, false))
            writer.write("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, Commit: ${BuildConfig.COMMIT_HASH})\n")
            writer.write("Android SDK version: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE} - ${Build.VERSION.CODENAME})\n")
            writer.write("Device: ${Build.MODEL} from ${Build.MANUFACTURER} (Device: ${Build.DEVICE}, Product: ${Build.PRODUCT})\n")
            writer.write("------------------------------\n")
            writer.write("$stackTrace\n")
            writer.close()
        }
    }
}

fun Context.closeLogger() {
    if (Logger.isOpen())
        Logger.getInstance(this).destroy()
}

fun Context.deleteAllLogs() {
    if (Logger.isOpen())
        Logger.getInstance(this).destroy()
    Logger.getLogDir(this).listFiles().forEach {
        it.delete()
    }
}

fun Fragment.log(text: String, tag: String? = this::class.java.simpleName, vararg formatArgs: Any) {
    if (context != null) requireContext().log(text, tag, formatArgs)
}

fun Fragment.log(
    text: String,
    tag: String? = this::class.java.simpleName,
    intent: Intent?,
    vararg formatArgs: Any
) {
    if (context != null) requireContext().log(text, tag, intent, formatArgs)
}

fun Fragment.log(e: Throwable) {
    if (context != null) requireContext().log(e)
}

class Logger private constructor(context: Context) {
    private val logFile: File
    private val fileWriter: BufferedWriter
    private val printToConsole = BuildConfig.DEBUG
    private var oldPrintStream: PrintStream?
    private var oldSystemOut: PrintStream?
    private val lock = ReentrantLock()
    var enabled: Boolean = true
    private val id: Int

    init {
        val logDir = getLogDir(context)
        id = ++context.getPreferences().lastLogId
        logFile = File(
            logDir,
            "${id}_${logFileNameTimeStampFormatter.format(System.currentTimeMillis())}.log"
        )
        logDir.mkdirs()
        logFile.createNewFile()
        fileWriter = BufferedWriter(FileWriter(logFile, false))

        log("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        log("Database version: ${AppDatabase.currentVersion}")
        log("Android SDK version: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE} - ${Build.VERSION.CODENAME})")
        log("Device: ${Build.MODEL} from ${Build.MANUFACTURER} (Device: ${Build.DEVICE}, Product: ${Build.PRODUCT})")
        log("Language: ${Locale.getDefault().displayLanguage}")
        log("------------------------------", tag = null)
        oldPrintStream = System.err
        oldSystemOut = System.out
        System.setErr(object : PrintStream(oldPrintStream!!) {
            override fun println(x: String?) {
                super.println(x)
                log(x ?: "", "System.Err")
            }
        })
        System.setOut(object : PrintStream(oldSystemOut!!) {
            override fun println(x: String?) {
                super.println(x)
                log(x ?: "", "System.Out")
            }
        })
    }

    companion object {
        private var instance: Logger? = null
        var timeStampFormatter = SimpleDateFormat("EEE MMM dd.yy kk:mm:ss", Locale.US)
        var logFileNameTimeStampFormatter = SimpleDateFormat("dd_MM_yyyy___kk_mm_ss", Locale.US)
        private var enabled: Boolean? = null

        internal fun getInstance(context: Context): Logger {
            if (instance == null) {
                instance = Logger(context)
            }
            return instance!!
        }

        internal fun isEnabled(context: Context): Boolean {
            return enabled ?: run {
                enabled = context.getPreferences().loggingEnabled
                enabled!!
            }
        }

        internal fun setEnabled(enabled: Boolean) {
            this.enabled = enabled
        }

        fun logIfOpen(tag: String, text: String) {
            if (enabled == true)
                instance?.log(text, tag)
        }

        fun isOpen(): Boolean {
            return instance != null
        }

        fun getLogDir(context: Context): File {
            return File(context.filesDir, "logs/")
        }

        fun stacktraceToString(e: Throwable): String {
            val stringWriter = StringWriter()
            e.printStackTrace(PrintWriter(stringWriter))
            return stringWriter.toString()
        }

        fun describeIntent(intent: Intent?): String {
            if (intent == null) {
                return "Intent{NullIntent}"
            } else {
                return buildString {
                    append("Intent{Action:").append(intent.action).append("; ")
                    append("Type:").append(intent.type).append("; ")
                    append("Package:").append(intent.`package`).append("; ")
                    append("Scheme:").append(intent.scheme).append("; ")
                    append("Data:").append(intent.dataString).append("; ")
                    append("Component:").append(intent.component).append("; ")
                    append("Categories:").append(intent.categories).append("; ")
                    append("Flags:").append(intent.flags).append(";")
                    append("Extras:[")
                    if (intent.extras != null) {
                        val keys = intent.extras!!.keySet().iterator()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            append(key).append("->").append(intent.extras!!.get(key))
                            if (keys.hasNext()) append("; ")
                        }
                    }
                    append("]")
                    append("}")
                }
            }
        }
    }

    fun destroy() {
        enabled = false
        instance = null
        fileWriter.close()
        System.setErr(oldPrintStream!!)
        System.setOut(oldSystemOut!!)
    }

    fun log(text: String, tag: String? = "Info", vararg formatArgs: Any) {
        if (enabled) {
            lock.withLock {
                val textBuilder = StringBuilder()
                textBuilder.append("[")
                textBuilder.append(System.currentTimeMillis())
                textBuilder.append("][")
                textBuilder.append(timeStampFormatter.format(System.currentTimeMillis()))
                textBuilder.append("]>")
                textBuilder.append("[")
                if (tag != null) textBuilder.append(tag)
                textBuilder.append("]: ")
                textBuilder.append(text.let {
                    var newString = it
                    formatArgs.forEachIndexed { index, arg ->
                        newString = newString.replace("$${index + 1}", arg.toString())
                    }
                    newString
                })
                textBuilder.append("\n")
                if (printToConsole) {
                    (oldSystemOut ?: System.out).println(textBuilder)
                }
                fileWriter.write(textBuilder.toString())
                fileWriter.flush()
            }
        }
    }

    fun log(e: Throwable) {
        if (enabled) {
            val stackTrace = stacktraceToString(e)
            log(stackTrace)
            val errorFile =
                File(
                    logFile.parentFile,
                    "${id}_${logFileNameTimeStampFormatter.format(System.currentTimeMillis())}.err"
                )

            if (errorFile.createNewFile()) {
                val writer = BufferedWriter(FileWriter(errorFile, false))
                writer.write("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                writer.write("Android SDK version: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE} - ${Build.VERSION.CODENAME})\n")
                writer.write("Device: ${Build.MODEL} from ${Build.MANUFACTURER} (Device: ${Build.DEVICE}, Product: ${Build.PRODUCT})\n")
                writer.write("Language: ${Locale.getDefault().displayLanguage}\n")
                writer.write("------------------------------\n")
                writer.write("$stackTrace\n")
                writer.close()
            }
        }
    }

    fun log(text: String, tag: String? = "Info", intent: Intent?, vararg formatArgs: Any) {
        log(text + " -- ${describeIntent(intent)}", tag, formatArgs)
    }
}

fun Context.zipAllLogFiles(): File? {
    val dir = Logger.getLogDir(this)
    if (!dir.canWrite() || !dir.canRead()) return null

    val zipFile = File(dir, "logs.zip")
    if (zipFile.exists() && (!zipFile.canRead() || !zipFile.canWrite())) return null
    if (zipFile.exists()) zipFile.delete()

    var filesToBeZipped = dir.listFiles()
    val dest = FileOutputStream(zipFile)
    val out = ZipOutputStream(BufferedOutputStream(dest))
    val buffer = ByteArray(2048)

    val queryGenLogFile = File(filesDir, "querygenlog.txt")

    if (queryGenLogFile.exists()) {
        filesToBeZipped += queryGenLogFile
    }
    for (f in filesToBeZipped) {
        val fileInput = FileInputStream(f)
        val inStream = BufferedInputStream(fileInput, buffer.size)
        val entry = ZipEntry(f.name.substring(f.name.lastIndexOf("/") + 1))
        out.putNextEntry(entry)
        do {
            val count = inStream.read(buffer, 0, buffer.size)
            if (count >= 0) out.write(buffer, 0, count)
        } while (count != -1)
        out.flush()
        inStream.close()
    }
    out.close()
    dest.close()
    return zipFile
}