package com.frostnerd.smokescreen.fragment

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.frostnerd.dnstunnelproxy.DnsServerInformation
import com.frostnerd.general.service.isServiceRunning
import com.frostnerd.smokescreen.*
import com.frostnerd.smokescreen.activity.SpeedTestActivity
import com.frostnerd.smokescreen.dialog.ServerChoosalDialog
import com.frostnerd.smokescreen.service.Command
import com.frostnerd.smokescreen.service.DnsVpnService
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
    private var proxyState:ProxyState = ProxyState.NOT_RUNNING
    private var vpnStateReceiver: BroadcastReceiver? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onResume() {
        super.onResume()
        proxyState = if(requireContext().isServiceRunning(DnsVpnService::class.java)) {
            if(DnsVpnService.paused) ProxyState.PAUSED
            else ProxyState.RUNNING
        } else ProxyState.NOT_RUNNING
        updateVpnIndicators()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        startButton.setOnClickListener {
            proxyState = when (proxyState) {
                ProxyState.RUNNING -> {
                    DnsVpnService.sendCommand(requireContext(), Command.STOP)
                    ProxyState.NOT_RUNNING
                }
                ProxyState.PAUSED -> {
                    DnsVpnService.sendCommand(it.context, Command.PAUSE_RESUME)
                    ProxyState.STARTING
                }
                else -> {
                    startVpn()
                    ProxyState.STARTING
                }
            }
            updateVpnIndicators()
        }
        startButton.setOnTouchListener { _, event ->
            if (proxyState == ProxyState.RUNNING || proxyState == ProxyState.STARTING) {
                false
            } else {
                if (event.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED != 0) {
                    if (event.action == MotionEvent.ACTION_UP) {
                        if (VpnService.prepare(context!!) != null) {
                            showInfoTextDialog(context!!,
                                getString(R.string.dialog_overlaydetected_title),
                                getString(R.string.dialog_overlaydetected_message),
                                positiveButton = null,
                                negativeButton = null,
                                neutralButton = getString(android.R.string.ok) to { dialog, _ ->
                                    dialog.dismiss()
                                    startVpn()
                                    proxyState = ProxyState.STARTING
                                }
                            )
                            true
                        } else false
                    } else false
                } else false
            }
        }
        speedTest.setOnClickListener {
            startActivity(Intent(context!!, SpeedTestActivity::class.java))
        }
        vpnStateReceiver = requireContext().registerLocalReceiver(
            listOf(
                DnsVpnService.BROADCAST_VPN_ACTIVE,
                DnsVpnService.BROADCAST_VPN_INACTIVE,
                DnsVpnService.BROADCAST_VPN_PAUSED,
                DnsVpnService.BROADCAST_VPN_RESUMED
            )
        ) {
            if (it != null && it.action != null) {
                when (it.action) {
                    DnsVpnService.BROADCAST_VPN_ACTIVE, DnsVpnService.BROADCAST_VPN_RESUMED -> {
                        proxyState = ProxyState.RUNNING
                    }
                    DnsVpnService.BROADCAST_VPN_INACTIVE -> {
                        proxyState = ProxyState.NOT_RUNNING
                    }
                    DnsVpnService.BROADCAST_VPN_PAUSED -> {
                        proxyState = ProxyState.PAUSED
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
            }.show()
        }
        privacyStatementText.setOnClickListener {
            if (it.tag != null) {
                val i = Intent(Intent.ACTION_VIEW)
                val url = it.tag as URL
                i.data = Uri.parse(url.toURI().toString())
                try {
                    startActivity(i)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(
                        context!!,
                        R.string.error_no_webbrowser_installed,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        GlobalScope.launch {
            val context = context
            if (isAdded && !isDetached && context != null) {
                val config = context.getPreferences().dnsServerConfig
                updatePrivacyPolicyLink(config)
            }
        }
        updateVpnIndicators()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (vpnStateReceiver != null) requireContext().unregisterLocalReceiver(vpnStateReceiver!!)
    }

    private fun startVpn() {
        val prepare = VpnService.prepare(requireContext()).apply {
            this?.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        }

        if (prepare == null) {
            requireContext().startService(Intent(requireContext(), DnsVpnService::class.java))
            getPreferences().vpnInformationShown = true
        } else {
            if (getPreferences().vpnInformationShown) {
                startActivityForResult(prepare, vpnRequestCode)
            } else {
                showInfoTextDialog(requireContext(),
                    getString(R.string.dialog_vpninformation_title),
                    getString(R.string.dialog_vpninformation_message),
                    neutralButton = getString(android.R.string.ok) to { dialog, _ ->
                        startActivityForResult(prepare, vpnRequestCode)
                        dialog.dismiss()
                    }, withDialog = {
                        setCancelable(false)
                    })
                getPreferences().vpnInformationShown = true
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == vpnRequestCode && resultCode == Activity.RESULT_OK) {
            startVpn()
        } else if (requestCode == vpnRequestCode) {
            updateVpnIndicators()
        }
    }

    private fun updateVpnIndicators() {
        val privateDnsActive = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            false
        } else {
            (context!!.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).let {
                if (it.activeNetwork == null) false
                else it.getLinkProperties(it.activeNetwork)?.isPrivateDnsActive ?: false
            }
        }
        when(proxyState) {
            ProxyState.RUNNING -> {
                privateDnsInfo.visibility = View.INVISIBLE
                statusImage.setImageResource(R.drawable.ic_lock)
                statusImage.clearAnimation()
                startButton.setText(R.string.all_stop)
            }
            ProxyState.STARTING -> {
                privateDnsInfo.visibility = View.INVISIBLE
                startButton.setText(R.string.all_stop)
                statusImage.setImageResource(R.drawable.ic_lock_half_open)
            }
            ProxyState.PAUSED -> {
                privateDnsInfo.visibility = View.INVISIBLE
                startButton.setText(R.string.all_resume)
                statusImage.setImageResource(R.drawable.ic_lock_half_open)
            }
            else -> {
                startButton.setText(R.string.all_start)
                if (privateDnsActive) {
                    statusImage.setImageResource(R.drawable.ic_lock)
                    statusImage.clearAnimation()
                    privateDnsInfo.visibility = View.VISIBLE
                } else {
                    statusImage.setImageResource(R.drawable.ic_lock_open)
                    statusImage.clearAnimation()
                    privateDnsInfo.visibility = View.INVISIBLE
                }
            }
        }
    }

    private fun updatePrivacyPolicyLink(serverInfo: DnsServerInformation<*>) {
        activity?.runOnUiThread {
            val url = serverInfo.specification.privacyPolicyURL
            val text = view?.findViewById<TextView>(R.id.privacyStatementText)
            if (url != null && text != null) {
                text.text =
                    getString(R.string.main_dnssurveillance_privacystatement, serverInfo.name)
                text.tag = url
                text.visibility = View.VISIBLE
            } else if (text != null) {
                text.visibility = View.GONE
            }
        }
    }

    enum class ProxyState {
        NOT_RUNNING, STARTING, RUNNING, PAUSED
    }
}