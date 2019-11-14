package com.frostnerd.smokescreen.util.proxy

import android.content.Context
import com.frostnerd.dnstunnelproxy.LocalResolver
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.dialog.DnsRuleDialog
import com.frostnerd.smokescreen.getPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.minidns.dnsmessage.Question
import org.minidns.record.A
import org.minidns.record.AAAA
import org.minidns.record.Record

class DnsRuleResolver(context: Context): LocalResolver(true) {
    private val dao = context.getDatabase().dnsRuleDao()
    private val resolveResults = mutableMapOf<Question, String>()
    private val wwwRegex = Regex("^www\\.")
    private val useUserRules = context.getPreferences().customHostsEnabled
    private var ruleCount:Int? = null
    private var wildcardCount:Int? = null
    private var whitelistCount:Int? = null

    init {
        refreshRuleCount(context)
    }

    fun refreshRuleCount(context: Context) {
        GlobalScope.launch {
            ruleCount = dao.getCount().toInt()
            wildcardCount = dao.getWildcardCount().toInt()
            whitelistCount = dao.getWhitelistCount().toInt()
        }
    }

    override suspend fun canResolve(question: Question): Boolean {
        return if ((ruleCount ==  0 || (ruleCount != null && ruleCount == whitelistCount)) || (question.type != Record.TYPE.A && question.type != Record.TYPE.AAAA)) {
            false
        } else {
            val uniformQuestion = question.name.toString().replace(wwwRegex, "")
            val isWhitelisted = if(whitelistCount != 0) {
                (wildcardCount != 0 && dao.findPossibleWildcardRuleTarget(
                    uniformQuestion,
                    question.type,
                    useUserRules,
                    true,
                    false
                ).any {
                    DnsRuleDialog.databaseHostToMatcher(it.host).reset(uniformQuestion)
                        .matches()
                }) || dao.findNonWildcardWhitelistEntry(
                    uniformQuestion,
                    useUserRules
                ).isNotEmpty()
            } else false

            if (isWhitelisted) false
            else {
                val resolveResult =
                    dao.findRuleTarget(uniformQuestion, question.type, useUserRules)
                        ?.let {
                            when (it) {
                                "0" -> "0.0.0.0"
                                "1" -> {
                                    if (question.type == Record.TYPE.AAAA) "::1"
                                    else "127.0.0.1"
                                }
                                else -> it
                            }
                        }
                if (resolveResult != null) {
                    resolveResults[question] = resolveResult
                    true
                } else if(wildcardCount != 0){
                    val wildcardResolveResults = dao.findPossibleWildcardRuleTarget(
                        uniformQuestion,
                        question.type,
                        useUserRules,
                        false,
                        true
                    ).filter {
                        DnsRuleDialog.databaseHostToMatcher(it.host)
                            .reset(uniformQuestion).matches()
                    }
                    if(wildcardResolveResults.isNotEmpty()) {
                        resolveResults[question] = wildcardResolveResults.first().let {
                            if (question.type == Record.TYPE.AAAA) it.ipv6Target
                                ?: it.target
                            else it.target
                        }.let {
                            when (it) {
                                "0" -> "0.0.0.0"
                                "1" -> {
                                    if (question.type == Record.TYPE.AAAA) "::1"
                                    else "127.0.0.1"
                                }
                                else -> it
                            }
                        }
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
        }
    }

    override suspend fun resolve(question: Question): List<Record<*>> {
        val result = resolveResults.remove(question)
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

    override fun cleanup() {}

}