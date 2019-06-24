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
import com.frostnerd.smokescreen.*
import com.frostnerd.smokescreen.database.entities.HostSource
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.dialog.NewHostSourceDialog
import com.frostnerd.smokescreen.service.RuleImportService
import com.frostnerd.smokescreen.util.SpaceItemDecorator
import kotlinx.android.synthetic.main.activity_dns_rules.*
import kotlinx.android.synthetic.main.activity_dns_rules.toolBar
import kotlinx.android.synthetic.main.item_datasource.view.*

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
    private var cnt = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dns_rules)
        setSupportActionBar(toolBar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        addSource.setOnClickListener {
            NewHostSourceDialog(this) {
                if (!sourceAdapterList.contains(it)) {
                    val insertPos = sourceAdapterList.indexOfFirst {
                        it.name > it.name
                    }.let {
                        when (it) {
                            0 -> 0
                            -1 -> sourceAdapterList.size
                            else -> it
                        }
                    }
                    sourceAdapterList.add(insertPos, it)
                    sourceAdapter.notifyItemInserted(insertPos)
                    getDatabase().hostSourceDao().insert(it)
                }
            }.show()
        }
        refresh.setOnClickListener {
            startService(Intent(this, RuleImportService::class.java))
            it.isEnabled = false
            refreshProgress.show()
        }
        sourceAdapterList = getDatabase().hostSourceDao().getAll().toMutableList()
        adapterDataSource = ListDataSource(sourceAdapterList)
        sourceAdapter = ModelAdapterBuilder.withModelAndViewHolder({ view, type ->
            if (type == 0) {
                SourceViewHolder(view, deleteSource = {
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
            } else {
                CustomRulesViewHolder(view, changeSourceStatus = {
                    getPreferences().customHostsEnabled = it
                }, clearRules = {
                    showInfoTextDialog(this,
                        getString(R.string.dialog_clearuserrules_title),
                        getString(R.string.dialog_clearuserrules_message),
                        getString(R.string.all_yes) to { dialog, _ ->
                            getDatabase().dnsRuleRepository().deleteAllAsync()
                            dialog.dismiss()
                        }, getString(R.string.all_no) to { dialog, _ ->
                            dialog.dismiss()
                        }, null)
                })
            }
        }, adapterDataSource) {
            viewBuilder = { parent, type ->
                layoutInflater.inflate(
                    if (type == 0) R.layout.item_datasource else R.layout.item_datasource_rules,
                    parent,
                    false
                )
            }
            getItemCount = {
                sourceAdapterList.size + 1
            }
            bindModelView = { viewHolder, _, data ->
                (viewHolder as SourceViewHolder).display(data)
            }
            bindNonModelView = { viewHolder, position ->
                (viewHolder as CustomRulesViewHolder).apply {
                    this.enabled.isChecked = getPreferences().customHostsEnabled
                    this.clear.setOnClickListener {

                    }
                }
            }
            getViewType = { position ->
                if (position == getItemCount() - 1) 1
                else 0
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

    private class CustomRulesViewHolder(view: View, changeSourceStatus: (Boolean) -> Unit, clearRules: () -> Unit) :
        BaseViewHolder(view) {
        val clear = view.delete
        val enabled = view.enable

        init {
            enabled.setOnCheckedChangeListener { _, isChecked ->
                changeSourceStatus(isChecked)
            }
            clear.setOnClickListener {
                clearRules()
            }
            view.cardContent.setOnClickListener {
                enabled.isChecked = !enabled.isChecked
            }
        }

        override fun destroy() {}
    }
}