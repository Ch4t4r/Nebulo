package com.frostnerd.smokescreen.fragment.querylogfragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.database.entities.DnsQuery
import kotlinx.android.synthetic.main.fragment_querylog_detail.*


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
class QueryLogDetailFragment : Fragment() {
    var currentQuery: DnsQuery? = null
        private set


    private var viewCreated = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_querylog_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewCreated = true
        updateUi()
    }

    fun isShowingQuery(): Boolean {
        return currentQuery != null
    }

    fun showQuery(query: DnsQuery) {
        val wasUpdated = query != currentQuery
        currentQuery = query

        if (wasUpdated) {
            updateUi()
        }
    }

    private fun updateUi() {
        val query = currentQuery
        if(query != null && viewCreated) {
            queryTime.text = QueryLogListFragment.formatTimeStamp(query.questionTime)
            if(query.responseTime >= query.questionTime) {
                latency.text = (query.responseTime - query.questionTime).toString() + " ms"
            } else {
                latency.text = "-"
            }
            longName.text = query.name
            type.text = query.type.name
            if(query.fromCache) {
                resolvedBy.text = "Cache"
            } else {
                resolvedBy.text = query.askedServer ?: "-"
            }
        }
    }

}