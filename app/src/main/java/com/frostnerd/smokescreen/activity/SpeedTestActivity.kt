package com.frostnerd.smokescreen.activity

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.frostnerd.cacheadapter.AdapterBuilder
import com.frostnerd.dnstunnelproxy.DnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.AbstractHttpsDNSHandle
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.tls.AbstractTLSDnsHandle
import com.frostnerd.lifecyclemanagement.BaseActivity
import com.frostnerd.lifecyclemanagement.BaseViewHolder
import com.frostnerd.lifecyclemanagement.launchWithLifecylce
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.showInfoTextDialog
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
                val dotCount = testResults!!.count { it.server !is HttpsDnsServerInformation }
                val dotReachable = testResults!!.count { it.server !is HttpsDnsServerInformation && it.latency != null}
                val dotNotReachable = dotCount - dotReachable

                val dohCount = testResults!!.size - dotCount
                val dohReachable = testResults!!.count { it.server is HttpsDnsServerInformation && it.latency != null}
                val dohNotReachable = dohCount - dohReachable

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
                        dohNotReachable,
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
        prepareListJob = launchWithLifecylce(true) {
            val dnsServers = AbstractTLSDnsHandle.KNOWN_DNS_SERVERS.values +
                    AbstractHttpsDNSHandle.KNOWN_DNS_SERVERS.values +
                    getPreferences().userServers.map {
                        it.serverInformation
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
        testJob = launchWithLifecylce(false) {
            prepareListJob?.join()
            testRunning = true
            wasStartedBefore = true
            val testsLeft = testResults!!.shuffled()
            var cnt = 0
            runOnUiThread {
                startTest.text = "0/${testsLeft.size}"
            }
            testsLeft.forEach {
                if(testJob?.isCancelled == false) {
                    it.started = true
                    val res = DnsSpeedTest(it.server, 500, 750).runTest(3)

                    if (res != null) it.latency = res
                    else it.error = true

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
            serverType.text = if(speedTest.server is HttpsDnsServerInformation) getString(R.string.tasker_mode_doh)
            else getString(R.string.tasker_mode_dot)
            if (speedTest.latency == null) {
                when {
                    speedTest.error -> {
                        latency.text = "- ms"
                        latency.setTextColor(Color.RED)
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