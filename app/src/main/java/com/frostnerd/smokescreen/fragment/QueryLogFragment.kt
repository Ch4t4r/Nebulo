package com.frostnerd.smokescreen.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.frostnerd.cacheadapter.ModelAdapterBuilder
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.util.LiveDataSource
import kotlinx.android.synthetic.main.fragment_querylog.*

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return layoutInflater.inflate(R.layout.fragment_querylog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val live = requireContext().getDatabase().dnsQueryDao().getAllLive()
        val source = LiveDataSource(this, live, true) {
            list.smoothScrollToPosition(0)
        }
        val adapter = ModelAdapterBuilder.newBuilder(source) {
            viewBuilder = { parent, viewType ->
                layoutInflater.inflate(R.layout.item_logged_query, parent, false)
            }
            getItemCount = {
                source.currentSize()
            }
            bindModelView = { viewHolder, position, data ->
                viewHolder.itemView.findViewById<TextView>(R.id.text).text = data.shortName + " " + data.type + "(" + data.name + ")"
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
}