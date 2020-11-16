package com.frostnerd.smokescreen.activity

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.frostnerd.dnstunnelproxy.*
import com.frostnerd.encrypteddnstunnelproxy.AbstractHttpsDNSHandle
import com.frostnerd.encrypteddnstunnelproxy.quic.AbstractQuicDnsHandle
import com.frostnerd.encrypteddnstunnelproxy.tls.AbstractTLSDnsHandle
import com.frostnerd.smokescreen.BuildConfig
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.createQuicCronetEngineIfInstalled
import com.frostnerd.smokescreen.fragment.AppIntroServerChooseFragment
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.util.speedtest.DnsSpeedTest
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment
import com.github.appintro.AppIntroPageTransformerType
import kotlinx.coroutines.*
import kotlin.math.ceil

/*
 * Copyright (C) 2020 Daniel Wolf (Ch4t4r)
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

class NebuloAppIntro:AppIntro() {
    private var isChoosingServer = false
    private var serverChoseFragment:AppIntroServerChooseFragment? = null
    companion object {
        private val speedTestResults:MutableMap<DnsServerInformation<*>, Int?> = (AbstractTLSDnsHandle.waitUntilKnownServersArePopulated(10) {
            it.values.map {
                it to null
            }.toMap()
        } + AbstractHttpsDNSHandle.waitUntilKnownServersArePopulated(10) {
            it.values.map {
                it to null
            }.toMap()
        } + AbstractQuicDnsHandle.waitUntilKnownServersArePopulated(10) {
            it.values.map {
                it to null
            }.toMap()
        }).filter { BuildConfig.SHOW_ALL_SERVERS || !it.key.hasCapability(DEFAULT_DNSERVER_CAPABILITIES.BLOCK_ADS) }.toMutableMap()
        private val jobSupervisor = SupervisorJob()
    }

    private fun beginSpeedtest() {
        val parts = 3
        val context = newFixedThreadPoolContext(parts, "speedtest-pool") + jobSupervisor
        val scope = CoroutineScope(context)
        val chunks = speedTestResults.keys.chunked(ceil(speedTestResults.size.toDouble()/parts).toInt())
        for(i in 0 until parts) {
            scope.launch {
                for(server in chunks[i]) {
                    if(!isActive) break
                    val testResult = DnsSpeedTest(this@NebuloAppIntro, server, log = {}, cronetEngine = createQuicCronetEngineIfInstalled(this@NebuloAppIntro)).runTest(3)
                    synchronized(speedTestResults) {
                        speedTestResults[server] = testResult
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getPreferences().theme.id)
        beginSpeedtest()
        super.onCreate(savedInstanceState)
        setTransformer(AppIntroPageTransformerType.Zoom)
        isImmersive = false
        showStatusBar(true)


        val textColor = getPreferences().theme.getTextColor(this)
        val backgroundColor = getPreferences().theme.resolveAttribute(theme, android.R.attr.colorBackground)

        if(intent?.hasExtra("chooseServer") == true) {
            isChoosingServer = true
            serverChoseFragment = AppIntroServerChooseFragment().also {
                addSlide(it)
            }
        } else {
            addSlide(
                AppIntroFragment.newInstance(
                    title = getString(R.string.appintro_first_title),
                    description = getString(R.string.appintro_first_description),
                    titleColor = textColor,
                    descriptionColor = textColor, backgroundColor = backgroundColor,
                    imageDrawable = R.drawable.intro_lock
                ))

            addSlide(
                AppIntroFragment.newInstance(
                    title = getString(R.string.appintro_second_title),
                    description = getString(R.string.appintro_second_description),
                    titleColor = textColor,
                    descriptionColor = textColor, backgroundColor = backgroundColor
                ))
            addSlide(
                AppIntroFragment.newInstance(
                    title = getString(R.string.appintro_third_title),
                    description = getString(R.string.appintro_third_description),
                    titleColor = textColor,
                    descriptionColor = textColor, backgroundColor = backgroundColor
                ))
            if(BuildConfig.SHOW_ALL_SERVERS) addSlide(
                AppIntroFragment.newInstance(
                    title = getString(R.string.appintro_forth_title),
                    description = getString(R.string.appintro_forth_description),
                    titleColor = textColor,
                    descriptionColor = textColor, backgroundColor = backgroundColor
                ))
        }
    }

    override fun onSkipPressed(currentFragment: Fragment?) {
        if(isChoosingServer) {
            jobSupervisor.cancel()
            serverChoseFragment?.serverType?.apply {
                setMatchingServer(this)
            }
            serverChoseFragment = null
            val startIntent = Intent(this, MainActivity::class.java)
            startIntent.putExtras(intent?.extras?.getBundle("extras") ?: Bundle())
            startActivity(PinActivity.passPin(startIntent))
            finish()
        } else {
            val startIntent = Intent(this, NebuloAppIntro::class.java)
            startIntent.putExtra("extras", intent?.extras?.getBundle("extras") ?: Bundle())
            startIntent.putExtra("chooseServer", true)
            startActivity(PinActivity.passPin(startIntent))
            finish()
        }
    }

    private fun setMatchingServer(type:AppIntroServerChooseFragment.ServerType) {
        val capabilitiesNeeded:Set<DnsServerCapability> = when(type) {
            AppIntroServerChooseFragment.ServerType.ADS -> setOf(DEFAULT_DNSERVER_CAPABILITIES.BLOCK_ADS)
            AppIntroServerChooseFragment.ServerType.ANTI_MALWARE -> setOf(DEFAULT_DNSERVER_CAPABILITIES.BLOCK_MALICIOUS)
            AppIntroServerChooseFragment.ServerType.PRIVACY, AppIntroServerChooseFragment.ServerType.FASTEST_PRIVACY -> setOf(DEFAULT_DNSERVER_CAPABILITIES.NO_CENSORSHIP)
            else -> setOf()
        }
        val capabilitiesDesired:Set<DnsServerCapability> = when(type) {
            AppIntroServerChooseFragment.ServerType.PRIVACY -> setOf(DEFAULT_DNSERVER_CAPABILITIES.PRIVACY_FOCUSED)
            else -> setOf()
        }
        val capabilitiesExcluded = setOf(DEFAULT_DNSERVER_CAPABILITIES.BLOCK_PORN)
        val isPrivacyAware = type == AppIntroServerChooseFragment.ServerType.FASTEST_PRIVACY || type == AppIntroServerChooseFragment.ServerType.PRIVACY

        val filteredMaybePendingResult = speedTestResults.asSequence().filter {
            !it.key.capabilities.any { capability -> capabilitiesExcluded.contains(capability) }
        }.filter {
            it.key.capabilities.containsAll(capabilitiesNeeded) && (!isPrivacyAware || it.key.specification.tracksUsers == Decision.NO || it.key.specification.tracksUsers == Decision.LIKELY_NO)
        }

        val filteredServer = filteredMaybePendingResult.filter {
            it.value != null
        }.sortedWith(compareBy {
            it.value
        }).map {
            it.key
        }.firstOrNull() ?: filteredMaybePendingResult.toList().firstOrNull()?.key
        ?: AbstractHttpsDNSHandle.KNOWN_DNS_SERVERS[3] // Index 3 = Quad9

        if (filteredServer != null) {
            getPreferences().dnsServerConfig = filteredServer
        }
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        onSkipPressed(currentFragment)
    }
}