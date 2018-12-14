package com.frostnerd.smokescreen.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.Window
import com.frostnerd.lifecyclemanagement.BaseActivity
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.service.DnsVpnService
import com.frostnerd.smokescreen.startForegroundServiceCompat

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 * <p>
 * development@frostnerd.com
 */
class BackgroundVpnConfigureActivity : BaseActivity() {

    override fun getConfiguration(): Configuration {
        return Configuration.withDefaults()
    }

    companion object {
        val extraKeyPrimaryUrl = "primary_url"
        val extraKeySecondaryUrl = "secondary_url"
        private val VPN_REQUEST_CODE = 1

        fun prepareVpn(context: Context, primaryServerUrl: String? = null, secondaryServerUrl: String? = null) {
            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent == null) {
                startService(context, primaryServerUrl, secondaryServerUrl)
            } else {
                val intent = Intent(context, BackgroundVpnConfigureActivity::class.java)
                intent.putExtra(extraKeyPrimaryUrl, primaryServerUrl)
                intent.putExtra(extraKeySecondaryUrl, secondaryServerUrl)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }

        private fun startService(
            context: Context,
            primaryServerUrl: String? = null,
            secondaryServerUrl: String? = null
        ) {
            val intent = Intent(context, DnsVpnService::class.java)
            if (primaryServerUrl != null) intent.putExtra(extraKeyPrimaryUrl, primaryServerUrl)
            if (secondaryServerUrl != null) intent.putExtra(extraKeySecondaryUrl, secondaryServerUrl)
            context.startForegroundServiceCompat(intent)
        }
    }

    var requestTime: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getPreferences().theme.dialogStyle)
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()
        actionBar?.hide()

        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent == null) {
            startService()
            finish()
        } else {
            requestTime = System.currentTimeMillis()
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                startService()
                finish()
            } else if (resultCode == Activity.RESULT_CANCELED) {
                if (System.currentTimeMillis() - requestTime <= 500) { // Most likely the system denied the request automatically
                    showPermissionDenialDialog()
                } else {
                    finish()
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun startService() {
        startService(this, intent.extras?.getString(extraKeyPrimaryUrl), intent.extras?.getString(extraKeySecondaryUrl))
    }

    private fun showPermissionDenialDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this, getPreferences().theme.dialogStyle)
            .setTitle(getString(R.string.app_name) + " - " + getString(R.string.information))
            .setPositiveButton(R.string.open_app) { _, _ ->
                val intent = Intent(this@BackgroundVpnConfigureActivity, MainActivity::class.java)
                startActivity(intent)
                finish()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.cancel()
                finish()
            }
            .setOnDismissListener {
                finish()
            }
            .setOnCancelListener {
                finish()
            }.show()
    }
}