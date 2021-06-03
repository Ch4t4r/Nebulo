package com.frostnerd.smokescreen.util.proxy

import android.content.Context
import com.frostnerd.dnstunnelproxy.LocalResolver
import com.frostnerd.smokescreen.database.entities.DnsRule
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.dialog.DnsRuleDialog
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.util.MaxSizeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.minidns.dnsmessage.DnsMessage
import org.minidns.dnsmessage.Question
import org.minidns.record.*
import java.util.*
import kotlin.collections.HashSet
import kotlin.math.abs

class DnsRuleResolver(context: Context) : LocalResolver(false) {
    private val maxWhitelistCacheSize = 250
    private val maxResolvedCacheSize = 500
    private val maxWildcardResolvedCacheSize = 250

    private val dao = context.getDatabase().dnsRuleDao()
    private val resolveResults = mutableMapOf<Int, String>()
    private val wwwRegex = Regex("^www\\.")
    private val useUserRules = context.getPreferences().customHostsEnabled
    private var ruleCount: Int? = null
    private var wildcardCount: Int? = null
    private var whitelistCount: Int? = null
    private var nonWildcardCount:Int? = null
    private var wildcardWhitelistCount:Int? = null
    private var nonWildcardWhitelistCount:Int? = null

    // These sets contain hashes of the hosts, the most significant bit of the hash it 1 for IPv6 and 0 for IPv4
    // Hashes are stored because they are shorter than strings (Int=4 Bytes, String=2-3 per char)
    private var cachedWildcardWhitelisted = HashSet<Int>(15)
    private var cachedNonWildcardWhitelisted = HashSet<Int>(15)
    private var cachedResolved = MaxSizeMap<Int, String>(maxResolvedCacheSize, 40)
    private var cachedWildcardResolved = MaxSizeMap<Int, String>(maxWildcardResolvedCacheSize, 30)
    private var cachedNonIncluded = HashSet<Int>(15)

    private var previousRefreshJob:Job? = null

    init {
        refreshRuleCount()
    }

    fun refreshRuleCount() {
        previousRefreshJob?.cancel()
        previousRefreshJob = GlobalScope.launch(Dispatchers.IO) {
            val previousRuleCount = ruleCount
            ruleCount = dao.getActiveCount().toInt()
            wildcardCount = dao.getActiveWildcardCount().toInt()
            val previousWhitelistCount = whitelistCount
            whitelistCount = dao.getActiveWhitelistCount().toInt()
            nonWildcardCount = ruleCount!! - wildcardCount!!

            wildcardWhitelistCount = dao.getActiveWildcardWhitelistCount().toInt()
            nonWildcardWhitelistCount = whitelistCount!! - wildcardWhitelistCount!!

            if(previousWhitelistCount != whitelistCount) {
                cachedWildcardWhitelisted.clear()
                cachedNonWildcardWhitelisted.clear()
                preloadWhitelistEntries()
            }
            if(previousRuleCount != ruleCount){
                cachedResolved.clear()
                cachedWildcardResolved.clear()
                cachedNonIncluded.clear()
            }
        }
    }

    private fun preloadWhitelistEntries() {
        cachedNonWildcardWhitelisted.addAll(dao.getRandomNonWildcardWhitelistEntries(100).map {
            hashHost(it.toLowerCase(Locale.ROOT), Record.TYPE.ANY)
        })
    }

    private fun findRuleTarget(question: String, type:Record.TYPE):String? {
        val uniformQuestion = question.replace(wwwRegex, "").toLowerCase(Locale.ROOT)
        val hostHash = hashHost(uniformQuestion, type)
        val wildcardHostHash = hashHost(uniformQuestion, Record.TYPE.ANY)

        if(cachedNonIncluded.size != 0 && cachedNonIncluded.contains(hostHash)) return null
        if(whitelistCount != 0) {
            if(cachedNonWildcardWhitelisted.size != 0 && cachedNonWildcardWhitelisted.contains(wildcardHostHash)) return null
            else if(cachedWildcardWhitelisted.size != 0 && cachedWildcardWhitelisted.contains(wildcardHostHash)) return null
        }
        if(nonWildcardCount != 0 && cachedResolved.size != 0) {
            val res = cachedResolved[hostHash]
            if(res != null) {
                return res
            }
        }
        if(wildcardCount != 0 && cachedWildcardResolved.size != 0) {
            val res = cachedWildcardResolved[hostHash]
            if(res != null) {
                return res
            }
        }

        val whitelistEntry: DnsRule? = if (whitelistCount != 0) {
            val normal = if(nonWildcardWhitelistCount != 0 && (nonWildcardWhitelistCount == null || nonWildcardWhitelistCount != whitelistCount)) dao.findNonWildcardWhitelistEntry(
                uniformQuestion,
                useUserRules
            ).firstOrNull() else null
            normal ?: if(wildcardWhitelistCount != 0) dao.findPossibleWildcardRuleTarget(
                uniformQuestion,
                type,
                useUserRules,
                includeWhitelistEntries = true,
                includeNonWhitelistEntries = false
            ).firstOrNull {
                DnsRuleDialog.databaseHostToMatcher(it.host).reset(uniformQuestion)
                    .matches()
            } else null
        } else null

        if (whitelistEntry != null) {
            if(whitelistEntry.isWildcard) cachedWildcardWhitelisted.add(wildcardHostHash)
            else cachedNonWildcardWhitelisted.add(wildcardHostHash)

            if(cachedWildcardWhitelisted.size >= maxWhitelistCacheSize*2) cachedWildcardWhitelisted.clear()
            if(cachedNonWildcardWhitelisted.size >= maxWhitelistCacheSize) cachedNonWildcardWhitelisted.clear()
            return null
        }
        else {
            val resolveResult = if(nonWildcardCount != 0) {
                if(nonWildcardCount == cachedResolved.size) {
                    null // We would have hit cache otherwise
                } else {
                    dao.findRuleTarget(uniformQuestion, type, useUserRules)
                        ?.let {
                            when (it) {
                                "0" -> {
                                    if(type == Record.TYPE.AAAA) "::"
                                    else "0.0.0.0"
                                }
                                "1" -> {
                                    if (type == Record.TYPE.AAAA) "::1"
                                    else "127.0.0.1"
                                }
                                else -> it
                            }
                        }
                }
            } else null
            when {
                resolveResult != null -> {
                    cachedResolved[hostHash] = resolveResult
                    return resolveResult
                }
                wildcardCount != 0 -> {
                    val wildcardResolveResult = dao.findPossibleWildcardRuleTarget(
                        uniformQuestion,
                        type,
                        useUserRules,
                        includeWhitelistEntries = false,
                        includeNonWhitelistEntries = true
                    ).firstOrNull {
                        DnsRuleDialog.databaseHostToMatcher(it.host)
                            .reset(uniformQuestion).matches()
                    }?.let {
                        if (type == Record.TYPE.AAAA) it.ipv6Target
                            ?: it.target
                        else it.target
                    }?.let {
                        when (it) {
                            "0" -> {
                                if(type == Record.TYPE.AAAA) "::"
                                else "0.0.0.0"
                            }
                            "1" -> {
                                if (type == Record.TYPE.AAAA) "::1"
                                else "127.0.0.1"
                            }
                            else -> it
                        }
                    }
                    return if (wildcardResolveResult != null) {
                        cachedWildcardResolved[hostHash] = wildcardResolveResult
                        wildcardResolveResult
                    } else {
                        if(cachedNonIncluded.size >= maxWhitelistCacheSize) cachedNonIncluded.clear()
                        cachedNonIncluded.add(hostHash)
                        null
                    }
                }
                else -> {
                    if(cachedNonIncluded.size >= maxWhitelistCacheSize) cachedNonIncluded.clear()
                    cachedNonIncluded.add(hostHash)
                    return null
                }
            }
        }
    }

    override fun canResolve(question: Question): Boolean {
        if(StaticDnsRuleResolver.staticRules.containsKey(question.name?.toString()?.lowercase())) {
            return true
        }
        return if ((ruleCount == 0 || (ruleCount != null && ruleCount == whitelistCount)) || (question.type != Record.TYPE.A && question.type != Record.TYPE.AAAA)) {
            false
        } else {
            val res = findRuleTarget(question.name.toString(), question.type)
            return if(res != null) {
                resolveResults[question.hashCode()] = res
                true
            } else false
        }
    }

    // A fast hashing function with high(er) collision rate
    // As only a few hosts are stored at the same time the collision rate is not important.
    // The effective room is 2^31
    private fun hashHost(host: String, type: Record.TYPE): Int {
        return if (type == Record.TYPE.AAAA) {
            abs(host.hashCode()) * -1 // Sets first bit to 1
        } else {
            abs(host.hashCode()) // Sets first bit to 0
        }
    }

    override fun resolve(question: Question): List<Record<*>> {
        if(StaticDnsRuleResolver.staticRules.containsKey(question.name?.toString()?.lowercase())) {
            return StaticDnsRuleResolver.staticRules[question.name?.toString()?.lowercase()] ?: emptyList()
        }
        val result = resolveResults.remove(question.hashCode())
        return result?.let {
            val data = if (question.type == Record.TYPE.A) {
                A(it)
            } else {
                AAAA(it)
            }
            listOf(
                Record(
                    question.name.toString(),
                    question.type,
                    question.clazz.value,
                    9999,
                    data
                )
            )
        } ?: throw IllegalStateException()
    }

    override fun cleanup() {
        cachedWildcardWhitelisted.clear()
        cachedNonWildcardWhitelisted.clear()
        cachedResolved.clear()
        cachedWildcardResolved.clear()
        cachedNonIncluded.clear()
    }

    // Handle CNAME Cloaking
    // Does not need to handle whitelist as the query has already been forwarded
    override fun mapResponse(message: DnsMessage): DnsMessage {
        if(ruleCount == 0 || (ruleCount != null && ruleCount == whitelistCount) || message.questions.size == 0) return message // No rules or only whitelist rules present
        else if(whitelistCount != 0 && hashHost(message.question.name.toString().replace(wwwRegex, "").toLowerCase(Locale.ROOT), message.question.type).let {
                cachedWildcardWhitelisted.contains(it) || cachedNonWildcardWhitelisted.contains(it)
            }) return message
        else if(!message.answerSection.any {
                it.type == Record.TYPE.CNAME
            }) return message
        else if(!message.answerSection.any {
                it.type == Record.TYPE.A
            } && ! message.answerSection.any {
                it.type == Record.TYPE.AAAA
            }) return message // The Dns rules only have IPv6 and IPv4

        val ordered = message.answerSection.sortedByDescending { it.type.value } // CNAME at the front

        val mappedAnswers = mutableListOf<Record<*>>()
        val mappedTargets = mutableSetOf<String>()
        for(record in ordered) {
            if(record.type == Record.TYPE.CNAME) {
                val target = (record.payloadData as CNAME).target.toString()
                if(mappedTargets.contains(record.name.toString())) { // Continue skipping the whole CNAME tree
                    mappedTargets.add(target)
                    continue
                }
                mappedAnswers.add(record)
                val originalTargetRecord = followCnameChainToFirstRecord(target, ordered)
                val type = originalTargetRecord?.type ?: message.questions.firstOrNull()?.type ?: Record.TYPE.A
                val ruleData = resolveForCname(target, type)
                if(ruleData != null) {
                    mappedTargets.add(target)
                    mappedAnswers.add(Record(target, type, Record.CLASS.IN, originalTargetRecord?.ttl ?: message.answerSection.minByOrNull {
                        it.ttl
                    }?.ttl ?: record.ttl, ruleData, originalTargetRecord?.unicastQuery ?: false))
                }
            } else if(!mappedTargets.contains(record.name.toString())) mappedAnswers.add(record)
        }

        return if(mappedTargets.size != 0) {
            message.asBuilder().setAnswers(mappedAnswers).build()
        } else {
            message
        }
    }

    private fun followCnameChainToFirstRecord(name:String, records:List<Record<*>>):Record<*>? {
        var target = name
        var recursionDepth = 0
        while(recursionDepth++ < 50) {
            val nextTarget = records.firstOrNull {
                it.name.toString() == target
            }
            if(nextTarget != null && nextTarget.type != Record.TYPE.CNAME) {
                return nextTarget
            } else if(nextTarget == null) return null
            else target = (nextTarget.payloadData as CNAME).target.toString()
        }
        return null
    }

    private fun resolveForCname(host:String, type:Record.TYPE): Data? {
        val entry = findRuleTarget(host, type)

        return if(entry != null) {
            if(type == Record.TYPE.A) A(entry)
            else AAAA(entry)
        } else null
    }

}