package com.frostnerd.smokescreen.util.proxy

import com.frostnerd.dnstunnelproxy.LocalResolver
import org.minidns.dnsmessage.DnsMessage
import org.minidns.dnsmessage.Question
import org.minidns.record.*

// Resolver which does not use user rules.
class StaticDnsRuleResolver : LocalResolver(false) {
    companion object {
        val staticRules = mapOf<String, List<Record<*>>>(
            "use-application-dns.net" to emptyList()
        )
    }

    override fun canResolve(question: Question): Boolean {
        return staticRules.containsKey(question.name?.toString()?.lowercase())
    }

    override fun resolve(question: Question): List<Record<*>> {
        return staticRules[question.name?.toString()?.lowercase()] ?: emptyList()
    }

    override fun cleanup() {

    }

    override fun mapResponse(message: DnsMessage): DnsMessage {
        return message
    }
}