package com.frostnerd.smokescreen.tasker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import com.frostnerd.lifecyclemanagement.BaseActivity
import com.frostnerd.materialedittext.MaterialEditText
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.dialog.NewServerDialog
import kotlinx.android.synthetic.main.activity_tasker_configure.*

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */
class ConfigureActivity : BaseActivity() {
    override fun getConfiguration(): Configuration {
        return Configuration.withDefaults()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasker_configure)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        createLayout()
        applyOldConfiguration()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            saveConfiguration()
            finish()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun applyOldConfiguration() {
        if (intent != null && intent.extras != null) {
            val settings = intent.extras!!.getBundle(TaskerHelper.EXTRAS_BUNDLE_KEY)
            if (settings != null) {
                val action = settings.getString(TaskerHelper.DATA_KEY_ACTION, "start")
                when (action) {
                    "stop" -> {
                        spinner.setSelection(1)
                    }
                    "start" -> {
                        spinner.setSelection(0)
                        startIfRunning.isChecked = settings.getBoolean(TaskerHelper.DATA_KEY_STARTIFRUNNING, true)
                        useServersFromConfig.isChecked = !settings.containsKey(TaskerHelper.DATA_KEY_PRIMARYSERVER)
                        if (!useServersFromConfig.isChecked) {
                            primaryServer.setText(settings.getString(TaskerHelper.DATA_KEY_PRIMARYSERVER))
                            secondaryServer.setText(settings.getString(TaskerHelper.DATA_KEY_SECONDARYSERVER))
                        }
                    }
                }
            }
        }
    }

    private fun createLayout() {
        useServersFromConfig.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                serverConfigWrap.visibility = View.GONE
            } else {
                serverConfigWrap.visibility = View.VISIBLE
            }
        }
        val adapter: ArrayAdapter<CharSequence> = ArrayAdapter.createFromResource(
            this,
            R.array.tasker_action_values,
            R.layout.item_tasker_action_spinner_item
        )
        adapter.setDropDownViewResource(R.layout.item_tasker_action_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) {
                    startConfigWrap.visibility = View.VISIBLE
                } else {
                    startConfigWrap.visibility = View.GONE
                }
            }
        }
        addUrlTextWatcher(primaryServerWrap, primaryServer, false)
        addUrlTextWatcher(secondaryServerWrap, secondaryServer, true)
    }

    private fun addUrlTextWatcher(materialEditText: MaterialEditText, editText: EditText, emptyAllowed: Boolean) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                val valid = (emptyAllowed && s.isBlank()) || NewServerDialog.SERVER_URL_REGEX.matches(s.toString())

                materialEditText.indicatorState = if (valid) {
                    if (s.isBlank()) MaterialEditText.IndicatorState.UNDEFINED
                    else MaterialEditText.IndicatorState.CORRECT
                } else MaterialEditText.IndicatorState.INCORRECT
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

        })
    }

    override fun onBackPressed() {
        saveConfiguration()
        super.onBackPressed()
    }

    private fun saveConfiguration() {
        var valid = true
        val action = if (spinner.selectedItemPosition == 0) "start" else "stop"
        val resultIntent = Intent()
        val settings = Bundle()
        settings.putString(TaskerHelper.DATA_KEY_ACTION, action)
        resultIntent.putExtra(
            TaskerHelper.EXTRAS_BLURB_KEY,
            resources.getStringArray(R.array.tasker_action_values)[spinner.selectedItemPosition]
        )
        if (action == "start") {
            settings.putBoolean(TaskerHelper.DATA_KEY_STARTIFRUNNING, startIfRunning.isChecked)
            if (!useServersFromConfig.isChecked) {
                if (primaryServerWrap.indicatorState != MaterialEditText.IndicatorState.INCORRECT &&
                    secondaryServerWrap.indicatorState != MaterialEditText.IndicatorState.INCORRECT
                ) {
                    var primary = primaryServer.text.toString()
                    var secondary = if (secondaryServer.text.isNullOrBlank()) null else secondaryServer.text.toString()
                    if (!primary.startsWith("https")) primary = "https://$primary"
                    if (secondary != null && !secondary.startsWith("https")) secondary = "https://$secondary"

                    settings.putString(TaskerHelper.DATA_KEY_PRIMARYSERVER, primary)
                    settings.putString(TaskerHelper.DATA_KEY_SECONDARYSERVER, secondary)
                    if (secondary != null) {
                        resultIntent.putExtra(
                            TaskerHelper.EXTRAS_BLURB_KEY,
                            getString(R.string.tasker_start_app_custom_urls, primary, secondary)
                        )
                    } else {
                        resultIntent.putExtra(
                            TaskerHelper.EXTRAS_BLURB_KEY,
                            getString(R.string.tasker_start_app_custom_url, primary)
                        )
                    }
                    settings.putBoolean(TaskerHelper.DATA_KEY_STARTIFRUNNING, startIfRunning.isChecked)
                } else {
                    valid = false
                }
            }
        }
        if (valid) {
            resultIntent.putExtra(TaskerHelper.EXTRAS_BUNDLE_KEY, settings)
            setResult(Activity.RESULT_OK, resultIntent)
        } else setResult(Activity.RESULT_CANCELED)
    }
}