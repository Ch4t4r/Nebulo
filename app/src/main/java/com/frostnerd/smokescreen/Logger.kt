package com.frostnerd.smokescreen

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.fragment.app.Fragment
import java.io.*
import java.lang.IllegalStateException
import java.lang.StringBuilder
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */


fun Context.log(text: String, tag: String? = this::class.java.simpleName, vararg formatArgs:Any) {
    if (!Logger.crashed && Logger.enabledGlobally) {
        Logger.getInstance(this).log(text, tag, formatArgs)
    }
}

fun Context.log(text: String, tag: String? = this::class.java.simpleName, intent: Intent?, vararg formatArgs:Any) {
    if (!Logger.crashed && Logger.enabledGlobally) {
        Logger.getInstance(this).log(text, tag, intent, formatArgs)
    }
}

fun Context.log(e: Throwable) {
    if (!Logger.crashed && Logger.enabledGlobally) {
        Logger.getInstance(this).log(e)
    }
}

fun Context.closeLogger() {
    if (Logger.isOpen())
        Logger.getInstance(this).destroy()
}

fun Fragment.log(text: String, tag: String? = this::class.java.simpleName, vararg formatArgs:Any) {
    if (!Logger.crashed && Logger.enabledGlobally) {
        Logger.getInstance(requireContext()).log(text, tag, formatArgs)
    }
}

fun Fragment.log(text: String, tag: String? = this::class.java.simpleName, intent: Intent?, vararg formatArgs:Any) {
    if (!Logger.crashed && Logger.enabledGlobally) {
        Logger.getInstance(requireContext()).log(text, tag, intent, formatArgs)
    }
}

fun Fragment.log(e: Throwable) {
    if (!Logger.crashed && Logger.enabledGlobally) {
        Logger.getInstance(requireContext()).log(e)
    }
}

fun Fragment.closeLogger() {
    if (Logger.isOpen())
        Logger.getInstance(requireContext()).destroy()
}

class Logger private constructor(context: Context) {
    private val logFile: File
    private val fileWriter: BufferedWriter
    var enabled: Boolean = true

    init {
        val logDir = getLogDir(context)
        logFile = File(logDir, "${logFileNameTimeStampFormatter.format(System.currentTimeMillis())}.log")

        if ((!logDir.exists() && !logDir.mkdirs()) || !logFile.createNewFile() || !logFile.canWrite()) {
            Logger.crashed = true
            throw IllegalStateException("Could not create log file.")
        }
        fileWriter = BufferedWriter(FileWriter(logFile, false))

        log("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        log("Android SDK version: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE} - ${Build.VERSION.CODENAME})")
        log("Device: ${Build.MODEL} from ${Build.MANUFACTURER} (Device: ${Build.DEVICE}, Product: ${Build.PRODUCT})")
        log("Language: ${Locale.getDefault().displayLanguage}")
        log("------------------------------", tag = null)
    }

    companion object {
        internal var crashed: Boolean = false
        private var instance: Logger? = null
        var enabledGlobally = true
        var timeStampFormatter = SimpleDateFormat("EEE MMM dd.yy kk:mm:ss", Locale.US)
        var logFileNameTimeStampFormatter = SimpleDateFormat("dd_MM_yyyy___kk_mm_ss", Locale.US)

        internal fun getInstance(context: Context): Logger {
            if (instance == null) {
                instance = Logger(context)
            }
            return instance!!
        }

        fun isOpen(): Boolean {
            return instance != null && !crashed
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
    }

    fun log(text: String, tag: String? = "Info", vararg formatArgs:Any) {
        if (enabled) {
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
                    newString = newString.replace("$${index+1}", arg.toString())
                }
                newString
            })
            textBuilder.append("\n")

            fileWriter.write(textBuilder.toString())
            fileWriter.flush()
        }
    }

    fun log(e: Throwable) {
        if (enabled) {
            val stackTrace = stacktraceToString(e)
            log(stackTrace)
            val errorFile =
                File(logFile.parentFile, "${logFileNameTimeStampFormatter.format(System.currentTimeMillis())}.err")

            if (errorFile.canWrite() && errorFile.createNewFile()) {
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

    fun log(text: String, tag: String? = "Info", intent: Intent?, vararg formatArgs:Any) {
        log(text + " -- ${describeIntent(intent)}", tag, formatArgs)
    }
}

fun Context.zipAllLogFiles(): File? {
    val dir = Logger.getLogDir(this)
    if (!dir.canWrite() || !dir.canRead()) return null

    val zipFile = File(dir, "logs.zip")
    if (zipFile.exists() && (!zipFile.canRead() || !zipFile.canWrite())) return null
    if (zipFile.exists()) zipFile.delete()

    val filesToBeZipped = dir.listFiles { pathname ->
        pathname.name.endsWith(".log") || pathname.name.endsWith(".err")
    }
    val dest = FileOutputStream(zipFile)
    val out = ZipOutputStream(BufferedOutputStream(dest))
    val buffer = ByteArray(2048)

    for (f in filesToBeZipped) {
        val fileInput = FileInputStream(f)
        val inStream = BufferedInputStream(fileInput, buffer.size)
        val entry = ZipEntry(f.name.substring(f.name.lastIndexOf("/") + 1))
        out.putNextEntry(entry)
        do {
            val count = inStream.read(buffer, 0, buffer.size)
            if(count >= 0) out.write(buffer, 0, count)
        } while (count != -1)
        out.flush()
        inStream.close()
    }
    out.close()
    dest.close()
    return zipFile
}