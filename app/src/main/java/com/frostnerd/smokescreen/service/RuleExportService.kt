package com.frostnerd.smokescreen.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.database.entities.DnsRule
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.sendLocalBroadcast
import com.frostnerd.smokescreen.util.Notifications
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import leakcanary.LeakSentry
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.io.Serializable
import java.lang.Exception
import java.text.DateFormat
import java.util.*

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
class RuleExportService : Service() {
    private var exportJob: Job? = null
    private var cancelled = false
    private var notification: NotificationCompat.Builder? = null

    companion object {
        const val BROADCAST_EXPORT_DONE = "com.frostnerd.nebulo.RULE_EXPORT_DONE"
    }

    override fun onCreate() {
        super.onCreate()
        LeakSentry.refWatcher.watch(this, "RuleExportService")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotification()
        if (intent?.hasExtra("params") == true) {
            val params = intent.getSerializableExtra("params") as Params
            startWork(params)
        } else if (intent?.hasExtra("abort") == true) {
            cancelled = true
            exportJob?.cancel()
        }
        return START_STICKY
    }

    private fun createNotification() {
        if (notification == null) {
            notification = NotificationCompat.Builder(this, Notifications.servicePersistentNotificationChannel(this))
            notification!!.setSmallIcon(R.drawable.ic_mainnotification)
            notification!!.setOngoing(true)
            notification!!.setAutoCancel(false)
            notification!!.setSound(null)
            notification!!.setOnlyAlertOnce(true)
            notification!!.setUsesChronometer(true)
            notification!!.setContentTitle(getString(R.string.notification_ruleexport_title))
            notification!!.setContentText(getString(R.string.notification_ruleexport_secondarymessage))
            notification!!.setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_ruleexport_secondarymessage)))
            notification!!.setProgress(100, 0, true)
            val abortPendingAction = PendingIntent.getService(
                this,
                1,
                Intent(this, RuleExportService::class.java).putExtra("abort", true),
                PendingIntent.FLAG_CANCEL_CURRENT
            )
            val abortAction =
                NotificationCompat.Action(R.drawable.ic_times, getString(android.R.string.cancel), abortPendingAction)
            notification!!.addAction(abortAction)
        }
        startForeground(5, notification!!.build())
    }

    private fun updateNotification(ruleCount: Int, totalRuleCount: Int) {
        notification?.setProgress(totalRuleCount, ruleCount, false)
        val text = getString(R.string.notification_ruleexport_message, ruleCount, totalRuleCount)
        notification!!.setContentText(text)
        notification!!.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        startForeground(5, notification!!.build())
    }

    private fun showSuccessNotification(ruleCount: Int) {
        val successNotification = NotificationCompat.Builder(this, Notifications.getDefaultNotificationChannelId(this))
        successNotification.setSmallIcon(R.drawable.ic_mainnotification)
        successNotification.setAutoCancel(true)
        successNotification.setContentTitle(getString(R.string.notification_ruleexportfinished_title))
        successNotification.setContentText(getString(R.string.notification_ruleexportfinished_message, ruleCount))
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(6, successNotification.build())
    }


    private fun startWork(params: Params) {
        exportJob = GlobalScope.launch {
            var stream:BufferedWriter? = null
            try {
                stream = BufferedWriter(OutputStreamWriter(contentResolver.openOutputStream(Uri.parse(params.targetUri))!!))
                var ruleCount = 0
                var writtenCount = 0
                var nonUserRuleCount: Int? = null

                if (params.exportUserRules) ruleCount += getDatabase().dnsRuleDao().getUserCount().toInt()
                if (params.exportFromSources) {
                    nonUserRuleCount = getDatabase().dnsRuleDao().getNonUserCount().toInt()
                    ruleCount += nonUserRuleCount
                }
                val progressIncrements = (ruleCount * 0.01).toInt().let {
                    when {
                        it == 0 -> 1
                        it < 20 -> it*10
                        else -> it
                    }
                }
                stream.write(buildString {
                    append("# Dns rules exported from Nebulo")
                    append(System.lineSeparator())
                    append("# Exported at: ")
                    append(DateFormat.getDateTimeInstance().format(Date()))
                    append(System.lineSeparator())
                    append("# Rule count: ")
                    append(ruleCount)
                    append(System.lineSeparator())
                    append("# Format: HOSTS")
                    append(System.lineSeparator())
                })
                updateNotification(0, ruleCount)
                if (params.exportUserRules && !cancelled) {
                    getDatabase().dnsRuleDao().getAllUserRules().forEach {
                        if (!cancelled) {
                            writtenCount++
                            writeRule(stream, it)
                            if(writtenCount % progressIncrements == 0) {
                                stream.flush()
                                updateNotification(writtenCount, ruleCount)
                            }
                        } else return@forEach
                    }
                }
                updateNotification(writtenCount, ruleCount)
                if (params.exportFromSources && !cancelled) {
                    val limit = 2000
                    var offset = 0
                    while (!cancelled && offset < nonUserRuleCount!!) {
                        getDatabase().dnsRuleDao().getAllNonUserRules(offset, limit).forEach {
                            if (!cancelled) {
                                writtenCount++
                                writeRule(stream, it)
                                if(writtenCount % progressIncrements == 0) {
                                    stream.flush()
                                    updateNotification(writtenCount, ruleCount)
                                }
                            } else return@forEach
                        }
                        offset += limit
                    }
                }
                if (!cancelled) {
                    stream.flush()
                    showSuccessNotification(writtenCount)
                }
            } catch (ex:Exception) {
                ex.printStackTrace()
            } finally {
                stream?.close()
            }
            exportJob = null
            stopForeground(true)
            sendLocalBroadcast(Intent(BROADCAST_EXPORT_DONE))
            stopSelf()
        }
    }

    private fun writeRule(stream: BufferedWriter, rule: DnsRule) {
        stream.write(buildString {
            append(rule.target)
            append(" ")
            append(rule.host)
            append(System.lineSeparator())
            if (rule.ipv6Target != null) {
                append(rule.ipv6Target)
                append(" ")
                append(rule.host)
                append(System.lineSeparator())
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelled = true
        exportJob?.cancel()
    }


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    data class Params(
        val exportFromSources: Boolean,
        val exportUserRules: Boolean,
        val targetUri: String
    ) : Serializable
}