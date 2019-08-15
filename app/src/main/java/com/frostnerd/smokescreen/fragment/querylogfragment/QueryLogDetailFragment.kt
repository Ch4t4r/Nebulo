package com.frostnerd.smokescreen.fragment.querylogfragment

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.frostnerd.dnstunnelproxy.QueryListener
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.database.entities.DnsQuery
import kotlinx.android.synthetic.main.fragment_querylog_detail.*
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
class QueryLogDetailFragment : Fragment() {
    var currentQuery: DnsQuery? = null
        private set
    private lateinit var timeFormatSameDay: DateFormat
    private lateinit var timeFormatDifferentDay: DateFormat
    private fun formatTimeStamp(timestamp:Long): String {
        return if(isTimeStampToday(timestamp)) timeFormatSameDay.format(timestamp)
        else timeFormatDifferentDay.format(timestamp)
    }

    private fun isTimeStampToday(timestamp:Long):Boolean {
        return timestamp >= getStartOfDay()
    }

    private fun getStartOfDay():Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            resources.configuration.locales.get(0)!!
        } else{
            @Suppress("DEPRECATION")
            resources.configuration.locale!!
        }
    }

    private fun setupTimeFormat() {
        val locale = getLocale()
        timeFormatSameDay = DateFormat.getTimeInstance(DateFormat.MEDIUM, locale)
        timeFormatDifferentDay = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale)
    }
    private var viewCreated = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        setupTimeFormat()
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
            queryTime.text = formatTimeStamp(query.questionTime)
            if(query.responseTime >= query.questionTime) {
                latency.text = (query.responseTime - query.questionTime).toString() + " ms"
            } else {
                latency.text = "-"
            }
            longName.text = query.name
            type.text = query.type.name
            protocol.text = when {
                query.askedServer == null -> ""
                query.askedServer!!.startsWith("https") -> getString(R.string.fragment_querydetail_mode_doh)
                else -> getString(R.string.fragment_querydetail_mode_dot)
            }
            if(query.responseSource == QueryListener.Source.CACHE) {
                resolvedBy.text = "Cache"
            } else {
                resolvedBy.text = query.askedServer?.replace("tls::", "")?.replace("https::", "") ?: "-"
            }
        }
    }

}