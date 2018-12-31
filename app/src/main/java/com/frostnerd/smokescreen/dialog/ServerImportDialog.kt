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
import com.frostnerd.encrypteddnstunnelproxy.HttpsDnsServerInformation
import com.frostnerd.encrypteddnstunnelproxy.RequestType
import com.frostnerd.lifecyclemanagement.BaseViewHolder
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.getPreferences

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */
class ServerImportDialog(context: Context, val servers: List<HttpsDnsServerInformation>) :
    AlertDialog(context, context.getPreferences().theme.dialogStyle) {
    private val selectedServerPositions = mutableSetOf<Int>()

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
        val queriesEncrypted = itemView.findViewById<CheckBox>(R.id.queriesEncrypted)
        val selected = itemView.findViewById<CheckBox>(R.id.checkbox)

        override fun destroy() {
        }

        fun display(context: Context, server: HttpsDnsServerInformation) {
            name.text = server.name
            capabilities.text = buildString {
                for (capability in server.capabilities) {
                    append(capability.name)
                    append(", ")
                }
            }
            if (capabilities.text.isNullOrBlank()) capabilities.visibility = View.GONE
            else capabilities.visibility = View.VISIBLE
            queriesEncrypted.isChecked = server.servers.any {
                it.requestTypes.containsKey(RequestType.WIREFORMAT_POST)
            }
            urls.text = buildString {
                for (dohServer in server.servers) {
                    append("- ")
                    append(dohServer.address.getUrl())
                    append(" (")
                    append(context.getString(R.string.dialog_serverimport_priority, dohServer.priority))
                    if (dohServer.experimental) {
                        append(", ")
                        append(context.getString(R.string.dialog_serverimport_experimental))
                    }
                    append(")")
                }
            }
        }
    }
}