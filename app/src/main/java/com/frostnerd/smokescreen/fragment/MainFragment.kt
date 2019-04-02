package com.frostnerd.smokescreen.fragment

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.frostnerd.dnstunnelproxy.DnsServerInformation
import com.frostnerd.general.service.isServiceRunning
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.dialog.ServerChoosalDialog
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.registerLocalReceiver
import com.frostnerd.smokescreen.service.Command
import com.frostnerd.smokescreen.service.DnsVpnService
import com.frostnerd.smokescreen.unregisterLocalReceiver
import kotlinx.android.synthetic.main.fragment_main.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.URL


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
class MainFragment : Fragment() {
    private val vpnRequestCode: Int = 1
    private var loadingAnimation: RotateAnimation? = null
    private var proxyRunning: Boolean = false
    private var proxyStarting = false
    private var vpnStateReceiver: BroadcastReceiver? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onResume() {
        super.onResume()
        proxyRunning = requireContext().isServiceRunning(DnsVpnService::class.java)
        updateVpnIndicators()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        startButton.setOnClickListener {
            if (proxyRunning) {
                DnsVpnService.sendCommand(requireContext(), Command.STOP)
                proxyStarting = true
                proxyRunning = false
            } else {
                startVpn()
                proxyStarting = true
            }
            updateVpnIndicators()
        }
        vpnStateReceiver = requireContext().registerLocalReceiver(
            listOf(
                DnsVpnService.BROADCAST_VPN_ACTIVE,
                DnsVpnService.BROADCAST_VPN_INACTIVE
            )
        ) {
            if (it != null && it.action != null) {
                when (it.action) {
                    DnsVpnService.BROADCAST_VPN_ACTIVE -> {
                        proxyStarting = false
                        proxyRunning = true
                    }
                    DnsVpnService.BROADCAST_VPN_INACTIVE -> {
                        proxyRunning = false
                        proxyStarting = false
                    }
                }
                updateVpnIndicators()
            }
        }
        serverButton.setOnClickListener {
            ServerChoosalDialog(requireActivity() as AppCompatActivity) { config ->
                updatePrivacyPolicyLink(config)
                val prefs = requireContext().getPreferences()
                prefs.edit {
                    prefs.dnsServerConfig = config
                }
                println("Saved $config")
            }.show()
        }
        privacyStatementText.setOnClickListener {
            if(it.tag != null) {
                val i = Intent(Intent.ACTION_VIEW)
                val url = it.tag as URL
                i.data = Uri.parse(url.toURI().toString())
                startActivity(i)
            }
        }
        GlobalScope.launch {
            val context = context
            if (isAdded && !isDetached && context != null) {
                val config = context.getPreferences().dnsServerConfig
                requireActivity().runOnUiThread {
                    updatePrivacyPolicyLink(config)
                }
            }
        }
        updateVpnIndicators()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (vpnStateReceiver != null) requireContext().unregisterLocalReceiver(vpnStateReceiver!!)
    }

    fun startVpn() {
        val prepare = VpnService.prepare(requireContext())

        if (prepare == null) {
            requireContext().startService(Intent(requireContext(), DnsVpnService::class.java))
        } else {
            startActivityForResult(prepare, vpnRequestCode)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == vpnRequestCode && resultCode == Activity.RESULT_OK) {
            startVpn()
        } else if (requestCode == vpnRequestCode) {
            proxyStarting = false
            updateVpnIndicators()
        }
    }

    private fun updateVpnIndicators() {
        when {
            proxyRunning -> {
                statusImage.setImageResource(R.drawable.ic_lock)
                statusImage.clearAnimation()
                startButton.setText(R.string.all_stop)
            }
            proxyStarting -> {
                startButton.setText(R.string.all_stop)
                if (loadingAnimation == null) {
                    loadingAnimation = RotateAnimation(
                        0f,
                        360f,
                        Animation.RELATIVE_TO_SELF,
                        0.5f,
                        Animation.RELATIVE_TO_SELF,
                        0.5f
                    )
                    loadingAnimation?.repeatCount = Animation.INFINITE
                    loadingAnimation?.duration = 2300
                    loadingAnimation?.interpolator = LinearInterpolator()
                    statusImage.startAnimation(loadingAnimation)
                } else if (!loadingAnimation!!.hasStarted() || loadingAnimation!!.hasEnded()) {
                    statusImage.startAnimation(loadingAnimation)
                }
                statusImage.setImageResource(R.drawable.ic_spinner)
            }
            else -> {
                startButton.setText(R.string.all_start)
                statusImage.setImageResource(R.drawable.ic_lock_open)
                statusImage.clearAnimation()
            }
        }
    }

    private fun updatePrivacyPolicyLink(serverInfo:DnsServerInformation<*>) {
        val url = serverInfo.specification.privacyPolicyURL

        if(url != null) {
            privacyStatementText.text = getString(R.string.main_dnssurveillance_privacystatement, serverInfo.name)
            privacyStatementText.tag = url
            privacyStatementText.visibility = View.VISIBLE
        } else {
            privacyStatementText.visibility = View.GONE
        }
    }
}