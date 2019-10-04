package com.frostnerd.smokescreen.fragment.querylogfragment

import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.frostnerd.cacheadapter.DefaultViewHolder
import com.frostnerd.cacheadapter.ModelAdapterBuilder
import com.frostnerd.dnstunnelproxy.QueryListener
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.database.entities.DnsQuery
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.fragment.QueryLogFragment
import com.frostnerd.smokescreen.util.LiveDataSource
import kotlinx.android.synthetic.main.fragment_querylog_list.*
import kotlinx.android.synthetic.main.item_logged_query.view.*

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
class QueryLogListFragment: Fragment(), SearchView.OnQueryTextListener {
    private lateinit var unfilteredAdapter:RecyclerView.Adapter<*>
    private var currentSearchText:String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return layoutInflater.inflate(R.layout.fragment_querylog_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val live = requireContext().getDatabase().dnsQueryDao().getAllLive()
        val source = LiveDataSource(this, live, true)
        unfilteredAdapter = createAdapter(source)

        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = unfilteredAdapter
    }

    private fun createAdapter(source:LiveDataSource<DnsQuery>): RecyclerView.Adapter<DefaultViewHolder> {
        return ModelAdapterBuilder.newBuilder(source) {
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
                viewHolder.itemView.typeImage.setImageResource(when(data.responseSource) {
                    QueryListener.Source.UPSTREAM -> R.drawable.ic_reply
                    QueryListener.Source.CACHE, QueryListener.Source.CACHE_AND_LOCALRESOLVER -> R.drawable.ic_database
                    QueryListener.Source.LOCALRESOLVER -> R.drawable.ic_flag
                    else -> R.drawable.ic_query_question
                })
                if(isDisplayingQuery(data)) displayQuery(data, false)
            }
            bindNonModelView = { viewHolder, position ->

            }
            runOnUiThread = {
                requireActivity().runOnUiThread(it)
            }
        }.build()
    }

    private fun displayQuery(dnsQuery: DnsQuery, switchToDetailView:Boolean = true) {
        (parentFragment as QueryLogFragment).displayQueryDetailed(dnsQuery, switchToDetailView)
    }

    private fun isDisplayingQuery(dnsQuery: DnsQuery):Boolean {
        val parent = parentFragment as QueryLogFragment
        if(!parent.detailFragment.isShowingQuery()) return false
        return parent.detailFragment.currentQuery?.id == dnsQuery.id
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_queryloglist, menu)
        val searchManager = context!!.getSystemService(Context.SEARCH_SERVICE) as SearchManager
        val searchView:SearchView = menu.findItem(R.id.search)!!.actionView as SearchView
        searchView.setSearchableInfo(searchManager.getSearchableInfo(activity!!.componentName))
        searchView.setOnQueryTextListener(this)
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        if(!query.isNullOrBlank() && query != currentSearchText) {
            val live = requireContext().getDatabase().dnsQueryDao().getAllWithHostLive(query)
            val source = LiveDataSource(this, live, true)
            list.adapter = createAdapter(source)
            currentSearchText = query
        } else if(query.isNullOrBlank() && !currentSearchText.isNullOrBlank()) {
            list.adapter = unfilteredAdapter
            currentSearchText = null
        }
        return true
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        if(newText.isNullOrBlank() && !currentSearchText.isNullOrBlank()) onQueryTextSubmit(newText)
        return false
    }
}