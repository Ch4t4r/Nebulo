package com.frostnerd.smokescreen.fragment.querylogfragment

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.database.entities.DnsQuery
import kotlinx.android.synthetic.main.fragment_querylog_detail.*
import java.text.DateFormat
import java.util.*


/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 * <p>
 * development@frostnerd.com
 */
class QueryLogDetailFragment : Fragment() {
    var currentQuery: DnsQuery? = null
        private set
    private lateinit var timeFormatSameDay:DateFormat
    private lateinit var timeFormatDifferentDay:DateFormat
    private var viewCreated = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_querylog_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewCreated = true
        setupTimeFormat()
        updateUi()
    }

    private fun setupTimeFormat() {
        val locale = getLocale()
        timeFormatSameDay = DateFormat.getTimeInstance(DateFormat.MEDIUM, locale)
        timeFormatDifferentDay = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale)
    }

    private fun getLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            resources.configuration.locales.get(0)!!
        } else{
            @Suppress("DEPRECATION")
            resources.configuration.locale!!
        }
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
            if(isTimeStampToday(query.questionTime)) {
                queryTime.text = timeFormatSameDay.format(query.questionTime)
            } else {
                queryTime.text = timeFormatDifferentDay.format(query.questionTime)
            }

            if(query.responseTime >= query.questionTime) {
                latency.text = (query.responseTime - query.questionTime).toString() + " ms"
            } else {
                latency.text = "-"
            }
            longName.text = query.name
            type.text = query.type.name
            askedServer.text = query.askedServer ?: "-"
        }
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
}