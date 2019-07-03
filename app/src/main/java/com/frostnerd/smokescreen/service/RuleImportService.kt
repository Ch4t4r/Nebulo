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
import com.frostnerd.smokescreen.database.entities.HostSource
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.log
import com.frostnerd.smokescreen.sendLocalBroadcast
import com.frostnerd.smokescreen.util.Notifications
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
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
    private val DNSMASQ_MATCHER =
        Pattern.compile("^address=/([^/]+)/(?:([0-9.]+)|([0-9a-fA-F:]+))(?:$|\\s+.*)").matcher("")
    private val HOSTS_MATCHER =
        Pattern.compile("^((?:[A-Fa-f0-9:]|[0-9.])+)\\s+([\\w._\\-]+).*")
            .matcher("")
    private val DOMAINS_MATCHER = Pattern.compile("^([_\\w][\\w_\\-.]+)(?:\$|\\s+.*)").matcher("")
    private val ADBLOCK_MATCHER = Pattern.compile("^\\|\\|(.*)\\^(?:\$|\\s+.*)").matcher("")
    private val ruleCommitSize = 10000
    private var notification: NotificationCompat.Builder? = null
    private var ruleCount: Int = 0
    private var checkDuplicates:Boolean = false

    companion object {
        const val BROADCAST_IMPORT_DONE = "com.frostnerd.nebulo.RULE_IMPORT_DONE"
    }

    private val httpClient by lazy {
        OkHttpClient()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.hasExtra("abort")) {
            abortImport()
        }
        createNotification()
        startWork()
        return START_STICKY
    }

    private fun abortImport() {
        importJob?.let {
            importJob = null
            it.cancel()
            GlobalScope.launch {
                val dnsRuleDao = getDatabase().dnsRuleDao()
                dnsRuleDao.deleteStagedRules()
                dnsRuleDao.commitStaging()
            }
            stopForeground(true)
            sendLocalBroadcast(Intent(BROADCAST_IMPORT_DONE))
            stopSelf()
        }
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
            notification!!.setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_ruleimport_secondarymessage)))
            notification!!.setProgress(100, 0, true)
            val abortPendingAction = PendingIntent.getService(
                this,
                1,
                Intent(this, RuleImportService::class.java).putExtra("abort", true),
                PendingIntent.FLAG_CANCEL_CURRENT
            )
            val abortAction =
                NotificationCompat.Action(R.drawable.ic_times, getString(android.R.string.cancel), abortPendingAction)
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

    private fun updateNotification(source: HostSource, count: Int, maxCount: Int) {
        if (notification != null) {
            val notificationText = getString(
                R.string.notification_ruleimport_message,
                source.name,
                source.source
            )
            notification?.setContentText(
                notificationText
            )
            notification?.setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            notification?.setProgress(maxCount, count, false)
            startForeground(3, notification!!.build())
        }
    }

    private fun updateNotificationFinishing() {
        if(notification != null) {
            val notificationText = getString(R.string.notification_ruleimport_tertiarymessage)
            notification?.setContentText(
                notificationText
            )
            notification?.setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            notification?.setProgress(1, 1, true)
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
                if (importJob != null && importJob?.isCancelled == false) {
                    updateNotification(it, count, maxCount.toInt())
                    count++
                    if (it.isFileSource) {
                        log("Importing from file")
                        var stream: InputStream? = null
                        try {
                            val uri = Uri.parse(it.source)
                            stream = contentResolver.openInputStream(uri)
                            processLines(it, stream)
                        } catch (ex: Exception) {
                            log("Import failed: $ex")
                            ex.printStackTrace()
                        } finally {
                            stream?.close()
                        }
                    } else {
                        log("Importing from URL")
                        var response: Response? = null
                        try {
                            val request = Request.Builder().url(it.source)
                            response = httpClient.newCall(request.build()).execute()
                            if (response.isSuccessful) {
                                processLines(it, response.body!!.byteStream())
                            } else {
                                log("Downloading resource of $it failed.")
                            }
                        } catch (ex: java.lang.Exception) {
                            log("Downloading resource of $it failed ($ex)")
                        } finally {
                            response?.body?.close()
                        }
                    }
                    log("Import of $it finished")
                } else {
                    log("Aborting import.")
                    return@forEach
                }
            }
            if (importJob != null && importJob?.isCancelled == false) {
                updateNotificationFinishing()
                dnsRuleDao.deleteMarkedRules()
                dnsRuleDao.commitStaging()
                getDatabase().recreateDnsRuleIndizes()
                showSuccessNotification()
            }
            log("All imports finished.")
            importJob = null
            stopForeground(true)
            sendLocalBroadcast(Intent(BROADCAST_IMPORT_DONE))
            stopSelf()
        }
    }

    private fun processLines(source: HostSource, stream: InputStream) {
        val parsers = mutableMapOf(
            DNSMASQ_MATCHER to (0 to mutableListOf<DnsRule>()),
            HOSTS_MATCHER to (0 to mutableListOf()),
            DOMAINS_MATCHER to (0 to mutableListOf()),
            ADBLOCK_MATCHER to (0 to mutableListOf())
        )
        var lineCount = 0
        val sourceId = source.id
        BufferedReader(InputStreamReader(stream)).useLines { lines ->
            lines.forEach { line ->
                if (importJob != null && importJob?.isCancelled == false) {
                    if (parsers.isNotEmpty() && !line.trim().startsWith("#") && !line.trim().startsWith("!") && !line.isBlank()) {
                        lineCount++
                        val iterator = parsers.iterator()
                        for ((matcher, hosts) in iterator) {
                            if (matcher.reset(line).matches()) {
                                val rule = processLine(matcher, sourceId)
                                if(rule != null) hosts.second.add(rule)
                                if(lineCount > ruleCommitSize) {
                                    commitLines(parsers)
                                    lineCount = 0
                                }
                            } else {
                                if (hosts.first > 5) {
                                    log("Matcher $matcher failed 5 times, last for '$line'. Removing.")
                                    iterator.remove()
                                } else parsers[matcher] = hosts.copy(hosts.first + 1)
                                if (parsers.isEmpty()) {
                                    log("No parsers left. Aborting.")
                                }
                            }
                        }
                    }
                } else {
                    return@useLines
                }
            }
        }
        commitLines(parsers, true)
    }

    private fun commitLines(
        parsers: Map<Matcher, Pair<Int, MutableList<DnsRule>>>,
        forceCommit: Boolean = false
    ) {
        val hosts = parsers[parsers.keys.minBy {
            parsers[it]!!.first
        } ?: parsers.keys.first()]!!.second
        if (hosts.size > ruleCommitSize || forceCommit) {
            getDatabase().dnsRuleDao().insertAllIgnoreConflict(hosts)
            ruleCount += hosts.size
            hosts.clear()
        }
    }

    private val wwwRegex = Regex("^www\\.")
    private fun processLine(matcher: Matcher, sourceId:Long): DnsRule? {
        when {
            matcher.groupCount() == 1 -> {
                val host = matcher.group(1).replace(wwwRegex, "")
                return if(checkDuplicates) {
                    val existingIpv4 = getDatabase().dnsRuleDao().getNonUserRule(host, Record.TYPE.A)
                    val existingIpv6 = getDatabase().dnsRuleDao().getNonUserRule(host, Record.TYPE.AAAA)
                    if(existingIpv4 == null && existingIpv6 == null) DnsRule(Record.TYPE.ANY, host, "0", "1", importedFrom = sourceId)
                    else if(existingIpv4 == null) DnsRule(Record.TYPE.A, host, "0", importedFrom = sourceId)
                    else if(existingIpv6 == null) DnsRule(Record.TYPE.AAAA, host, "1", importedFrom = sourceId)
                    else null
                } else DnsRule(Record.TYPE.ANY, host, "0", "1", importedFrom = sourceId)
            }
            matcher == DNSMASQ_MATCHER -> {
                val host = matcher.group(1).replace(wwwRegex, "")
                var target = matcher.group(2)
                val type = if (target.contains(":")) Record.TYPE.AAAA else Record.TYPE.A
                target = target.let {
                    when (it) {
                        "0.0.0.0" -> "0"
                        "127.0.0.1", "::1" -> "1"
                        else -> it
                    }
                }
                return createRuleIfNotExists(host, target, type, sourceId)
            }
            matcher == HOSTS_MATCHER -> {
                var target = matcher.group(1)
                val type = if (target.contains(":")) Record.TYPE.AAAA else Record.TYPE.A
                target = target.let {
                    when (it) {
                        "0.0.0.0" -> "0"
                        "127.0.0.1", "::1" -> "1"
                        else -> it
                    }
                }
                val host = matcher.group(2).replace(wwwRegex, "")
                return createRuleIfNotExists(host, target, type, sourceId)
            }
        }
        throw IllegalStateException()
    }

    private fun createRuleIfNotExists(host:String, target:String, type:Record.TYPE, sourceId:Long):DnsRule? {
        return if(checkDuplicates) {
            val existingRule = getDatabase().dnsRuleDao().getNonUserRule(host, type)
            if(existingRule == null) DnsRule(type, host, target, importedFrom = sourceId)
            else null
        } else DnsRule(type, host, target, importedFrom = sourceId)
    }


    override fun onDestroy() {
        super.onDestroy()
        abortImport()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}