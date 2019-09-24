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
import com.frostnerd.smokescreen.database.entities.HostSource
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.log
import com.frostnerd.smokescreen.sendLocalBroadcast
import com.frostnerd.smokescreen.util.DeepActionState
import com.frostnerd.smokescreen.util.Notifications
import leakcanary.LeakSentry
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.minidns.record.Record
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
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
class RuleImportService : IntentService("RuleImportService") {
    private val dnsmasqMatcher =
        Pattern.compile("^address=/([^/]+)/(?:([0-9.]+)|([0-9a-fA-F:]+))(?:/?\$|\\s+.*)").matcher("") // address=/xyz.com/0.0.0.0
    private val dnsmasqBlockMatcher = Pattern.compile("^address=/([^/]+)/$").matcher("") // address=/xyz.com/
    private val hostsMatcher =
        Pattern.compile("^((?:[A-Fa-f0-9:]|[0-9.])+)\\s+([*\\w._\\-]+).*")
            .matcher("") // 0.0.0.0 xyz.com
    private val domainsMatcher = Pattern.compile("^([_\\w*][*\\w_\\-.]+)(?:\$|\\s+.*)").matcher("") // xyz.com
    private val adblockMatcher = Pattern.compile("^\\|\\|(.*)\\^(?:\$|\\s+.*)").matcher("") // ||xyz.com^
    private val ruleCommitSize = 10000
    private var notification: NotificationCompat.Builder? = null
    private var ruleCount: Int = 0
    private var isAborted = false

    companion object {
        const val BROADCAST_IMPORT_DONE = "com.frostnerd.nebulo.RULE_IMPORT_DONE"
    }

    private val httpClient by lazy(LazyThreadSafetyMode.NONE) {
        OkHttpClient()
    }

    override fun onCreate() {
        super.onCreate()
        LeakSentry.refWatcher.watch(this, "RuleImportService")
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(Notifications.ID_DNSRULE_IMPORT_FINISHED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return if (intent != null && intent.hasExtra("abort")) {
            abortImport()
            START_NOT_STICKY
        } else super.onStartCommand(intent, flags, startId)
    }

    override fun onHandleIntent(intent: Intent?) {
        createNotification()
        startWork()
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
            notification!!.setContentTitle(getString(R.string.notification_ruleimport_title))
            notification!!.setContentText(getString(R.string.notification_ruleimport_secondarymessage))
            notification!!.setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_ruleimport_secondarymessage)))
            notification!!.setProgress(100, 0, true)
            notification!!.setContentIntent(DeepActionState.DNS_RULES.pendingIntentTo(this))
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
        startForeground(Notifications.ID_DNSRULE_IMPORT, notification!!.build())
    }

    private fun showSuccessNotification() {
        val successNotification = NotificationCompat.Builder(this, Notifications.getDefaultNotificationChannelId(this))
        successNotification.setSmallIcon(R.drawable.ic_mainnotification)
        successNotification.setAutoCancel(true)
        successNotification.setContentTitle(getString(R.string.notification_ruleimportfinished_title))
        val actualRuleCount = getDatabase().dnsRuleDao().getNonUserCount()
        getString(R.string.notification_ruleimportfinished_message,
            actualRuleCount,
            ruleCount - actualRuleCount).apply {
            successNotification.setContentText(this)
            successNotification.setStyle(NotificationCompat.BigTextStyle().bigText(this))
        }
        successNotification.setContentIntent(DeepActionState.DNS_RULES.pendingIntentTo(this))
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(Notifications.ID_DNSRULE_IMPORT_FINISHED, successNotification.build())
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
            startForeground(Notifications.ID_DNSRULE_IMPORT, notification!!.build())
        }
    }

    private fun updateNotificationFinishing() {
        if (notification != null) {
            val notificationText = getString(R.string.notification_ruleimport_tertiarymessage)
            notification?.setContentText(
                notificationText
            )
            notification?.setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            notification?.setProgress(1, 1, true)
            startForeground(Notifications.ID_DNSRULE_IMPORT, notification!!.build())
        }
    }

    private fun startWork() {
        val dnsRuleDao = getDatabase().dnsRuleDao()
        dnsRuleDao.markNonUserRulesForDeletion()
        dnsRuleDao.deleteStagedRules()
        var count = 0
        val maxCount = getDatabase().hostSourceDao().getEnabledCount()
        val newChecksums = mutableMapOf<HostSource, String>()
        getDatabase().hostSourceDao().getAllEnabled().forEach {
            log("Importing HostSource $it")
            if (!isAborted) {
                updateNotification(it, count, maxCount.toInt())
                count++
                if (it.isFileSource) {
                    log("Importing from file")
                    var stream: InputStream? = null
                    try {
                        val uri = Uri.parse(it.source)
                        stream = contentResolver.openInputStream(uri)
                        processLines(it, stream!!)
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
                        val realChecksum = it.checksum?.replace("<qt>", "\"")
                        if(realChecksum != null) request.header("If-None-Match", realChecksum)
                        response = httpClient.newCall(request.build()).execute()
                        val receivedChecksum = response.headers.find {
                            it.first.equals("etag", true)
                        }?.second
                        val localDataIsRecent = response.code == 304 || (realChecksum != null && (receivedChecksum == realChecksum || receivedChecksum == "W/$realChecksum"))
                        when {
                            response.isSuccessful && !localDataIsRecent -> {
                                response.headers.find {
                                    it.first.equals("etag", true)
                                }?.second?.apply {
                                    newChecksums[it] = this.replace("\"", "<qt>")
                                }
                                processLines(it, response.body!!.byteStream())
                            }
                            localDataIsRecent -> {
                                log("Host source ${it.name} hasn't changed, not updating.")
                                ruleCount += it.ruleCount ?: 0
                                dnsRuleDao.unstageRulesOfSource(it.id)
                                log("Unstaged rules for ${it.name}")
                            }
                            else -> log("Downloading resource of ${it.name} failed.")
                        }
                    } catch (ex: java.lang.Exception) {
                        ex.printStackTrace()
                        log("Downloading resource of ${it.name} failed ($ex)")
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
        if (!isAborted) {
            updateNotificationFinishing()
            log("Delete rules staged for deletion")
            dnsRuleDao.deleteMarkedRules()
            log("Commiting staging")
            dnsRuleDao.commitStaging()
            dnsRuleDao.deleteStagedRules()
            log("Recreating database indices")
            getDatabase().recreateDnsRuleIndizes()
            log("Updating Etag values for sources")
            newChecksums.forEach { (source, etag) ->
                source.checksum = etag
                getDatabase().hostSourceDao().update(source)
            }
            getDatabase().hostSourceDao().removeChecksumForDisabled()
            log("Done.")
            showSuccessNotification()
        } else {
            dnsRuleDao.deleteStagedRules()
            dnsRuleDao.commitStaging()
            sendLocalBroadcast(Intent(BROADCAST_IMPORT_DONE))
            stopForeground(true)
        }
        log("All imports finished.")
        stopForeground(true)
        sendLocalBroadcast(Intent(BROADCAST_IMPORT_DONE))
    }

    private fun processLines(source: HostSource, stream: InputStream) {
        val parsers = mutableMapOf(
            dnsmasqMatcher to (0 to mutableListOf<DnsRule>()),
            hostsMatcher to (0 to mutableListOf()),
            domainsMatcher to (0 to mutableListOf()),
            adblockMatcher to (0 to mutableListOf()),
            dnsmasqBlockMatcher to (0 to mutableListOf())
        )
        var lineCount = 0
        var ruleCount = 0
        val sourceId = source.id
        BufferedReader(InputStreamReader(stream)).useLines { lines ->
            lines.forEach { line ->
                if (!isAborted) {
                    if (parsers.isNotEmpty() && !line.trim().startsWith("#") && !line.trim().startsWith("!") && !line.isBlank()) {
                        lineCount++
                        val iterator = parsers.iterator()
                        for ((matcher, hosts) in iterator) {
                            if (matcher.reset(line).matches()) {
                                val rule = processLine(matcher, sourceId, source.whitelistSource)
                                if (rule != null) hosts.second.add(rule.apply {
                                    stagingType = 2
                                })
                                if (lineCount > ruleCommitSize) {
                                    ruleCount += commitLines(parsers)
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
        if(!isAborted) {
            ruleCount += commitLines(parsers, true)
            source.ruleCount = ruleCount
            getDatabase().hostSourceDao().update(source)
        }
    }

    private fun commitLines(
        parsers: Map<Matcher, Pair<Int, MutableList<DnsRule>>>,
        forceCommit: Boolean = false
    ):Int {
        val hosts = parsers[parsers.keys.minBy {
            parsers[it]!!.first
        } ?: parsers.keys.first()]!!.second
        return if (hosts.size > ruleCommitSize || forceCommit) {
            getDatabase().dnsRuleDao().insertAllIgnoreConflict(hosts)
            ruleCount += hosts.size
            val added = hosts.size
            hosts.clear()
            added
        } else 0
    }

    private val wwwRegex = Regex("^www\\.")
    private fun processLine(matcher: Matcher, sourceId: Long, isWhitelist:Boolean): DnsRule? {
        val defaultTargetV4 = if(isWhitelist) "" else "0"
        val defaultTargetV6 = if(isWhitelist) "" else "1"
        when {
            matcher.groupCount() == 1 -> {
                val host = if(matcher == dnsmasqBlockMatcher) "%%" + matcher.group(1)
                else matcher.group(1).replace(wwwRegex, "")
                return createRule(host, defaultTargetV4, defaultTargetV6, Record.TYPE.ANY, sourceId)
            }
            matcher == dnsmasqMatcher -> {
                val host = "%%" + matcher.group(1)
                var target = matcher.group(2)
                val type = if (target.contains(":")) Record.TYPE.AAAA else Record.TYPE.A
                target = target.let {
                    when (it) {
                        "0.0.0.0" -> "0"
                        "127.0.0.1", "::1" -> "1"
                        else -> it
                    }
                }
                return createRule(host, target, null, type, sourceId)
            }
            matcher == hostsMatcher -> {
                return if(isWhitelist) {
                    val host = matcher.group(2).replace(wwwRegex, "")
                    DnsRule(Record.TYPE.ANY, host, defaultTargetV4, defaultTargetV6, importedFrom = sourceId)
                } else {
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
                    return createRule(host, target, null, type, sourceId)
                }
            }
        }
        throw IllegalStateException()
    }

    private fun createRule(host: String, target: String, targetV6:String? = null, type: Record.TYPE, sourceId: Long): DnsRule? {
        var isWildcard = false
        val alteredHost = host.let {
            if(it.contains("*")) {
                isWildcard = true
                it.replace("**", "%%").replace("*", "%")
            } else it
        }
        return DnsRule(type, alteredHost, target, targetV6, sourceId, isWildcard)
    }

    override fun onDestroy() {
        super.onDestroy()
        abortImport()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}