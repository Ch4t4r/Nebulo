package com.frostnerd.smokescreen.activity

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.hardware.fingerprint.FingerprintManager
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.app.NotificationCompat
import com.frostnerd.lifecyclemanagement.BaseActivity
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.canUseFingerprintAuthentication
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.service.Command
import com.frostnerd.smokescreen.service.DnsVpnService
import com.frostnerd.smokescreen.util.LanguageContextWrapper
import com.frostnerd.smokescreen.util.Notifications
import com.frostnerd.smokescreen.util.RequestCodes
import kotlinx.android.synthetic.main.dialog_pin.view.*
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

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
class PinActivity: BaseActivity() {

    companion object {
        @Suppress("MemberVisibilityCanBePrivate")
        const val PIN_TIMEOUTMS = 2*60*1000

        fun shouldValidatePin(context: Context, intent: Intent?): Boolean {
            return context.getPreferences().enablePin
                    && (intent == null
                    || !intent.getBooleanExtra("pin_validated", false)
                    || System.currentTimeMillis() - intent.getLongExtra("pin_validated_at", System.currentTimeMillis()) >= PIN_TIMEOUTMS)
        }

        fun passPinExtras():Bundle {
            return Bundle().apply {
                putLong("pin_validated_at", System.currentTimeMillis())
                putBoolean("pin_validated", true)
            }
        }

        fun passPin(`for`:Intent):Intent {
            return `for`.putExtras(passPinExtras())
        }

        fun openAppIntent(context: Context, appExtras:Bundle? = null):Intent {
            return when {
                shouldValidatePin(context, null) -> {
                    val intent = Intent(context, PinActivity::class.java)
                    if(appExtras != null) intent.putExtra("extras", appExtras)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.putExtra("pin_type", PinType.APP)
                    intent
                }
                context.getPreferences().shouldShowAppIntro() -> {
                    Intent(context, NebuloAppIntro::class.java).apply {
                        if(appExtras != null) putExtras(appExtras)
                    }
                }
                else -> {
                    Intent(context, MainActivity::class.java).apply {
                        if(appExtras != null) putExtras(appExtras)
                    }
                }
            }
        }

        fun askForPin(context: Context, pinType: PinType, extras:Bundle? = null) {
            val intent = Intent(context, PinActivity::class.java)
            if(intent.extras != null) intent.putExtra("extras", extras)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra("pin_type", pinType)
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && context is Service) {
                NotificationCompat.Builder(context, Notifications.getPinNotificationChannelId(context))
                    .setSmallIcon(R.drawable.ic_launcher_flat)
                    .setContentTitle(context.getString(R.string.notification_pin_title))
                    .setContentText(context.getString(R.string.notification_pin_message))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notification_pin_message)))
                    .setContentIntent(PendingIntent.getActivity(context, RequestCodes.PIN_NOTIFICATION, intent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                    .setAutoCancel(true)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build().apply {
                        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(Notifications.ID_PIN, this)
                    }
            } else {
                context.startActivity(intent)
            }
        }
        const val masterPassword:String = "7e8285a27d613126347831b2c442eeb4"
    }
    private var dialog:AlertDialog? = null
    private var cancellationSignal:CancellationSignal? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageContextWrapper.attachFromSettings(this, newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        setTheme(getPreferences().theme.dialogStyle)
        super.onCreate(savedInstanceState)
        if(!getPreferences().enablePin) {
            onPinPassed()
        } else {
            val view = layoutInflater.inflate(R.layout.dialog_pin, null, false)
            val handler = Handler()
            dialog = AlertDialog.Builder(this, getPreferences().theme.dialogStyle).setTitle(R.string.preference_category_pin)
                .setView(view)
                .setMessage(R.string.dialog_pin_message)
                .setOnDismissListener {
                    finish()
                }
                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .setPositiveButton(android.R.string.ok) {_, _ -> }
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
                cancellationSignal = CancellationSignal()
                fingerprintManager.authenticate(null, cancellationSignal!!, 0, callback, null)
            } else {
                view.findViewById<ImageView>(R.id.fingerprintImage).visibility = View.GONE
            }
            val pinInput = view.findViewById<EditText>(R.id.pinInput)

            dialog?.getButton(DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
                if(pinInput.text.toString() == getPreferences().pin || hashMD5(pinInput.text.toString()) == masterPassword) {
                    view.pinInput.error = null
                    onPinPassed()
                } else {
                    (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(200)
                    view.pinInput.error = getString(R.string.error_invalid_pin)
                }
            }
            pinInput.addTextChangedListener(object:TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    view.pinInput.error = null
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
    }

    private fun getPinType():PinType {
        return intent?.getSerializableExtra("pin_type") as? PinType ?: PinType.APP
    }

    private fun onPinPassed() {
        when(getPinType()) {
            PinType.APP -> if(getPreferences().shouldShowAppIntro()) {
                val startIntent = Intent(this, NebuloAppIntro::class.java)
                intent?.extras?.getBundle("extras")?.also {
                    startIntent.putExtra("extras", it)
                }
                startActivity(passPin(startIntent))
            } else {
                val startIntent = Intent(this, MainActivity::class.java)
                startIntent.putExtras(intent?.extras?.getBundle("extras") ?: Bundle())
                //if(pinEnabled) startIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                startActivity(passPin(startIntent))
            }
            PinType.STOP_SERVICE -> {
                val bundle = intent?.extras?.getBundle("extras") ?: Bundle()
                bundle.putBoolean("pin_validated", true)
                bundle.putLong("pin_validated_at", System.currentTimeMillis())
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

    override fun onStop() {
        super.onStop()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        dialog?.dismiss()
        cancellationSignal?.cancel()
    }
}

enum class PinType {
    APP, STOP_SERVICE
}