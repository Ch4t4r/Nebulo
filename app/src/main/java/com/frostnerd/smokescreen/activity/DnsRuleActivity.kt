package com.frostnerd.smokescreen.activity

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.frostnerd.cacheadapter.ListDataSource
import com.frostnerd.cacheadapter.ModelAdapterBuilder
import com.frostnerd.lifecyclemanagement.BaseActivity
import com.frostnerd.lifecyclemanagement.BaseViewHolder
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.util.rules.HostSource
import kotlinx.android.synthetic.main.activity_dns_rules.*

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
    private lateinit var sourceAdapter:RecyclerView.Adapter<*>
    private lateinit var sourceAdapterList:List<HostSource>
    private lateinit var adapterDataSource:ListDataSource<HostSource>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dns_rules)
        addSource.setOnClickListener {

        }
        sourceAdapterList = getPreferences().hostSources.sortedBy {
            it.name
        }
        adapterDataSource = ListDataSource(sourceAdapterList)
        sourceAdapter = ModelAdapterBuilder.withModelAndViewHolder ({ view ->
            SourceViewHolder(
                view
            )
        }, adapterDataSource) {


        }.build()
    }

    override fun getConfiguration(): Configuration {
        return Configuration.withDefaults()
    }


    private class SourceViewHolder(view: View):BaseViewHolder(view) {
        override fun destroy() {}
    }
}