package com.frostnerd.smokescreen.activity

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
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
import com.frostnerd.smokescreen.*
import com.frostnerd.smokescreen.database.entities.DnsRule
import com.frostnerd.smokescreen.database.entities.HostSource
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.dialog.DnsRuleDialog
import com.frostnerd.smokescreen.dialog.NewHostSourceDialog
import com.frostnerd.smokescreen.service.RuleImportService
import com.frostnerd.smokescreen.util.SpaceItemDecorator
import kotlinx.android.synthetic.main.activity_dns_rules.*
import kotlinx.android.synthetic.main.activity_dns_rules.toolBar
import kotlinx.android.synthetic.main.item_datasource.view.*
import kotlinx.android.synthetic.main.item_datasource.view.cardContent
import kotlinx.android.synthetic.main.item_datasource.view.delete
import kotlinx.android.synthetic.main.item_datasource.view.enable
import kotlinx.android.synthetic.main.item_datasource.view.text
import kotlinx.android.synthetic.main.item_datasource_rules.view.*
import kotlinx.android.synthetic.main.item_dnsrule_host.view.*

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
    private var importDoneReceiver:BroadcastReceiver? = null
    private lateinit var userDnsRules:MutableList<DnsRule>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dns_rules)
        setSupportActionBar(toolBar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        getDatabase().dnsRuleRepository().getUserRulesAsnc(block = {
            userDnsRules = it.toMutableList()
        }, coroutineScope = LifecycleCoroutineScope(this, ui = false))
        addSource.setOnClickListener {
            NewHostSourceDialog(this) { newSource ->
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
            }.show()
        }
        refresh.setOnClickListener {
            if(isServiceRunning(RuleImportService::class.java)) {
                startService(Intent(this, RuleImportService::class.java).putExtra("abort", true))
            } else {
                startService(Intent(this, RuleImportService::class.java))
                refreshProgress.show()
            }
        }
        sourceAdapterList = getDatabase().hostSourceDao().getAll().toMutableList()
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
                            getDatabase().dnsRuleRepository().deleteAllFromSourceAsync(it)
                            getDatabase().hostSourceRepository().deleteAsync(it)
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
                        getDatabase().dnsRuleRepository().getUserCountAsync(coroutineScope = LifecycleCoroutineScope(this, ui = false), block= {
                            userRuleCount = it.toInt()
                            runOnUiThread {
                                sourceAdapter.notifyItemRangeInserted(sourceAdapterList.size + 1, userRuleCount)
                            }
                        })
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
                        getDatabase().dnsRuleRepository().insertAsync(newRule)
                        userDnsRules.add(insertPos, newRule)
                        if(showUserRules) {
                            userRuleCount += 1
                            sourceAdapter.notifyItemInserted(sourceAdapterList.size + 1 + insertPos)
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
                        getDatabase().dnsRuleRepository().updateAsync(newRule)
                        val index = userDnsRules.indexOf(it)
                        userDnsRules[index] = newRule
                        sourceAdapter.notifyItemChanged(sourceAdapterList.size + 1 + index)
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
            bindModelView = { viewHolder, _, data ->
                (viewHolder as SourceViewHolder).display(data)
            }
            bindNonModelView = { viewHolder, position ->
                if(viewHolder is CustomRulesViewHolder) {
                    viewHolder.enabled.isChecked = getPreferences().customHostsEnabled
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
            refresh.isEnabled = false
            refreshProgress.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                refreshProgress.show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        importDoneReceiver = registerLocalReceiver(IntentFilter(RuleImportService.BROADCAST_IMPORT_DONE)) {
            refresh.isEnabled = true
            refreshProgress.hide()
        }
        if(!isServiceRunning(RuleImportService::class.java) && !refresh.isEnabled) {
            refresh.isEnabled = true
            refreshProgress.hide()
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterLocalReceiver(importDoneReceiver)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_dnsrule, menu)
        menu?.getItem(0)?.actionView?.findViewById<Switch>(R.id.actionbarSwitch)?.setOnCheckedChangeListener { _, isChecked ->
            getPreferences().dnsRulesEnabled = isChecked
        }
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
        private var elementsShown = false

        init {
            enabled.setOnCheckedChangeListener { _, isChecked ->
                changeSourceStatus(isChecked)
            }
            clear.setOnClickListener {
                clearRules()
            }
            openList.setOnClickListener {
                if(elementsShown) {
                    openList.animate().rotationBy(-180f).setDuration(350).start()
                } else {
                    openList.animate().rotationBy(180f).setDuration(350).start()
                }
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

        override fun destroy() {}
    }

    private class CustomRuleHostViewHolder(view:View,
                                           deleteRule:(DnsRule) -> Unit,
                                           editRule:(DnsRule) -> Unit):BaseViewHolder(view) {
        val text = view.text
        val delete = view.delete
        val cardContent = view.cardContent
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
        }


        override fun destroy() {}

    }
}