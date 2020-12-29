package com.frostnerd.smokescreen.util.speedtest

import android.content.Context
import androidx.annotation.IntRange
import com.frostnerd.dnstunnelproxy.DnsServerInformation
import com.frostnerd.dnstunnelproxy.UpstreamAddress
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.ServerConfiguration
import com.frostnerd.encrypteddnstunnelproxy.closeSilently
import com.frostnerd.encrypteddnstunnelproxy.quic.QuicUpstreamAddress
import com.frostnerd.encrypteddnstunnelproxy.tls.TLSUpstreamAddress
import com.frostnerd.smokescreen.createHttpCronetEngineIfInstalled
import com.frostnerd.smokescreen.type
import com.frostnerd.smokescreen.util.ServerType
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.chromium.net.CronetEngine
import org.minidns.dnsmessage.DnsMessage
import org.minidns.dnsmessage.Question
import org.minidns.record.Record
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.*
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

class DnsSpeedTest(context:Context,
                   val server: DnsServerInformation<*>,
                   private val connectTimeout: Int = 2500,
                   private val readTimeout:Int = 1500,
                   private val cronetEngine: CronetEngine?,
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
    private val httpsCronetEngine = createHttpCronetEngineIfInstalled(context)

    companion object {
        val testDomains = listOf("google.com", "frostnerd.com", "amazon.com", "youtube.com", "github.com",
            "stackoverflow.com", "stackexchange.com", "spotify.com", "material.io", "reddit.com", "android.com")
    }

    /**
     * @param passes The amount of requests to make
     * @return The average response time (in ms)
     */
    fun runTest(@IntRange(from = 1) passes: Int, strategy: Strategy = Strategy.AVERAGE): Int? {
        log("Running with cronet: ${httpsCronetEngine != null}. Testing DoQ: ${cronetEngine != null}")
        val latencies = mutableListOf<Int>()

        var firstPass = true
        for (i in 0 until passes) {
            when(server.type) {
                ServerType.DOT -> {
                    (server as DnsServerInformation<TLSUpstreamAddress>).servers.forEach {
                        if(firstPass) testTls(it.address)
                        latencies += testTls(it.address) ?: 0
                    }
                }
                ServerType.DOH -> {
                    server as HttpsDnsServerInformation
                    server.serverConfigurations.values.forEach {
                        latencies += if(httpsCronetEngine == null) {
                            if(firstPass) testHttps(it)
                            testHttps(it) ?: 0
                        } else {
                            if(firstPass) testHttpsCronet(it)
                            testHttpsCronet(it) ?: 0
                        }

                    }
                }
                ServerType.DOQ -> {
                    (server as DnsServerInformation<QuicUpstreamAddress>).servers.forEach {
                        if(cronetEngine != null) {
                            if(firstPass) testQuic(it.address)
                            latencies += testQuic(it.address) ?: 0
                        }
                    }
                }
            }
            firstPass = false
        }
        return when (strategy) {
            Strategy.BEST_CASE -> {
                latencies.minByOrNull {
                    it
                }
            }
            Strategy.AVERAGE -> {
                latencies.sum().let {
                    if(it <= 0) null else it
                }?.div(passes)
            }
            else -> {
                var pos = 0
                latencies.sumBy {
                    // Weight first responses less (min 80%)
                    val minWeight = 90
                    val step = minOf(2, (100-minWeight)/passes)
                    val weight = maxOf(100, minOf(minWeight, 100-(passes - pos++)*step))
                    (it*weight)/100
                }.let {
                    if(it <= 0) null else it
                }
            }
        }
    }

    private fun testHttps(config: ServerConfiguration): Int? {
        val msg = createTestDnsPacket()
        val start = System.currentTimeMillis()
        val url: URL = config.urlCreator.createUrl(msg, config.urlCreator.address)
        try {
            url.toString().toHttpUrl()
            log("Using URL: $url")
        } catch (ignored:IllegalArgumentException) {
            log("Invalid URL: $url")
            return null
        }

        val requestBuilder = Request.Builder().url(url)
        if (config.requestHasBody) {
            val body = config.bodyCreator!!.createBody(msg, config.urlCreator.address)
            if (body != null) {
                requestBuilder.header("Content-Type", config.contentType)
                requestBuilder.post(body.rawBody.toRequestBody(body.mediaType?.toMediaTypeOrNull(), 0, body.rawBody.size))
            } else {
                log("DoH test failed once for ${server.name}: BodyCreator didn't create a body")
                return null
            }
        }
        var response:Response? = null
        try {
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

    private fun testHttpsCronet(config: ServerConfiguration): Int? {
        val msg = createTestDnsPacket()
        val start = System.currentTimeMillis()
        val url: URL = config.urlCreator.createUrl(msg, config.urlCreator.address)
        try {
            url.toString().toHttpUrl()
            log("Using URL: $url")
        } catch (ignored:IllegalArgumentException) {
            log("Invalid URL: $url")
            return null
        }

        try {
            val connection = httpsCronetEngine!!.openConnection(url) as HttpURLConnection
            connection.connectTimeout = connectTimeout
            if(config.requestHasBody) {
                val body = config.bodyCreator?.createBody(msg, config.urlCreator.address) ?: return null
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", config.contentType)
                connection.doOutput = true
                body.mediaType?.also {connection.setRequestProperty("Accept", it) }
                connection.readTimeout = (connectTimeout*1.5).toInt()
                val outputStream = connection.outputStream
                outputStream.write(body.rawBody)
                outputStream.flush()
            } else {
                connection.requestMethod = "GET"
                connection.connect()
            }
            val status = connection.responseCode
            if(status == 200) {
                val data = connection.inputStream.readBytes().takeIf {
                    it.isNotEmpty()
                } ?: return null
                val time = System.currentTimeMillis() - start
                return if(testResponse(DnsMessage(data))) {
                    time.toInt()
                } else null
            } else {
                connection.inputStream.readBytes()
                return null
            }
        } catch (ex:Throwable) {
            return null
        }
    }

    private fun testTls(address: TLSUpstreamAddress): Int? {
        val addr =
            address.addressCreator.resolveOrGetResultOrNull(retryIfError = true, runResolveNow = true) ?: run {
                log("DoT test failed once for ${server.name}: Address failed to resolve ($address)")
                return null
            }
        var socket:Socket? = null
        try {
            socket = SSLSocketFactory.getDefault().createSocket()
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
        }
    }

    private fun testQuic(address: QuicUpstreamAddress):Int? {
        val url = URL(address.getUrl(false))
        var connection: HttpURLConnection? = null
        var wasEstablished = false
        val msg = createTestDnsPacket()
        try {
            val start = System.currentTimeMillis()
            connection = cronetEngine!!.openConnection(url) as HttpURLConnection
            connection.connectTimeout = connectTimeout
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/dns-message")
            connection.setRequestProperty("Accept", "application/dns-message")
            connection.doOutput = true
            connection.readTimeout = readTimeout
            wasEstablished = true
            val outputStream = connection.outputStream
            outputStream.write(msg.toArray())
            outputStream.flush()
            val status = connection.responseCode
            if(status == 200) {
                val data = connection.inputStream.readBytes().takeIf {
                    it.isNotEmpty()
                } ?: return null
                val time = (System.currentTimeMillis() - start).toInt()
                if(!testResponse(DnsMessage(data))) {
                    log("DoT test failed once for ${server.name}: Testing the response for valid dns message failed")
                    return null
                }
                return time
            } else {
               return null
            }
        } catch (ex: java.lang.Exception) {
            return null
        } finally {
            if(wasEstablished) try {
                connection?.inputStream?.closeSilently()
            } catch (ignored:Throwable) {}
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

    enum class Strategy {
        AVERAGE, BEST_CASE,
        @Suppress("unused")
        WEIGHTED_AVERAGE
    }
}