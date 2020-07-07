package com.frostnerd.smokescreen.util

import com.frostnerd.smokescreen.Logger
import java.io.DataOutputStream
import java.lang.Exception

/*
 * Copyright (C) 2020 Daniel Wolf (Ch4t4r)
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

/**
 * @return Whether the command has been executed successfully
 */
fun processSuCommand(command:String, logger:Logger?):Boolean {
    return try {
        logger?.log("Running command '$command' with SU", "CommandUtil")
        val su = runCommandWithSU(command)
        su.errorStream.readBytes().takeIf {
            it.isNotEmpty()
        }?.apply {
            logger?.log("Command '$command' yielded error output ${String(this)}", "CommandUtil")
        }

        su.waitFor()
        return su.exitValue() == 0
    } catch (ex: Exception) {
        logger?.log("Command '$command' yielded exception: ${Logger.stacktraceToString(ex)}", "CommandUtil")
        false
    }
}

private fun runCommandWithSU(command:String):Process {
    val su = Runtime.getRuntime().exec("su")
    DataOutputStream(su.outputStream).apply {
        writeBytes(command)
        writeBytes("\n")
        writeBytes("exit")
        writeBytes("\n")
        flush()
    }.close()
    return su
}