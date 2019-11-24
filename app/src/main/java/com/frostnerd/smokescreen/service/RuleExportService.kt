package com.frostnerd.smokescreen.service

import android.app.IntentService
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.database.entities.DnsRule
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.dialog.ExportType
import com.frostnerd.smokescreen.sendLocalBroadcast
import com.frostnerd.smokescreen.util.DeepActionState
import com.frostnerd.smokescreen.util.Notifications
import com.frostnerd.smokescreen.util.RequestCodes
import com.frostnerd.smokescreen.watchIfEnabled
import leakcanary.LeakSentry
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.io.Serializable
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
class RuleExportService : IntentService("RuleExportService") {
    private var isAborted = false
    private var notification: NotificationCompat.Builder? = null

    companion object {
        const val BROADCAST_EXPORT_DONE = "com.frostnerd.nebulo.RULE_EXPORT_DONE"
    }

    override fun onCreate() {
        super.onCreate()
        LeakSentry.watchIfEnabled(this, "RuleExportService")
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(Notifications.ID_DNSRULE_EXPORT_FINISHED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return if (intent != null && intent.hasExtra("abort")) {
            abortImport()
            START_NOT_STICKY
        } else super.onStartCommand(intent, flags, startId)
    }

    override fun onHandleIntent(intent: Intent?) {
        if(intent != null) {
            createNotification()
            startWork(intent.getSerializableExtra("params") as Params)
        }
    }

    private fun abortImport() {
        isAborted = true
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
            notification!!.setContentIntent(DeepActionState.DNS_RULES.pendingIntentTo(this))
            val abortPendingAction = PendingIntent.getService(
                this,
                RequestCodes.RULE_EXPORT_ABORT,
                Intent(this, RuleExportService::class.java).putExtra("abort", true),
                PendingIntent.FLAG_CANCEL_CURRENT
            )
            val abortAction =
                NotificationCompat.Action(R.drawable.ic_times, getString(android.R.string.cancel), abortPendingAction)
            notification!!.addAction(abortAction)
        }
        startForeground(Notifications.ID_DNSRULE_EXPORT, notification!!.build())
    }

    private fun updateNotification(ruleCount: Int, totalRuleCount: Int) {
        notification?.setProgress(totalRuleCount, ruleCount, false)
        val text = getString(R.string.notification_ruleexport_message, ruleCount, totalRuleCount)
        notification!!.setContentText(text)
        notification!!.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        startForeground(Notifications.ID_DNSRULE_EXPORT, notification!!.build())
    }

    private fun showSuccessNotification(ruleCount: Int) {
        val successNotification = NotificationCompat.Builder(this, Notifications.getDefaultNotificationChannelId(this))
        successNotification.setSmallIcon(R.drawable.ic_mainnotification)
        successNotification.setAutoCancel(true)
        successNotification.setContentTitle(getString(R.string.notification_ruleexportfinished_title))
        successNotification.setContentText(getString(R.string.notification_ruleexportfinished_message, ruleCount))
        successNotification.setContentIntent(DeepActionState.DNS_RULES.pendingIntentTo(this))
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(Notifications.ID_DNSRULE_EXPORT_FINISHED, successNotification.build())
    }

    private fun startWork(params: Params) {
        var stream: BufferedWriter? = null
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
                    it < 20 -> it * 10
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
            if (params.exportUserRules && !isAborted) {
                getDatabase().dnsRuleDao().getAllUserRules(params.exportType == ExportType.WHITELIST,
                    params.exportType == ExportType.NON_WHITELIST).forEach {
                    if (!isAborted) {
                        writtenCount++
                        writeRule(stream, it)
                        if (writtenCount % progressIncrements == 0) {
                            stream.flush()
                            updateNotification(writtenCount, ruleCount)
                        }
                    } else return@forEach
                }
            }
            updateNotification(writtenCount, ruleCount)
            if (params.exportFromSources && !isAborted) {
                val limit = 2000
                var offset = 0
                while (!isAborted && offset < nonUserRuleCount!!) {
                    getDatabase().dnsRuleDao().getAllNonUserRules(offset, limit, params.exportType == ExportType.WHITELIST,
                        params.exportType == ExportType.NON_WHITELIST).forEach {
                        if (!isAborted) {
                            writtenCount++
                            writeRule(stream, it)
                            if (writtenCount % progressIncrements == 0) {
                                stream.flush()
                                updateNotification(writtenCount, ruleCount)
                            }
                        } else return@forEach
                    }
                    offset += limit
                }
            }
            if (!isAborted) {
                stream.flush()
                showSuccessNotification(writtenCount)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        } finally {
            stream?.close()
        }
        stopForeground(true)
        sendLocalBroadcast(Intent(BROADCAST_EXPORT_DONE))
        stopSelf()
    }

    private fun writeRule(stream: BufferedWriter, rule: DnsRule) {
        stream.write(buildString {
            append(rule.target)
            append(" ")
            if(rule.isWildcard) {
                append(rule.host.replace("%%", "**").replace("%", "*"))
            } else append(rule.host)
            append(System.lineSeparator())
            if (rule.ipv6Target != null) {
                append(rule.ipv6Target)
                append(" ")
                if(rule.isWildcard) {
                    append(rule.host.replace("%%", "**").replace("%", "*"))
                } else append(rule.host)
                append(System.lineSeparator())
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        isAborted = true
    }


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    data class Params(
        val exportFromSources: Boolean,
        val exportUserRules: Boolean,
        val exportType: ExportType,
        val targetUri: String
    ) : Serializable
}