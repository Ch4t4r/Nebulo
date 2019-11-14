package com.frostnerd.smokescreen.util.proxy

import android.content.Context
import com.frostnerd.dnstunnelproxy.CacheControl
import com.frostnerd.smokescreen.getPreferences
import org.minidns.dnsmessage.DnsMessage
import org.minidns.dnsmessage.Question
import org.minidns.dnsname.DnsName
import org.minidns.record.Record

class NxDomainCacheControl(context: Context) :CacheControl {
    private val cacheTime = context.getPreferences().customDnsCacheTime.toLong()
    private val nxDomainCacheTime = context.getPreferences().nxDomainCacheTime.toLong()

    override suspend fun getTtl(
        answerMessage: DnsMessage,
        dnsName: DnsName,
        type: Record.TYPE,
        record: Record<*>
    ): Long {
        return if (answerMessage.responseCode == DnsMessage.RESPONSE_CODE.NX_DOMAIN) nxDomainCacheTime else cacheTime
    }

    override suspend fun getTtl(question: Question, record: Record<*>): Long =
        cacheTime


    override fun shouldCache(question: Question): Boolean = true
}