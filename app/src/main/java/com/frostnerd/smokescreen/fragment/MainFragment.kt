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
import com.frostnerd.encrypteddnstunnelproxy.ServerConfiguration
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.dialog.ServerChoosalDialog
import com.frostnerd.smokescreen.service.Command
import com.frostnerd.smokescreen.service.DnsVpnService
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.registerLocalReceiver
import com.frostnerd.smokescreen.unregisterLocalReceiver
import kotlinx.android.synthetic.main.fragment_main.*
import androidx.fragment.app.Fragment
import com.frostnerd.baselibrary.service.isServiceRunning
import java.net.URL


/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
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
        updateStatusImage()
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
            updateStatusImage()
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
                updateStatusImage()
            }
        }
        serverButton.setOnClickListener {
            ServerChoosalDialog(requireContext()) { primaryServerUrl: ServerConfiguration, secondaryServerUrl: ServerConfiguration?, customServers: Boolean ->
                updatePrivacyPolicyLink(primaryServerUrl)
                val prefs = requireContext().getPreferences()
                prefs.edit {
                    prefs.areCustomServers = customServers
                    prefs.primaryServerConfig = primaryServerUrl
                    prefs.secondaryServerConfig = secondaryServerUrl
                }
                println("Saved $primaryServerUrl, $secondaryServerUrl")
            }.show()
        }
        updatePrivacyPolicyLink(requireContext().getPreferences().primaryServerConfig)
        privacyStatementText.setOnClickListener {
            val i = Intent(Intent.ACTION_VIEW)
            val url = it.tag as URL
            i.data = Uri.parse(url.toURI().toString())
            startActivity(i)
        }
        updateStatusImage()
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
            updateStatusImage()
        }
    }

    fun updateStatusImage() {
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

    fun updatePrivacyPolicyLink(serverConfiguration: ServerConfiguration) {
        val url = serverConfiguration.serverInformation?.specification?.privacyPolicyURL

        if(url != null) {
            privacyStatementText.text = getString(R.string.main_dnssurveillance_privacystatement, serverConfiguration.serverInformation!!.name)
            privacyStatementText.tag = url
            privacyStatementText.visibility = View.VISIBLE
        } else {
            privacyStatementText.visibility = View.GONE
        }
    }
}