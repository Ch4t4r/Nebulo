package com.frostnerd.smokescreen.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.database.entities.DnsRule
import com.frostnerd.smokescreen.database.entities.HostSource
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.util.Notifications
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.minidns.record.Record
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
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

    private val httpClient by lazy {
        OkHttpClient()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent != null && intent.hasExtra("abort")) {
            importJob?.cancel()
        }
        createNotification()
        startWork()
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
            notification!!.setContentTitle(getString(R.string.notification_ruleimport_title))
            notification!!.setContentText(getString(R.string.notification_ruleimport_secondarymessage))
            notification!!.setProgress(100, 0, true)
            val abortPendingAction = PendingIntent.getService(this, 1, Intent(this, RuleImportService::class.java).putExtra("abort", true), PendingIntent.FLAG_CANCEL_CURRENT)
            val abortAction = NotificationCompat.Action(R.drawable.ic_times, getString(R.string.all_abort), abortPendingAction)
            notification!!.addAction(abortAction)
        }
        startForeground(3, notification!!.build())
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
            getDatabase().dnsRuleDao().deleteAllExceptUserRules()
            var count = 0
            val maxCount = getDatabase().hostSourceDao().getEnabledCount()
            getDatabase().hostSourceDao().getAllEnabled().forEach {
                if(importJob != null && importJob?.isCancelled == false) {
                    count++
                    updateNotification(it, count, maxCount.toInt())
                    if (it.isFileSource) {
                        TODO()
                    } else {
                        val request = Request.Builder().url(it.source)
                        val response = httpClient.newCall(request.build()).execute()
                        if (response.isSuccessful) {
                            processLines(it, response.body()!!.byteStream())
                        }
                    }
                }
            }
            importJob = null
            stopForeground(true)
            stopSelf()
        }
    }

    private fun processLines(source: HostSource, stream: InputStream) {
        val parsers = mutableMapOf(
            DNSMASQ_MATCHER to mutableListOf<Host>(),
            HOSTS_MATCHER to mutableListOf(),
            DOMAINS_MATCHER to mutableListOf(),
            ADBLOCK_MATCHER to mutableListOf()
        )
        BufferedReader(InputStreamReader(stream)).useLines { lines ->
            lines.forEach { line ->
                if(importJob != null && importJob?.isCancelled == false) {
                    if (!line.trim().startsWith("#") && !line.trim().startsWith("!") && !line.isBlank()) {
                        val iterator = parsers.iterator()
                        for ((matcher, hosts) in iterator) {
                            if (matcher.reset(line).matches()) {
                                hosts.add(processLine(matcher))
                                commitLines(source, parsers)
                            } else {
                                iterator.remove()
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
        parsers: Map<Matcher, MutableList<Host>>,
        forceCommit: Boolean = false
    ) {
        if (parsers.size == 1) {
            val hosts = parsers[parsers.keys.first()]!!
            if (hosts.size > 5000 || forceCommit) {
                getDatabase().dnsRuleDao().insertAll(hosts.map {
                    DnsRule(it.type, it.host, it.target, source.id)
                })
                hosts.clear()
            }
        }
    }

    private fun processLine(matcher: Matcher): Host {
        when {
            matcher.groupCount() == 1 -> return Host(matcher.group(1), "0.0.0.0", Record.TYPE.ANY)
            matcher == DNSMASQ_MATCHER -> {
                val host = matcher.group(1)
                val target = matcher.group(2)
                return Host(host, target, if (target.contains(":")) Record.TYPE.AAAA else Record.TYPE.A)
            }
            matcher == HOSTS_MATCHER -> {
                val target = matcher.group(1)
                val host = matcher.group(2)
                return Host(host, target, if (target.contains(":")) Record.TYPE.AAAA else Record.TYPE.A)
            }
        }
        throw IllegalStateException()
    }

    override fun onDestroy() {
        super.onDestroy()
        importJob?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private data class Host(val host: String, val target: String, val type: Record.TYPE)
}