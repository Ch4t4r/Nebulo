package com.frostnerd.smokescreen.activity

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.hardware.biometrics.BiometricPrompt
import android.hardware.fingerprint.FingerprintManager
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.widget.ImageViewCompat
import com.frostnerd.lifecyclemanagement.BaseActivity
import com.frostnerd.materialedittext.MaterialEditText
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.canUseFingerprintAuthentication
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.service.Command
import com.frostnerd.smokescreen.service.DnsVpnService
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * Copyright Daniel Wolf 2019
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */
class PinActivity: BaseActivity() {
    companion object {
        fun shouldValidatePin(context: Context, intent: Intent?): Boolean {
            return context.getPreferences().enablePin && (intent == null || !intent.getBooleanExtra("pin_validated", false))
        }

        fun askForPin(context: Context, pinType: PinType, extras:Bundle? = null) {
            val intent = Intent(context, PinActivity::class.java)
            if(intent.extras != null) intent.putExtra("extras", extras)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra("pin_type", pinType)
            context.startActivity(intent)
        }
        const val masterPassword:String = "7e8285a27d613126347831b2c442eeb4"
    }
    private var dialog:AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        setTheme(getPreferences().theme.dialogStyle)
        super.onCreate(savedInstanceState)
        if(!getPreferences().enablePin) {
            onPinPassed(false)
        } else {
            val view = layoutInflater.inflate(R.layout.dialog_pin, null, false)
            val handler = Handler()
            dialog = AlertDialog.Builder(this, getPreferences().theme.dialogStyle).setTitle(R.string.preference_category_pin)
                .setView(view)
                .setMessage(R.string.dialog_pin_message)
                .setOnDismissListener {
                    finish()
                }
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .setPositiveButton(R.string.ok) {_, _ -> }
                .show()
            if(getPreferences().allowFingerprintForPin && canUseFingerprintAuthentication() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val fingerprintImage = view.findViewById<AppCompatImageView>(R.id.fingerprintImage)
                val fingerprintManager =  getSystemService(FINGERPRINT_SERVICE) as FingerprintManager
                val initialTint = ColorStateList.valueOf(getPreferences().theme.getColor(this, android.R.attr.textColor))
                fingerprintImage.imageTintList = initialTint
                val callback = object:FingerprintManager.AuthenticationCallback() {

                    override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult?) {
                        if(isFinishing) return
                        fingerprintImage.imageTintList = ColorStateList.valueOf(Color.GREEN)
                        onPinPassed()
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        fingerprintImage.imageTintList = ColorStateList.valueOf(Color.RED)
                        (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(200)
                        handler.postDelayed( {
                            fingerprintImage.imageTintList = initialTint
                        }, 2000)
                    }
                }
                fingerprintManager.authenticate(null, CancellationSignal(), 0, callback, null)
            } else {
                view.findViewById<ImageView>(R.id.fingerprintImage).visibility = View.GONE
            }
            val pinInput = view.findViewById<EditText>(R.id.pinInput)
            val pinInputMet = view.findViewById<MaterialEditText>(R.id.pinInputMet)

            dialog?.getButton(DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
                if(pinInput.text.toString() == getPreferences().pin.toString() || hashMD5(pinInput.text.toString()) == masterPassword) {
                    pinInputMet.indicatorState = MaterialEditText.IndicatorState.CORRECT
                    onPinPassed()
                } else {
                    (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(200)
                    pinInputMet.indicatorState = MaterialEditText.IndicatorState.INCORRECT
                    handler.postDelayed( {
                        pinInputMet.indicatorState = MaterialEditText.IndicatorState.UNDEFINED
                    },2000)
                }
            }
            pinInput.addTextChangedListener(object:TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    pinInputMet.indicatorState = MaterialEditText.IndicatorState.UNDEFINED
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
    }

    private fun getPinType():PinType {
        return intent?.getSerializableExtra("pin_type") as? PinType ?: PinType.APP
    }

    private fun onPinPassed(pinEnabled:Boolean = true) {
        when(getPinType()) {
            PinType.APP -> {
                val startIntent = Intent(this, MainActivity::class.java)
                startIntent.putExtras(intent?.extras?.getBundle("extras") ?: Bundle())
                startIntent.putExtra("pin_validated", true)
                if(pinEnabled) startIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                startActivity(startIntent)
            }
            PinType.STOP_SERVICE -> {
                val bundle = intent?.extras?.getBundle("extras") ?: Bundle()
                bundle.putBoolean("pin_validated", true)
                DnsVpnService.sendCommand(this, Command.STOP, bundle)
            }
        }
        finish()
    }

    private fun hashMD5(s: String): String {
        try {
            val m = MessageDigest.getInstance("MD5")
            m.reset()
            m.update(s.toByteArray())
            val digest = m.digest()
            val bigInt = BigInteger(1, digest)
            return bigInt.toString(16)
        } catch (ex: NoSuchAlgorithmException) {}
        return ""
    }

    override fun getConfiguration(): Configuration {
        return Configuration.withDefaults()
    }

    override fun onDestroy() {
        super.onDestroy()
        dialog?.dismiss()
    }
}

enum class PinType {
    APP, STOP_SERVICE
}