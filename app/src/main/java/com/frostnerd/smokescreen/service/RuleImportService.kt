package com.frostnerd.smokescreen.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.database.entities.DnsRule
import com.frostnerd.smokescreen.database.entities.HostSource
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.log
import com.frostnerd.smokescreen.util.Notifications
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.minidns.record.Record
import java.io.*
import java.lang.IllegalStateException
import java.util.regex.Matcher
import java.util.regex.Pattern

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
class RuleImportService : Service() {
    private var importJob: Job? = null
    private val DNSMASQ_MATCHER = Pattern.compile("^address=/([^/]+)/(?:([0-9.]+)|([0-9a-fA-F:]+))").matcher("")
    private val HOSTS_MATCHER =
        Pattern.compile("^((?:[A-Fa-f0-9:]|[0-9.])+)\\s+([a-zA-Z0-9.]+).*")
            .matcher("")
    private val DOMAINS_MATCHER = Pattern.compile("^([A-Za-z0-9][A-Za-z0-9\\-.]+)").matcher("")
    private val ADBLOCK_MATCHER = Pattern.compile("^\\|\\|(.*)\\^$").matcher("")
    private var notification: NotificationCompat.Builder? = null
    private var ruleCount:Int = 0

    private val httpClient by lazy {
        OkHttpClient()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent != null && intent.hasExtra("abort")) {
            abortImport()
        }
        createNotification()
        startWork()
        return START_STICKY
    }

    private fun abortImport() {
        importJob?.let {
            it.cancel()
            val dnsRuleDao = getDatabase().dnsRuleDao()
            dnsRuleDao.deleteStagedRules()
            dnsRuleDao.commitStaging()
        }
        importJob = null
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
            notification!!.setContentTitle(getString(R.string.notification_ruleimport_title))
            notification!!.setContentText(getString(R.string.notification_ruleimport_secondarymessage))
            notification!!.setProgress(100, 0, true)
            val abortPendingAction = PendingIntent.getService(this, 1, Intent(this, RuleImportService::class.java).putExtra("abort", true), PendingIntent.FLAG_CANCEL_CURRENT)
            val abortAction = NotificationCompat.Action(R.drawable.ic_times, getString(R.string.all_abort), abortPendingAction)
            notification!!.addAction(abortAction)
        }
        startForeground(3, notification!!.build())
    }

    private fun showSuccessNotification() {
        val successNotification = NotificationCompat.Builder(this, Notifications.getDefaultNotificationChannelId(this))
        successNotification.setSmallIcon(R.drawable.ic_mainnotification)
        successNotification.setAutoCancel(true)
        successNotification.setContentTitle(getString(R.string.notification_ruleimportfinished_title))
        successNotification.setContentText(getString(R.string.notification_ruleimportfinished_message, ruleCount))
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(4, successNotification.build())
    }

    private fun updateNotification(source: HostSource, count:Int, maxCount:Int) {
        if (notification != null) {
            notification?.setContentText(
                getString(
                    R.string.notification_ruleimport_message,
                    source.name,
                    source.source
                )
            )
            notification?.setProgress(maxCount, count, false)
            startForeground(3, notification!!.build())
        }
    }

    private fun startWork() {
        importJob = GlobalScope.launch {
            val dnsRuleDao = getDatabase().dnsRuleDao()
            dnsRuleDao.markNonUserRulesForDeletion()
            var count = 0
            val maxCount = getDatabase().hostSourceDao().getEnabledCount()
            getDatabase().hostSourceDao().getAllEnabled().forEach {
                log("Importing HostSource $it")
                if(importJob != null && importJob?.isCancelled == false) {
                    updateNotification(it, count, maxCount.toInt())
                    count++
                    if (it.isFileSource) {
                        try {
                            val file = File(it.source)
                            if(file.canRead()) {
                                processLines(it, FileInputStream(file))
                            }
                        } catch (ex:Exception) {
                            ex.printStackTrace()
                        }
                    } else {
                        val request = Request.Builder().url(it.source)
                        val response = httpClient.newCall(request.build()).execute()
                        if (response.isSuccessful) {
                            processLines(it, response.body()!!.byteStream())
                        } else {
                            log("Downloading resource of $it failed.")
                        }
                    }
                }
            }
            if(importJob != null && importJob?.isCancelled == false) {
                dnsRuleDao.deleteMarkedRules()
                dnsRuleDao.commitStaging()
            }
            importJob = null
            showSuccessNotification()
            stopForeground(true)
            stopSelf()
        }
    }

    private fun processLines(source: HostSource, stream: InputStream) {
        val parsers = mutableMapOf(
            DNSMASQ_MATCHER to (0 to mutableListOf<Host>()),
            HOSTS_MATCHER to (0 to mutableListOf()),
            DOMAINS_MATCHER to (0 to mutableListOf()),
            ADBLOCK_MATCHER to (0 to mutableListOf())
        )
        BufferedReader(InputStreamReader(stream)).useLines { lines ->
            lines.forEach { line ->
                if(importJob != null && importJob?.isCancelled == false) {
                    if (parsers.isNotEmpty() && !line.trim().startsWith("#") && !line.trim().startsWith("!") && !line.isBlank()) {
                        val iterator = parsers.iterator()
                        for ((matcher, hosts) in iterator) {
                            if (matcher.reset(line).matches()) {
                                hosts.second.addAll(processLine(matcher))
                                commitLines(source, parsers)
                            } else {
                                log("Matcher $matcher mismatch for line $line")
                                if(hosts.first > 5) iterator.remove()
                                else parsers[matcher] = hosts.copy(hosts.first + 1)
                            }
                        }
                    }
                } else {
                    return@useLines
                }
            }
        }
        commitLines(source, parsers, true)
    }

    private fun commitLines(
        source: HostSource,
        parsers: Map<Matcher, Pair<Int, MutableList<Host>>>,
        forceCommit: Boolean = false
    ) {
        if (parsers.size == 1) {
            val hosts = parsers[parsers.keys.first()]!!.second
            if (hosts.size > 5000 || forceCommit) {
                getDatabase().dnsRuleDao().insertAll(hosts.map {
                    DnsRule(it.type, it.host, it.target, source.id)
                })
                ruleCount += hosts.size
                hosts.clear()
            }
        }
    }

    private fun processLine(matcher: Matcher): Collection<Host> {
        when {
            matcher.groupCount() == 1 -> return listOf(Host(matcher.group(1), "0.0.0.0", Record.TYPE.A), Host(matcher.group(1), "::1", Record.TYPE.AAAA))
            matcher == DNSMASQ_MATCHER -> {
                val host = matcher.group(1)
                val target = matcher.group(2)
                return listOf(Host(host, target, if (target.contains(":")) Record.TYPE.AAAA else Record.TYPE.A))
            }
            matcher == HOSTS_MATCHER -> {
                val target = matcher.group(1)
                val host = matcher.group(2)
                return listOf(Host(host, target, if (target.contains(":")) Record.TYPE.AAAA else Record.TYPE.A))
            }
        }
        throw IllegalStateException()
    }

    override fun onDestroy() {
        super.onDestroy()
        abortImport()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private data class Host(val host: String, val target: String, val type: Record.TYPE)
}