package com.frostnerd.smokescreen.fragment.querylogfragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.frostnerd.cacheadapter.DefaultViewHolder
import com.frostnerd.cacheadapter.ModelAdapterBuilder
import com.frostnerd.dnstunnelproxy.QueryListener
import com.frostnerd.lifecyclemanagement.launchWithLifecycle
import com.frostnerd.lifecyclemanagement.launchWithLifecycleUi
import com.frostnerd.smokescreen.BackpressFragment
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.database.entities.DnsQuery
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.dialog.QueryLogFilterDialog
import com.frostnerd.smokescreen.fragment.QueryLogFragment
import com.frostnerd.smokescreen.util.LiveDataSource
import kotlinx.android.synthetic.main.fragment_querylog_list.*
import kotlinx.android.synthetic.main.fragment_querylog_list.view.*
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
class QueryLogListFragment: Fragment(), SearchView.OnQueryTextListener, BackpressFragment,
    SearchView.OnCloseListener {
    private lateinit var unfilteredAdapter:RecyclerView.Adapter<*>
    private var currentSearchText:String? = null
    private var filterConfig = QueryLogFilterDialog.FilterConfig.showAllConfig
    private var shortenDomains:Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return layoutInflater.inflate(R.layout.fragment_querylog_list, container, false)
    }

    override fun onBackPressed(): Boolean {
        return if(filterConfig != QueryLogFilterDialog.FilterConfig.showAllConfig) {
            clearFilter()
            updateAdapter()
            true
        } else false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launchWithLifecycle {
            val live = requireContext().getDatabase().dnsQueryDao().getAllLive()
            unfilteredAdapter = createAdapter(LiveDataSource(this@QueryLogListFragment, live, true))

            launchWithLifecycleUi {
                list.layoutManager = LinearLayoutManager(requireContext())
                list.adapter = unfilteredAdapter
                progress.visibility = View.GONE
            }
        }
        view.filter.setOnClickListener {
            QueryLogFilterDialog(requireContext(), filterConfig, shortenDomains) { config, shortenDomain ->
                val filterChanged = config != filterConfig
                val shortenDomainsChanged = shortenDomains != shortenDomain
                filterConfig = config
                shortenDomains = shortenDomain
                if(filterChanged) updateAdapter()
                else if(shortenDomainsChanged) list.adapter?.notifyDataSetChanged()
            }.show()
        }
    }

    private fun createAdapter(forSource:LiveDataSource<DnsQuery>): RecyclerView.Adapter<DefaultViewHolder> {
        return ModelAdapterBuilder.newBuilder(forSource) {
            viewBuilder = { parent, _ ->
                val createdView = layoutInflater.inflate(R.layout.item_logged_query, parent, false)
                createdView.setOnClickListener {
                    displayQuery(it.tag as DnsQuery)
                }
                createdView
            }
            getItemCount = {
                forSource.currentSize()
            }
            bindModelView = { viewHolder, _, data ->
                viewHolder.itemView.findViewById<TextView>(R.id.text).text = if(shortenDomains) data.shortName else data.name
                viewHolder.itemView.tag = data
                viewHolder.itemView.typeImage.setImageResource(
                    when (data.responseSource) {
                        QueryListener.Source.UPSTREAM ->
                            if (data.isHostBlockedByDnsServer) R.drawable.ic_flag
                            else R.drawable.ic_reply
                        QueryListener.Source.CACHE, QueryListener.Source.CACHE_AND_LOCALRESOLVER -> R.drawable.ic_database
                        QueryListener.Source.LOCALRESOLVER -> R.drawable.ic_flag
                        else -> R.drawable.ic_query_question
                    }
                )
                if (isDisplayingQuery(data)) displayQuery(data, false)
            }
            bindNonModelView = { _, _ ->

            }
            runOnUiThread = {
                requireActivity().runOnUiThread(it)
            }
        }.build()
    }

    private fun createUpdatedAdapter():RecyclerView.Adapter<*> {
        return if(currentSearchText.isNullOrBlank() && filterConfig.showAll) {
            unfilteredAdapter
        } else {
            val liveData = when {
                currentSearchText.isNullOrBlank() -> requireContext().getDatabase().dnsQueryRepository().getAllWithFilterLive(filterConfig)
                filterConfig.showAll -> requireContext().getDatabase().dnsQueryDao().getAllWithHostLive(currentSearchText!!)
                else -> requireContext().getDatabase().dnsQueryRepository().getAllWithHostAndFilterLive(currentSearchText!!, filterConfig)
            }
            val source = LiveDataSource(this, liveData, true)
            createAdapter(source)
        }
    }

    private fun updateAdapter() {
        launchWithLifecycleUi {
            view?.progress?.visibility = View.VISIBLE
            launchWithLifecycle {
                val updatedAdapter = createUpdatedAdapter()
                launchWithLifecycleUi {
                    view?.progress?.visibility = View.GONE
                    list.adapter = updatedAdapter
                }
            }
        }
    }

    private fun displayQuery(dnsQuery: DnsQuery, switchToDetailView:Boolean = true) {
        (parentFragment as QueryLogFragment).displayQueryDetailed(dnsQuery, switchToDetailView)
    }

    private fun isDisplayingQuery(dnsQuery: DnsQuery):Boolean {
        val parent = parentFragment as QueryLogFragment
        if(!parent.detailFragment.isShowingQuery()) return false
        return parent.detailFragment.currentQuery?.id == dnsQuery.id
    }

    private fun clearFilter() {
        filterConfig = QueryLogFilterDialog.FilterConfig.showAllConfig
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        if(!query.isNullOrBlank() && query.trim() != currentSearchText) {
            currentSearchText = query.trim()
            updateAdapter()
        } else if(query.isNullOrBlank() && !currentSearchText.isNullOrBlank()) {
            currentSearchText = null
            updateAdapter()
        }
        return true
    }

    override fun onClose(): Boolean {
        clearFilter()
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        if(newText.isNullOrBlank() && !currentSearchText.isNullOrBlank()) onQueryTextSubmit(newText)
        return false
    }
}