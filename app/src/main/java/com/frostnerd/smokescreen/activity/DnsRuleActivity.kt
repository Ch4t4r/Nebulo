package com.frostnerd.smokescreen.activity

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.frostnerd.cacheadapter.ListDataSource
import com.frostnerd.cacheadapter.ModelAdapterBuilder
import com.frostnerd.lifecyclemanagement.BaseActivity
import com.frostnerd.lifecyclemanagement.BaseViewHolder
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.database.entities.HostSource
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.getPreferences
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
    private var cnt = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dns_rules)
        setSupportActionBar(toolBar)
        addSource.setOnClickListener {
            val newSource = HostSource("HerpDerp" + cnt++, "https://test.frostnerd.com/" + cnt)
            if (!sourceAdapterList.contains(newSource)) {
                val insertPos = sourceAdapterList.indexOfFirst {
                    it.name > newSource.name
                }.let {
                    when (it) {
                        0 -> 0
                        -1 -> sourceAdapterList.size - 1
                        else -> it
                    }
                }
                sourceAdapterList.add(insertPos, newSource)
                sourceAdapter.notifyItemInserted(insertPos)
                getDatabase().hostSourceDao().insert(newSource)
            }
        }
        sourceAdapterList = getDatabase().hostSourceDao().getAll().toMutableList()
        adapterDataSource = ListDataSource(sourceAdapterList)
        sourceAdapter = ModelAdapterBuilder.withModelAndViewHolder({ view, type ->
            if (type == 0) {
                SourceViewHolder(view, deleteSource = {
                    val pos = sourceAdapterList.indexOf(it)
                    sourceAdapterList.removeAt(pos)
                    sourceAdapter.notifyItemRemoved(pos)
                    getDatabase().hostSourceDao().delete(it)
                }, changeSourceStatus = { hostSource, enabled ->
                    hostSource.enabled = enabled
                    getDatabase().hostSourceDao().update(hostSource)
                })
            } else {
                CustomRulesViewHolder(view, changeSourceStatus = {
                    getPreferences().customHostsEnabled = it
                }, clearRules = {
                    getDatabase().dnsRuleRepository().deleteAllAsync()
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