package com.frostnerd.smokescreen.fragment.querylogfragment

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.frostnerd.cacheadapter.ModelAdapterBuilder
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.database.entities.DnsQuery
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.fragment.QueryLogFragment
import com.frostnerd.smokescreen.util.LiveDataSource
import kotlinx.android.synthetic.main.fragment_querylog_list.*
import java.text.DateFormat
import java.util.*

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
class QueryLogListFragment: Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return layoutInflater.inflate(R.layout.fragment_querylog_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val live = requireContext().getDatabase().dnsQueryDao().getAllLive()
        val source = LiveDataSource(this, live, true)
        val adapter = ModelAdapterBuilder.newBuilder(source) {
            viewBuilder = { parent, viewType ->
                val createdView = layoutInflater.inflate(R.layout.item_logged_query, parent, false)
                createdView.setOnClickListener {
                    displayQuery(it.tag as DnsQuery)
                }
                createdView
            }
            getItemCount = {
                source.currentSize()
            }
            bindModelView = { viewHolder, position, data ->
                viewHolder.itemView.findViewById<TextView>(R.id.text).text = data.shortName
                viewHolder.itemView.tag = data
                if(isDisplayingQuery(data)) displayQuery(data, false)
            }
            bindNonModelView = { viewHolder, position ->

            }
            runOnUiThread = {
                requireActivity().runOnUiThread(it)
            }
        }.build()

        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter
    }

    private fun displayQuery(dnsQuery: DnsQuery, switchToDetailView:Boolean = true) {
        (parentFragment as QueryLogFragment).displayQueryDetailed(dnsQuery, switchToDetailView)
    }

    private fun isDisplayingQuery(dnsQuery: DnsQuery):Boolean {
        val parent = parentFragment as QueryLogFragment
        if(!parent.detailFragment.isShowingQuery()) return false
        return parent.detailFragment.currentQuery?.id == dnsQuery.id
    }
}