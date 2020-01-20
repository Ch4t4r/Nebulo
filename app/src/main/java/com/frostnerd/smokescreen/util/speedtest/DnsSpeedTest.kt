package com.frostnerd.smokescreen.util.speedtest

import androidx.annotation.IntRange
import cn.danielw.fop.ObjectFactory
import cn.danielw.fop.ObjectPool
import cn.danielw.fop.PoolConfig
import cn.danielw.fop.Poolable
import com.frostnerd.dnstunnelproxy.DnsServerInformation
import com.frostnerd.dnstunnelproxy.UpstreamAddress
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.ServerConfiguration
import com.frostnerd.encrypteddnstunnelproxy.tls.TLSUpstreamAddress
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.internal.closeQuietly
import org.minidns.dnsmessage.DnsMessage
import org.minidns.dnsmessage.Question
import org.minidns.record.Record
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocketFactory
import kotlin.random.Random

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

class DnsSpeedTest(val server: DnsServerInformation<*>,
                   private val connectTimeout: Int = 2500,
                   private val readTimeout:Int = 1500,
                   val log:(line:String) -> Unit) {
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .dns(httpsDnsClient)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(readTimeout.toLong(), TimeUnit.MILLISECONDS)
            .build()
    }
    private val httpsDnsClient by lazy {
        PinnedDns((server as HttpsDnsServerInformation).serverConfigurations.values.map {
            it.urlCreator.address
        })
    }
    private val connectionPool = ConcurrentHashMap<TLSUpstreamAddress, ObjectPool<Socket>>()
    private lateinit var poolConfig:PoolConfig
    private val poolFactory = object: ObjectFactory<Socket> {
        private val sslSocketFactory = SSLSocketFactory.getDefault()

        override fun validate(t: Socket): Boolean {
            return !t.isConnected
        }

        override fun destroy(t: Socket) {
            t.closeQuietly()
        }

        override fun create(): Socket {
            return sslSocketFactory.createSocket()
        }
    }

    companion object {
        val testDomains = listOf("google.com", "frostnerd.com", "amazon.com", "youtube.com", "github.com",
            "stackoverflow.com", "stackexchange.com", "spotify.com", "material.io", "reddit.com", "android.com")
    }

    /**
     * @param passes The amount of requests to make
     * @return The average response time (in ms)
     */
    fun runTest(@IntRange(from = 1) passes: Int): Int? {
        var ttl = 0
        poolConfig = PoolConfig().apply {
            this.maxSize = 2
            this.minSize = 1
            this.partitionSize = 1
            this.maxIdleMilliseconds = 60*1000*5
        }

        for (i in 0 until passes) {
            if (server is HttpsDnsServerInformation) {
                server.serverConfigurations.values.forEach {
                    ttl += testHttps(it) ?: 0
                }
            } else {
                (server as DnsServerInformation<TLSUpstreamAddress>).servers.forEach {
                    ttl += testTls(it.address) ?: 0
                }
            }
        }
        return (ttl / passes).let {
            if (it <= 0) null else it
        }
    }

    private fun testHttps(config: ServerConfiguration): Int? {
        val msg = createTestDnsPacket()
        val url: URL = config.urlCreator.createUrl(msg, config.urlCreator.address)
        log("Using URL: $url")
        val requestBuilder = Request.Builder().url(url)
        if (config.requestHasBody) {
            val body = config.bodyCreator!!.createBody(msg, config.urlCreator.address)
            if (body != null) {
                requestBuilder.header("Content-Type", config.contentType)
                requestBuilder.post(body.rawBody.toRequestBody(body.mediaType, 0, body.rawBody.size))
            } else {
                log("DoH test failed once for ${server.name}: BodyCreator didn't create a body")
                return null
            }
        }
        var response:Response? = null
        try {
            val start = System.currentTimeMillis()
            response = httpClient.newCall(requestBuilder.build()).execute()
            if(!response.isSuccessful) {
                log("DoH test failed once for ${server.name}: Request not successful (${response.code})")
                return null
            }
            val body = response.body ?: run{
                log("DoH test failed once for ${server.name}: No response body")
                return null
            }
            val bytes = body.bytes()
            val time = (System.currentTimeMillis() - start).toInt()

            if (bytes.size < 17) {
                log("DoH test failed once for ${server.name}: Returned less than 17 bytes")
                return null
            } else if(!testResponse(DnsMessage(bytes))) {
                log("DoH test failed once for ${server.name}: Testing the response for valid dns message failed")
                return null
            }
            return time
        } catch (ex: Exception) {
            log("DoH test failed with exception once for ${server.name}: ${ex.message}")
            return null
        } finally {
            if(response?.body != null) response.close()
        }
    }

    private fun obtainTlsSocket(address: TLSUpstreamAddress): Poolable<Socket>? {
        return try {
            connectionPool.getOrPut(address) {
                ObjectPool(poolConfig, poolFactory)
            }.borrowObject()
        } catch (e: RuntimeException) {
            null
        }
    }

    private fun testTls(address: TLSUpstreamAddress): Int? {
        val addr =
            address.addressCreator.resolveOrGetResultOrNull(retryIfError = true, runResolveNow = true) ?: run {
                log("DoT test failed once for ${server.name}: Address failed to resolve ($address)")
                return null
            }
        var socketPooled: Poolable<Socket>? = null
        var socket:Socket? = null
        try {
            socketPooled = obtainTlsSocket(address)
            socket = socketPooled?.`object` ?: SSLSocketFactory.getDefault().createSocket()
            val msg = createTestDnsPacket()
            val start = System.currentTimeMillis()
            socket!!.connect(InetSocketAddress(addr[0], address.port), connectTimeout)
            socket.soTimeout = readTimeout
            val data: ByteArray = msg.toArray()
            val outputStream = DataOutputStream(socket.getOutputStream())
            val size = data.size
            val arr: ByteArray = byteArrayOf(((size shr 8) and 0xFF).toByte(), (size and 0xFF).toByte())
            outputStream.write(arr)
            outputStream.write(data)
            outputStream.flush()

            val inStream = DataInputStream(socket.getInputStream())
            val readData = ByteArray(inStream.readUnsignedShort())
            inStream.read(readData)
            val time = (System.currentTimeMillis() - start).toInt()

            socket = null
            if(!testResponse(DnsMessage(readData))) {
                log("DoT test failed once for ${server.name}: Testing the response for valid dns message failed")
                return null
            }
            return time
        } catch (ex: Exception) {
            log("DoT test failed with exception once for ${server.name}: $ex")
            return null
        } finally {
            socket?.close()
            socketPooled?.returnObject()
        }
    }

    private fun createTestDnsPacket(): DnsMessage {
        val msg = DnsMessage.builder().setQrFlag(false)
            .addQuestion(Question(testDomains.random(), Record.TYPE.A, Record.CLASS.IN))
            .setId(Random.nextInt(1, 999999))
            .setRecursionDesired(true)
            .setAuthenticData(true)
            .setRecursionAvailable(true)
        return msg.build()
    }

    private fun testResponse(message:DnsMessage):Boolean {
        return message.answerSection.size > 0
    }

    private inner class PinnedDns(private val upstreamServers: List<UpstreamAddress>) : Dns {

        override fun lookup(hostname: String): MutableList<InetAddress> {
            val res = mutableListOf<InetAddress>()
            for (server in upstreamServers) {
                if (server.host.equals(hostname, true)) {
                    res.addAll(server.addressCreator.resolveOrGetResultOrNull(true) ?: emptyArray())
                    break
                }
            }
            if (res.isEmpty()) {
                res.addAll(Dns.SYSTEM.lookup(hostname))
            }
            if (res.isEmpty()) {
                throw UnknownHostException("Could not resolve $hostname")
            }
            return res
        }
    }
}