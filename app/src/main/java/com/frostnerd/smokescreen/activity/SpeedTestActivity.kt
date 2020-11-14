package com.frostnerd.smokescreen.activity

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.frostnerd.cacheadapter.AdapterBuilder
import com.frostnerd.dnstunnelproxy.DEFAULT_DNSERVER_CAPABILITIES
import com.frostnerd.dnstunnelproxy.DnsServerInformation
import com.frostnerd.dnstunnelproxy.TransportProtocol
import com.frostnerd.encrypteddnstunnelproxy.AbstractHttpsDNSHandle
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.quic.AbstractQuicDnsHandle
import com.frostnerd.encrypteddnstunnelproxy.tls.AbstractTLSDnsHandle
import com.frostnerd.lifecyclemanagement.BaseActivity
import com.frostnerd.lifecyclemanagement.BaseViewHolder
import com.frostnerd.lifecyclemanagement.launchWithLifecycle
import com.frostnerd.lifecyclemanagement.launchWithLifecycleUi
import com.frostnerd.smokescreen.*
import com.frostnerd.smokescreen.util.LanguageContextWrapper
import com.frostnerd.smokescreen.util.ServerType
import com.frostnerd.smokescreen.util.SpaceItemDecorator
import com.frostnerd.smokescreen.util.speedtest.DnsSpeedTest
import kotlinx.android.synthetic.main.activity_speedtest.*
import kotlinx.android.synthetic.main.item_dns_speed.view.*
import kotlinx.coroutines.Job


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

class SpeedTestActivity : BaseActivity() {
    private var testRunning = false
    private var wasStartedBefore = false
    private var testJob: Job? = null
    private var testResults:MutableList<SpeedTest>? = null
    private var listAdapter:RecyclerView.Adapter<*>? = null
    private var prepareListJob:Job? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageContextWrapper.attachFromSettings(this, newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_speedtest)
        setSupportActionBar(toolBar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        startTest.setOnClickListener {
            startTest()
            startTest.isEnabled = false
            abort.visibility = View.VISIBLE
            info.visibility = View.GONE
        }
        abort.setOnClickListener {
            abort.visibility = View.GONE
            testJob?.cancel()
            testJob = null
            startTest.isEnabled = true
            testRunning = false
            info.visibility = View.VISIBLE
            startTest.text = getString(R.string.window_speedtest_runtest)
        }
        info.setOnClickListener {
            if(testResults != null) {
                val dotCount = testResults!!.count { it.server.type == ServerType.DOT }
                val dotReachable = testResults!!.count { it.server.type == ServerType.DOT && it.latency != null}
                val dotNotReachable = dotCount - dotReachable

                val dohCount = testResults!!.count { it.server.type == ServerType.DOH }
                val dohReachable = testResults!!.count {it.server.type == ServerType.DOH && it.latency != null}
                val dohNotReachable = dohCount - dohReachable

                val doqCount = testResults!!.count { it.server.type == ServerType.DOQ }
                val doqReachable = testResults!!.count {it.server.type == ServerType.DOQ && it.latency != null}
                val doqNotReachable = dohCount - dohReachable

                val avgLatency = testResults!!.sumBy { it.latency ?: 0 }/testResults!!.size
                val fastestServer = testResults!!.minBy { it.latency ?: Integer.MAX_VALUE}
                val slowestServer = testResults!!.maxBy { it.latency ?: 0}

                showInfoTextDialog(this,
                    getString(R.string.dialog_speedresult_title),
                    getString(R.string.dialog_speedresult_message,
                        testResults!!.size,
                        dotReachable,
                        dotNotReachable,
                        dohReachable,
                        dohNotReachable, // TODO show DoQ results
                        avgLatency,
                        fastestServer?.server?.name ?: "-",
                        slowestServer?.server?.name ?: "-"
                        ))
            }
        }
        serverList.layoutManager = LinearLayoutManager(this)
        serverList.addItemDecoration(SpaceItemDecorator(this))
        prepareList()
    }

    private fun prepareList() {
        prepareListJob = launchWithLifecycleUi {
            val hiddenDotServers = getPreferences().removedDefaultDoTServers
            val hiddenDohServers = getPreferences().removedDefaultDoHServers
            val hiddenDoQServers = getPreferences().removedDefaultDoQServers
            val hasIpv4 = hasDeviceIpv4Address()
            val hasIpv6 = hasDeviceIpv6Address()
            val unfilteredServers = AbstractTLSDnsHandle.KNOWN_DNS_SERVERS.filter { it.key !in hiddenDotServers }.values +
                    AbstractHttpsDNSHandle.KNOWN_DNS_SERVERS.filter { it.key !in hiddenDohServers }.values +
                    getPreferences().userServers.map {
                        it.serverInformation
                    } +
                    AbstractQuicDnsHandle.KNOWN_DNS_SERVERS.filter { it.key !in hiddenDoQServers }.values
            val dnsServers = unfilteredServers.filter {
                it.servers.all { server ->
                    hasIpv4 == (TransportProtocol.IPV4 in server.supportedTransportProtocols)
                            || hasIpv6 == (TransportProtocol.IPV6 in server.supportedTransportProtocols)
                }
            }.filter {
                !it.hasCapability(DEFAULT_DNSERVER_CAPABILITIES.BLOCK_ADS) || BuildConfig.SHOW_ALL_SERVERS
            }
            val testResults = dnsServers.map {
                SpeedTest(it, null)
            }.toMutableList()
            this@SpeedTestActivity.testResults = testResults
            val showUseServerDialog = { test:SpeedTest ->
                showInfoTextDialog(this@SpeedTestActivity,
                    getString(R.string.dialog_speedtest_useserver_title),
                    getString(R.string.dialog_speedtest_useserver_message,
                        test.server.name,
                        testResults.indexOf(test) + 1,
                        testResults.size,
                        test.latency!!
                    ),
                    getString(R.string.all_yes) to { dialog, _ ->
                        getPreferences().dnsServerConfig = test.server
                        dialog.dismiss()
                    }, getString(R.string.all_no) to { dialog, _ ->
                        dialog.dismiss()
                    }, null)
            }
            listAdapter = AdapterBuilder.withViewHolder({ view, _ -> SpeedViewHolder(view, showUseServerDialog) }) {
                viewBuilder = { parent, _ ->
                    layoutInflater.inflate(R.layout.item_dns_speed, parent, false)
                }
                getItemCount = {
                    testResults.size
                }
                bindView = { viewHolder, position ->
                    viewHolder.display(testResults[position])
                }
            }.build()
            runOnUiThread {
                serverList.adapter = listAdapter
            }
            prepareListJob = null
        }
    }

    private fun startTest() {
        if(wasStartedBefore) prepareList()
        testJob = launchWithLifecycle {
            prepareListJob?.join()
            val engine = createCronetEngineIfInstalled(this@SpeedTestActivity)
            testRunning = true
            wasStartedBefore = true
            val testsLeft = testResults!!.shuffled()
            var cnt = 0
            runOnUiThread {
                startTest.text = "0/${testsLeft.size}"
            }
            var highestLatency = 500
            testsLeft.forEach { pendingTest ->
                if(testJob?.isCancelled == false) {
                    pendingTest.started = true
                    log("Running SpeedTest for ${pendingTest.server.name}")
                    val res = DnsSpeedTest(pendingTest.server, highestLatency, highestLatency+250, engine) { line ->
                        log(line)
                    }.runTest(3)

                    if (res != null) pendingTest.latency = res
                    else pendingTest.error = true

                    highestLatency = kotlin.math.max(highestLatency, ((pendingTest.latency ?: 0)*1.25).toInt())
                    testResults!!.sortBy {
                        it.latency ?: Integer.MAX_VALUE
                    }
                    runOnUiThread {
                        cnt++
                        listAdapter!!.notifyDataSetChanged()
                        startTest.text = "$cnt/${testResults!!.size}"
                    }
                }
            }

            if(testJob?.isCancelled == false)runOnUiThread {
                startTest.isEnabled = true
                abort.visibility = View.GONE
                startTest.text = getString(R.string.window_speedtest_runtest)
                testRunning = false
                testJob = null
                info.visibility = View.VISIBLE
            }
        }
    }

    override fun getConfiguration(): Configuration {
        return Configuration.withDefaults()
    }

    private inner class SpeedViewHolder(view: View, private val showUseServerDialog:(SpeedTest) -> Any) : BaseViewHolder(view) {
        val name = view.name
        val servers = view.servers
        val progress = view.progress
        val latency = view.latency
        val serverType = view.serverType
        val nameWrap = view.nameWrap
        private var defaultTextColor = latency.currentTextColor

        fun display(speedTest: SpeedTest) {
            if(speedTest.latency != null) {
                val listener:(View) ->Unit = { showUseServerDialog(speedTest) }
                itemView.setOnClickListener(listener)
                nameWrap.setOnClickListener(listener)
            } else {
                itemView.setOnClickListener(null)
                nameWrap.setOnClickListener(null)
            }
            name.text = speedTest.server.name
            servers.text = buildString {
                speedTest.server.servers.forEach {
                    append(it.address.formatToString())
                    append("\n")
                }
            }
            serverType.text = when(speedTest.server.type) {
                ServerType.DOT -> getString(R.string.tasker_mode_dot)
                ServerType.DOH -> getString(R.string.tasker_mode_doh)
                ServerType.DOQ -> getString(R.string.tasker_mode_doq)
            }
            if (speedTest.latency == null) {
                when {
                    speedTest.error -> {
                        latency.text = "- ms"
                        latency.setTextColor(Color.parseColor("#80CBC4"))
                        progress.visibility = View.INVISIBLE
                        latency.visibility = View.VISIBLE
                    }
                    speedTest.started -> {
                        progress.visibility = View.VISIBLE
                        latency.visibility = View.INVISIBLE
                    }
                    else -> {
                        latency.text = "? ms"
                        latency.visibility = View.VISIBLE
                        progress.visibility = View.INVISIBLE
                        latency.setTextColor(defaultTextColor)
                    }
                }
            } else {
                latency.text = "${speedTest.latency} ms"
                latency.setTextColor(defaultTextColor)
                progress.visibility = View.INVISIBLE
                latency.visibility = View.VISIBLE
            }
        }

        override fun destroy() {}
    }

    private class SpeedTest(val server: DnsServerInformation<*>, var latency: Int?) {
        var error: Boolean = false
        var started:Boolean = false
    }



}