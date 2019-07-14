package com.frostnerd.smokescreen.activity

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.Switch
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.frostnerd.cacheadapter.ListDataSource
import com.frostnerd.cacheadapter.ModelAdapterBuilder
import com.frostnerd.general.service.isServiceRunning
import com.frostnerd.lifecyclemanagement.BaseActivity
import com.frostnerd.lifecyclemanagement.BaseViewHolder
import com.frostnerd.lifecyclemanagement.LifecycleCoroutineScope
import com.frostnerd.lifecyclemanagement.launchWithLifecylce
import com.frostnerd.smokescreen.*
import com.frostnerd.smokescreen.database.entities.DnsRule
import com.frostnerd.smokescreen.database.entities.HostSource
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.dialog.DnsRuleDialog
import com.frostnerd.smokescreen.dialog.ExportDnsRulesDialog
import com.frostnerd.smokescreen.dialog.NewHostSourceDialog
import com.frostnerd.smokescreen.service.RuleExportService
import com.frostnerd.smokescreen.service.RuleImportService
import com.frostnerd.smokescreen.util.SpaceItemDecorator
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_dns_rules.*
import kotlinx.android.synthetic.main.item_datasource.view.*
import kotlinx.android.synthetic.main.item_datasource.view.cardContent
import kotlinx.android.synthetic.main.item_datasource.view.delete
import kotlinx.android.synthetic.main.item_datasource.view.enable
import kotlinx.android.synthetic.main.item_datasource.view.text
import kotlinx.android.synthetic.main.item_datasource_rules.view.*
import kotlinx.android.synthetic.main.item_dnsrule_host.view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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
class DnsRuleActivity : BaseActivity() {
    private lateinit var sourceAdapter: RecyclerView.Adapter<*>
    private lateinit var sourceAdapterList: MutableList<HostSource>
    private lateinit var adapterDataSource: ListDataSource<HostSource>
    private lateinit var userDnsRules:MutableList<DnsRule>
    private lateinit var sourceRuleCount:MutableMap<HostSource, Int?>
    private var importDoneReceiver:BroadcastReceiver? = null
    private var exportDoneReceiver:BroadcastReceiver? = null
    private var refreshProgressShown = false
    private var exportProgressShown = false
    private var fileChosenRequestCode = 5
    private var fileChosenCallback: ((Uri) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dns_rules)
        setSupportActionBar(toolBar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        getDatabase().dnsRuleRepository().getUserRulesAsnc(block = {
            userDnsRules = it.toMutableList()
        }, coroutineScope = LifecycleCoroutineScope(this, ui = false))
        addSource.setOnClickListener {
            NewHostSourceDialog(this, onSourceCreated = { newSource ->
                if (!sourceAdapterList.contains(newSource)) {
                    val insertPos = sourceAdapterList.indexOfFirst {
                        it.name > newSource.name
                    }.let {
                        when (it) {
                            0 -> 0
                            -1 -> sourceAdapterList.size
                            else -> it
                        }
                    }
                    sourceAdapterList.add(insertPos, newSource)
                    sourceAdapter.notifyItemInserted(insertPos)
                    getDatabase().hostSourceDao().insert(newSource)
                }
            }, showFileChooser = { callback ->
                fileChosenCallback = callback
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/*"
                }, fileChosenRequestCode)
            }).show()
        }
        refresh.setOnClickListener {
            if(isServiceRunning(RuleImportService::class.java)) {
                startService(Intent(this, RuleImportService::class.java).putExtra("abort", true))
            } else {
                startService(Intent(this, RuleImportService::class.java))
                refreshProgress.show()
                refreshProgressShown = true
            }
        }
        export.setOnClickListener {
            if (isServiceRunning(RuleExportService::class.java)) {
                startService(Intent(this, RuleExportService::class.java).putExtra("abort", true))
            } else {
                ExportDnsRulesDialog(this) { exportFromSources, exportUserRules ->
                    fileChosenCallback = {
                        val intent = Intent(this, RuleExportService::class.java).apply {
                            putExtra(
                                "params",
                                RuleExportService.Params(exportFromSources, exportUserRules, it.toString())
                            )
                        }
                        startService(intent)
                        exportProgress.show()
                        exportProgressShown = true
                    }
                    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        putExtra(Intent.EXTRA_TITLE, "dnsRuleExport.txt")
                        type = "text/*"
                    }, fileChosenRequestCode)
                }.show()
            }
        }
        sourceAdapterList = getDatabase().hostSourceDao().getAll().toMutableList()
        sourceRuleCount = sourceAdapterList.map {
            it to (null as Int?)
        }.toMap().toMutableMap()
        adapterDataSource = ListDataSource(sourceAdapterList)
        var showUserRules = false
        var userRuleCount = 0
        sourceAdapter = ModelAdapterBuilder.withModelAndViewHolder({ view, type ->
            when (type) {
                0 -> SourceViewHolder(view, deleteSource = {
                    showInfoTextDialog(this,
                        getString(R.string.dialog_deletehostsource_title, it.name),
                        getString(R.string.dialog_deletehostsource_message, it.name),
                        getString(R.string.all_yes) to { dialog, _ ->
                            val pos = sourceAdapterList.indexOf(it)
                            sourceAdapterList.removeAt(pos)
                            sourceAdapter.notifyItemRemoved(pos)
                            GlobalScope.launch {
                                getDatabase().dnsRuleDao().deleteAllFromSource(it.id)
                                getDatabase().hostSourceDao().delete(it)
                            }
                            dialog.dismiss()
                        }, getString(R.string.all_no) to { dialog, _ ->
                            dialog.dismiss()
                        }, null)
                }, changeSourceStatus = { hostSource, enabled ->
                    hostSource.enabled = enabled
                    getDatabase().hostSourceDao().update(hostSource)
                })
                1 -> CustomRulesViewHolder(view, changeSourceStatus = {
                    getPreferences().customHostsEnabled = it
                }, clearRules = {
                    showInfoTextDialog(this,
                        getString(R.string.dialog_clearuserrules_title),
                        getString(R.string.dialog_clearuserrules_message),
                        getString(R.string.all_yes) to { dialog, _ ->
                            getDatabase().dnsRuleRepository().deleteAllUserRulesAsync()
                            userDnsRules = mutableListOf()
                            if(showUserRules) {
                                sourceAdapter.notifyItemRangeRemoved(sourceAdapterList.size + 1, userRuleCount)
                            }
                            userRuleCount = 0
                            dialog.dismiss()
                        }, getString(R.string.all_no) to { dialog, _ ->
                            dialog.dismiss()
                        }, null)
                }, changeRuleVisibility = {
                    showUserRules = !showUserRules
                    if(showUserRules) {
                        userRuleCount = userDnsRules.size
                        runOnUiThread {
                            sourceAdapter.notifyItemRangeInserted(sourceAdapterList.size + 1, userRuleCount)
                        }
                    } else {
                        sourceAdapter.notifyItemRangeRemoved(sourceAdapterList.size + 1, userRuleCount)
                        userRuleCount = 0
                    }
                }, createRule = {
                    DnsRuleDialog(this, onRuleCreated = { newRule ->
                        val insertPos = userDnsRules.indexOfFirst {
                            it.host > newRule.host
                        }.let {
                            when (it) {
                                0 -> 0
                                -1 -> userDnsRules.size
                                else -> it
                            }
                        }
                        val id = if(newRule.isWhitelistRule()) {
                            getDatabase().dnsRuleDao().insertWhitelist(newRule)
                            if(userDnsRules.any {
                                    println("$it vs $newRule")
                                    it.host == newRule.host && it.type == newRule.type
                                }) -1L
                            else 0L
                        } else getDatabase().dnsRuleDao().insertIgnore(newRule)
                        if(id != -1L) {
                            userDnsRules.add(insertPos, newRule)
                            val wereRulesShown = showUserRules
                            showUserRules = true
                            if(wereRulesShown) {
                                userRuleCount += 1
                                sourceAdapter.notifyItemInserted(sourceAdapterList.size + 1 + insertPos)
                            } else {
                                userRuleCount = userDnsRules.size
                                runOnUiThread {
                                    sourceAdapter.notifyItemChanged(sourceAdapterList.size)
                                    sourceAdapter.notifyItemRangeInserted(sourceAdapterList.size + 1, userRuleCount)
                                }
                            }
                        } else {
                            Snackbar.make(findViewById(android.R.id.content), R.string.window_dnsrules_hostalreadyexists, Snackbar.LENGTH_LONG).show()
                        }
                    }).show()
                })
                else -> CustomRuleHostViewHolder(view, deleteRule =  {
                    val index = userDnsRules.indexOf(it)
                    userDnsRules.remove(it)
                    getDatabase().dnsRuleRepository().removeAsync(it)
                    userRuleCount -= 1
                    sourceAdapter.notifyItemRemoved(sourceAdapterList.size + 1 + index)
                }, editRule = {
                    DnsRuleDialog(this, it) { newRule ->
                        val rows = getDatabase().dnsRuleDao().updateIgnore(newRule)
                        if(rows > 0) {
                            val index = userDnsRules.indexOf(it)
                            userDnsRules[index] = newRule
                            sourceAdapter.notifyItemChanged(sourceAdapterList.size + 1 + index)
                        } else {
                            Snackbar.make(findViewById(android.R.id.content), R.string.window_dnsrules_hostalreadyexists, Snackbar.LENGTH_LONG).show()
                        }
                    }.show()
                })
            }
        }, adapterDataSource) {
            viewBuilder = { parent, type ->
                layoutInflater.inflate(
                    when (type) {
                        0 -> R.layout.item_datasource
                        1 -> R.layout.item_datasource_rules
                        else -> R.layout.item_dnsrule_host
                    },
                    parent,
                    false
                )
            }
            getItemCount = {
                sourceAdapterList.size + 1 + userRuleCount
            }
            bindModelView = { viewHolder, position, data ->
                (viewHolder as SourceViewHolder).display(data)
                when {
                    sourceRuleCount[data] != null -> viewHolder.ruleCount.text = getString(R.string.window_dnsrules_customhosts_hostsource_rulecount, sourceRuleCount[data])
                    data.enabled -> launchWithLifecylce(false) {
                        sourceRuleCount[data] = getDatabase().dnsRuleDao().getCountForHostSource(data.id)
                        runOnUiThread {
                            sourceAdapter.notifyItemChanged(position)
                        }
                    }
                    else -> viewHolder.ruleCount.text = getString(R.string.window_dnsrules_customhosts_hostsource_rulecount_pending)
                }
            }
            bindNonModelView = { viewHolder, position ->
                if(viewHolder is CustomRulesViewHolder) {
                    viewHolder.enabled.isChecked = getPreferences().customHostsEnabled
                    if(!viewHolder.elementsShown && showUserRules) {
                        viewHolder.animateCaretSpin()
                        viewHolder.elementsShown = true
                    }
                } else if(viewHolder is CustomRuleHostViewHolder) {
                    viewHolder.display(userDnsRules[position - sourceAdapterList.size - 1])
                }
            }
            getViewType = { position ->
                when {
                    position < sourceAdapterList.size -> 0
                    position == sourceAdapterList.size -> 1
                    else -> 2
                }
            }
            runOnUiThread = {
                this@DnsRuleActivity.runOnUiThread(it)
            }

        }.build()
        list.layoutManager = LinearLayoutManager(this)
        list.recycledViewPool.setMaxRecycledViews(1, 1)
        list.addItemDecoration(SpaceItemDecorator(this))
        list.adapter = sourceAdapter
        if(isServiceRunning(RuleImportService::class.java)) {
            refreshProgress.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                refreshProgress.show()
                refreshProgressShown = true
            }
        }
        if(isServiceRunning(RuleExportService::class.java)) {
            exportProgress.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                exportProgressShown = true
                exportProgress.show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == fileChosenRequestCode && resultCode == RESULT_OK){
            if(data?.data != null) fileChosenCallback?.invoke(data.data!!)
            fileChosenCallback = null
        }
    }

    override fun onResume() {
        super.onResume()
        importDoneReceiver = registerLocalReceiver(IntentFilter(RuleImportService.BROADCAST_IMPORT_DONE)) {
            refreshProgress.hide()
            sourceRuleCount.keys.forEach {
                sourceRuleCount[it] = null
            }
            sourceAdapterList.forEachIndexed { index, hostSource ->
                if(hostSource.enabled) sourceAdapter.notifyItemChanged(index)
            }
            refreshProgressShown = false
        }
        exportDoneReceiver = registerLocalReceiver(IntentFilter(RuleExportService.BROADCAST_EXPORT_DONE)) {
            exportProgress.hide()
            exportProgressShown = false
        }
        if(!isServiceRunning(RuleImportService::class.java) && refreshProgressShown) {
            refreshProgress.hide()
            refreshProgressShown = false
        }
        if(!isServiceRunning(RuleExportService::class.java) && exportProgressShown) {
            exportProgress.show()
            exportProgressShown = false
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterLocalReceiver(importDoneReceiver)
        unregisterLocalReceiver(exportDoneReceiver)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_dnsrule, menu)
        val switch =  menu?.getItem(0)?.actionView?.findViewById<Switch>(R.id.actionbarSwitch)
        switch?.setOnCheckedChangeListener { _, isChecked ->
            getPreferences().dnsRulesEnabled = isChecked
            overlay.visibility = if(isChecked) View.GONE else View.VISIBLE
        }
        switch?.isChecked = getPreferences().dnsRulesEnabled
        return true
    }

    override fun getConfiguration(): Configuration {
        return Configuration.withDefaults()
    }


    private class SourceViewHolder(
        view: View,
        deleteSource: (HostSource) -> Unit,
        changeSourceStatus: (HostSource, enabled: Boolean) -> Unit
    ) : BaseViewHolder(view) {
        val text = view.text
        val subText = view.subText
        val enabled = view.enable
        val delete = view.delete
        val ruleCount = view.ruleCount
        lateinit var source: HostSource

        init {
            delete.setOnClickListener {
                deleteSource(source)
            }
            enabled.setOnCheckedChangeListener { _, isChecked ->
                changeSourceStatus(source, isChecked)
            }
            view.cardContent.setOnClickListener {
                enabled.isChecked = !enabled.isChecked
            }
        }

        fun display(source: HostSource) {
            this.source = source
            text.text = source.name
            enabled.isChecked = source.enabled
            subText.text = source.source
        }

        override fun destroy() {}
    }

    private class CustomRulesViewHolder(view: View,
                                        changeSourceStatus: (Boolean) -> Unit,
                                        clearRules: () -> Unit,
                                        changeRuleVisibility:(showRules:Boolean) -> Unit,
                                        createRule:() -> Unit) :
        BaseViewHolder(view) {
        val clear = view.clear
        val enabled = view.enable
        val openList = view.openList
        val add = view.add
        var elementsShown = false

        init {
            enabled.setOnCheckedChangeListener { _, isChecked ->
                changeSourceStatus(isChecked)
            }
            clear.setOnClickListener {
                clearRules()
            }
            openList.setOnClickListener {
                animateCaretSpin()
                changeRuleVisibility(!elementsShown)
                elementsShown = !elementsShown
            }
            view.cardContent.setOnClickListener {
                enabled.isChecked = !enabled.isChecked
            }
            add.setOnClickListener {
                createRule()
            }
        }

        fun animateCaretSpin(){
            if(elementsShown) {
                openList.animate().rotationBy(-90f).setDuration(350).start()
            } else {
                openList.animate().rotationBy(90f).setDuration(350).start()
            }
        }

        override fun destroy() {}
    }

    private class CustomRuleHostViewHolder(view:View,
                                           deleteRule:(DnsRule) -> Unit,
                                           editRule:(DnsRule) -> Unit):BaseViewHolder(view) {
        val text = view.text
        val delete = view.delete
        val cardContent = view.cardContent
        val whitelistIndicator = view.whitelistIndicator
        lateinit var dnsRule:DnsRule

        init {
            delete.setOnClickListener {
                deleteRule(dnsRule)
            }
            cardContent.setOnClickListener {
                editRule(dnsRule)
            }
        }

        fun display(rule:DnsRule) {
            dnsRule = rule
            text.text = rule.host
            whitelistIndicator.visibility = if(rule.isWhitelistRule()) View.VISIBLE else View.GONE
        }
        override fun destroy() {}
    }

    companion object {
        val defaultHostSources:List<HostSource> by lazy {
            mutableListOf(
                HostSource("Energized Basic", "https://raw.githubusercontent.com/EnergizedProtection/block/master/basic/formats/domains.txt"),
                HostSource("Energized Blu", "https://raw.githubusercontent.com/EnergizedProtection/block/master/blu/formats/domains.txt"),
                HostSource("Energized Spark", "https://raw.githubusercontent.com/EnergizedProtection/block/master/spark/formats/domains.txt"),
                HostSource("Energized Porn", "https://raw.githubusercontent.com/EnergizedProtection/block/master/porn/formats/domains.txt"),
                HostSource("Energized Ultimate", "https://raw.githubusercontent.com/EnergizedProtection/block/master/ultimate/formats/domains.txt"),
                HostSource("AdAway", "https://adaway.org/hosts.txt"),
                HostSource("StevenBlack unified", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"),
                HostSource("CoinBlockerList", "https://zerodot1.gitlab.io/CoinBlockerLists/hosts"),
                HostSource("Malewaredomainlist.com", "https://www.malwaredomainlist.com/hostslist/hosts.txt"),
                HostSource("PiHoleBlocklist Android tracking", "https://raw.githubusercontent.com/Perflyst/PiHoleBlocklist/master/android-tracking.txt"),
                HostSource("Quidsup NoTrack Tracker Blocklist", "https://gitlab.com/quidsup/notrack-blocklists/raw/master/notrack-blocklist.txt"),
                HostSource("someonewhocares.org", "https://someonewhocares.org/hosts/zero/hosts")
            ).apply {
                forEach { it.enabled = false }
            }
        }
    }
}