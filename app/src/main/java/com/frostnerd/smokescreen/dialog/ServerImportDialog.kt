package com.frostnerd.smokescreen.dialog

import android.content.Context
import android.content.DialogInterface
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.frostnerd.cacheadapter.AdapterBuilder
import com.frostnerd.dnstunnelproxy.DnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.HttpsUpstreamAddress
import com.frostnerd.encrypteddnstunnelproxy.RequestType
import com.frostnerd.lifecyclemanagement.BaseViewHolder
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.hasTlsServer

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
class ServerImportDialog(context: Context, loadedServers: List<DnsServerInformation<*>>) :
    AlertDialog(context, context.getPreferences().theme.dialogStyle) {
    private val selectedServerPositions = mutableSetOf<Int>()
    val servers = loadedServers.sortedByDescending {
        (it is HttpsDnsServerInformation)
    }.sortedBy {
        it.name
    }

    init {
        setTitle(context.getString(R.string.dialog_serverimport_title, servers.size))
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(R.string.cancel)) { dialog, _ ->
            dialog.dismiss()
        }
        setButton(
            DialogInterface.BUTTON_POSITIVE,
            context.getString(R.string.dialog_serverimport_done)
        ) { dialog, _ ->
            doImport()
            dialog.dismiss()
        }

        val view = layoutInflater.inflate(R.layout.dialog_server_import, null, false)
        setView(view)
        view.findViewById<TextView>(R.id.message).text = context.getString(R.string.dialog_serverimport_text)
        val serverList = view.findViewById<RecyclerView>(R.id.serverList)

        serverList.layoutManager = LinearLayoutManager(context)
        val adapter = AdapterBuilder.withViewHolder({ itemView -> ServerViewHolder(itemView) }) {
            getItemCount = {
                servers.count()
            }
            viewBuilder = { parent, _ ->
                val itemView = layoutInflater.inflate(R.layout.item_imported_server, parent, false)
                val checkbox = itemView.findViewById<CheckBox>(R.id.checkbox)
                itemView.setOnClickListener {
                    checkbox.toggle()
                }
                checkbox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedServerPositions.add(itemView.tag as Int)
                    else selectedServerPositions.remove(itemView.tag as Int)
                    getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = !selectedServerPositions.isEmpty()
                }
                itemView
            }
            bindView = { viewHolder, position ->
                viewHolder.itemView.tag = position
                val server = servers[position]
                viewHolder.display(context, server)
                viewHolder.selected.isChecked = selectedServerPositions.contains(position)
            }
        }
        serverList.adapter = adapter.build()
    }

    private fun doImport() {
        val prefs = context.getPreferences()
        prefs.edit {
            val servers = servers.filterIndexed { index, item ->
                selectedServerPositions.contains(index)
            }
            prefs.addUserServerConfiguration(servers)
        }
    }

    class ServerViewHolder(itemView: View) : BaseViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.name)
        val urls = itemView.findViewById<TextView>(R.id.urls)
        val capabilities = itemView.findViewById<TextView>(R.id.capabilities)
        val selected = itemView.findViewById<CheckBox>(R.id.checkbox)
        val serverType = itemView.findViewById<TextView>(R.id.serverType)

        override fun destroy() {
        }

        fun display(context: Context, server: DnsServerInformation<*>) {
            name.text = server.name
            capabilities.text = buildString {
                for (capability in server.capabilities) {
                    append(capability.name)
                    append(", ")
                }
            }
            if (capabilities.text.isNullOrBlank()) capabilities.visibility = View.GONE
            else capabilities.visibility = View.VISIBLE
            urls.text = buildString {
                for (serverConfiguration in server.servers) {
                    append("- ")
                    val address = serverConfiguration.address
                    if(address is HttpsUpstreamAddress) {
                        append(address.getUrl())
                    } else {
                        append(address.host)
                    }
                    append(" (")
                    append(context.getString(R.string.dialog_serverimport_priority, serverConfiguration.priority))
                    if (serverConfiguration.experimental) {
                        append(", ")
                        append(context.getString(R.string.dialog_serverimport_experimental))
                    }
                    append(")")
                }
            }
            if(server.hasTlsServer()) {
                serverType.setText(R.string.dialog_serverimport_servertype_dot)
            } else serverType.setText(R.string.dialog_serverimport_servertype_doh)
        }
    }
}