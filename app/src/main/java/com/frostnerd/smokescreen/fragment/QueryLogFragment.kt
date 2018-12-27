package com.frostnerd.smokescreen.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.database.entities.DnsQuery
import com.frostnerd.smokescreen.fragment.querylogfragment.QueryLogDetailFragment
import com.frostnerd.smokescreen.fragment.querylogfragment.QueryLogListFragment
import kotlinx.android.synthetic.main.fragment_querylog_main.*

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */
class QueryLogFragment : Fragment() {
    lateinit var listFragment: QueryLogListFragment
    lateinit var detailFragment: QueryLogDetailFragment

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_querylog_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        listFragment = QueryLogListFragment()
        detailFragment = QueryLogDetailFragment()

        viewpager.adapter = createViewAdapter()
        tabLayout.setupWithViewPager(viewpager)
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