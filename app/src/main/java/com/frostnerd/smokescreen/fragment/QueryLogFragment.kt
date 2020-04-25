package com.frostnerd.smokescreen.fragment

import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import com.frostnerd.smokescreen.BackpressFragment
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.database.entities.DnsQuery
import com.frostnerd.smokescreen.fragment.querylogfragment.QueryLogDetailFragment
import com.frostnerd.smokescreen.fragment.querylogfragment.QueryLogListFragment
import kotlinx.android.synthetic.main.fragment_querylog_main.*

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
class QueryLogFragment : Fragment(), BackpressFragment {
    lateinit var listFragment: QueryLogListFragment
    lateinit var detailFragment: QueryLogDetailFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_querylog_main, container, false)
    }

    override fun onBackPressed(): Boolean {
        return if(viewpager != null && viewpager.currentItem == 1) {
            viewpager.currentItem = 0
            true
        } else false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        listFragment = QueryLogListFragment()
        detailFragment = QueryLogDetailFragment()

        viewpager.adapter = createViewAdapter()
        tabLayout.setupWithViewPager(viewpager)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_queryloglist, menu)
        val searchManager = requireContext().getSystemService(Context.SEARCH_SERVICE) as SearchManager
        val searchView: SearchView = menu.findItem(R.id.search)!!.actionView as SearchView
        searchView.setSearchableInfo(searchManager.getSearchableInfo(requireActivity().componentName))
        searchView.setOnQueryTextListener(listFragment)
    }

    fun displayQueryDetailed(query:DnsQuery, switchToDetailView:Boolean = true) {
        val hadQuery = detailFragment.isShowingQuery()
        detailFragment.showQuery(query)

        if(!hadQuery) viewpager.adapter?.notifyDataSetChanged()
        else {
            val tab = tabLayout.getTabAt(1)
            tab?.text = query.shortName
        }
        if(switchToDetailView) viewpager.currentItem = 1
    }

    private fun createViewAdapter(): FragmentPagerAdapter {
        return object : FragmentPagerAdapter(childFragmentManager) {
            override fun getItem(position: Int): Fragment {
                return when (position) {
                    0 -> listFragment
                    else -> detailFragment
                }
            }

            override fun getCount(): Int {
                return if (detailFragment.isShowingQuery()) 2 else 1
            }

            override fun getPageTitle(position: Int): CharSequence? {
                return when (position) {
                    0 -> getString(R.string.menu_querylogging)
                    else -> detailFragment.currentQuery?.shortName
                }
            }

        }
    }
}