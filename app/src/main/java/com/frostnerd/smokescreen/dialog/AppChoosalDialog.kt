package com.frostnerd.smokescreen.dialog

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.frostnerd.cacheadapter.ListDataSource
import com.frostnerd.cacheadapter.ModelAdapterBuilder
import com.frostnerd.lifecyclemanagement.BaseViewHolder
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.getPreferences
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.HashSet

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
class AppChoosalDialog(
    private val context: Activity,
    selectedApps: Set<String>,
    var blackList:Boolean = true,
    private val hiddenAppPackages: Set<String> = emptySet(),
    private val defaultChosenUnselectablePackages:Set<String> = emptySet(),
    val infoText: String?,
    val dataCallback: (selectedApps: MutableSet<String>, blackList:Boolean) -> Unit
) {
    private val dialogBuilder by lazy(LazyThreadSafetyMode.NONE) { AlertDialog.Builder(context, context.getPreferences().theme.dialogStyle) }
    private val layoutInflater by lazy(LazyThreadSafetyMode.NONE) { LayoutInflater.from(context) }
    private val view by lazy(LazyThreadSafetyMode.NONE){ layoutInflater.inflate(R.layout.dialog_app_choosal, null, false) }
    private val progressBar by lazy(LazyThreadSafetyMode.NONE) { view.findViewById<ProgressBar>(R.id.progress) }
    private val packageManager by lazy(LazyThreadSafetyMode.NONE) { context.packageManager }
    private val installedPackages: MutableList<ApplicationInfo> by lazy(LazyThreadSafetyMode.NONE) {
        packageManager.getInstalledApplications(PackageManager.GET_META_DATA).filter {
            !hiddenAppPackages.contains(it.packageName)
        }.sortedBy {
            labels.getOrPut(it.packageName) { it.loadLabel(packageManager).toString() }.toLowerCase(Locale.ROOT)
        }.toMutableList()
    }
    private val filteredPackets: MutableList<ApplicationInfo> = emptyList<ApplicationInfo>().toMutableList()
    private val currentSelectedApps: MutableSet<String> = HashSet(selectedApps)
    private val icons = mutableMapOf<String, Drawable>()
    private val labels = mutableMapOf<String, String>()

    private var currentFilterJob: Job? = null
    private var showSystemApps = true
    private var labelSearchTerm: String? = null
    private var filterUpdatingUi = false

    fun createDialog(): AlertDialog {
        dialogBuilder.setCancelable(true)
        dialogBuilder.setPositiveButton(android.R.string.ok) { dialog, _ ->
            dataCallback(currentSelectedApps, blackList)
            dialog.dismiss()
        }
        dialogBuilder.setNegativeButton(android.R.string.cancel) { dialog, _ ->
            dialog.dismiss()
        }
        dialogBuilder.setView(view)
        dialogBuilder.setOnCancelListener {
            icons.clear()
            labels.clear()
            filteredPackets.clear()
            installedPackages.clear()
        }
        dialogBuilder.setOnDismissListener {
            icons.clear()
            labels.clear()
            filteredPackets.clear()
            installedPackages.clear()
        }

        val dialog = dialogBuilder.create()
        dialog.setOnShowListener {
            GlobalScope.launch {
                val list = view.findViewById<RecyclerView>(R.id.list)
                filterPackages(labelSearchTerm, showSystemApps)
                val adapter = ModelAdapterBuilder.withModelAndViewHolder<ApplicationInfo, AppViewHolder> {
                    this.getItemCount = { filteredPackets.size }
                    this.runOnUiThread = { block -> context.runOnUiThread(block) }
                    this.viewBuilder = { parent, _ ->
                        layoutInflater.inflate(R.layout.item__dialog_app_choosal, parent, false)
                    }
                    this.viewHolderBuilder = { view, _ ->
                        AppViewHolder(view)
                    }
                    this.bindNonModelView = { _, _ -> throw IllegalStateException() }
                    this.bindModelView = { viewHolder, _, data ->
                        viewHolder.bindApplicationInfo(data)
                    }
                    dataSources.add(ListDataSource(filteredPackets))
                }.build()
                context.runOnUiThread {
                    list.layoutManager = LinearLayoutManager(context)
                    list.adapter = adapter
                    if (infoText.isNullOrBlank()) view.findViewById<TextView>(R.id.text).visibility = View.GONE
                    else view.findViewById<TextView>(R.id.text).text = infoText

                    val systemAppCheckbox = view.findViewById<CheckBox>(R.id.showSystemApps)
                    systemAppCheckbox.setOnCheckedChangeListener { _, isChecked ->
                        showSystemApps = isChecked
                        filterPackagesInBackground(labelSearchTerm, !isChecked, adapter)
                    }
                    systemAppCheckbox.visibility = View.VISIBLE
                    val whiteListCheckBox = view.findViewById<CheckBox>(R.id.whitelist)
                    whiteListCheckBox.isChecked = !blackList
                    whiteListCheckBox.setOnCheckedChangeListener { _, isChecked ->
                        blackList = !isChecked
                        filterPackagesInBackground(labelSearchTerm, showSystemApps, adapter)
                    }
                    whiteListCheckBox.visibility = View.VISIBLE

                    progressBar.visibility = View.GONE
                    // view.findViewById<View>(R.id.searchWrap).visibility = View.VISIBLE

                    val searchEditText = view.findViewById<EditText>(R.id.search)
                    searchEditText.addTextChangedListener(object : TextWatcher {
                        override fun afterTextChanged(s: Editable) {
                            val prev = labelSearchTerm
                            labelSearchTerm = s.toString()
                            filterPackagesInBackground(prev, showSystemApps, adapter)
                        }

                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                        }

                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        }

                    })
                    view.findViewById<View>(R.id.clearSearch).setOnClickListener {
                        if(!searchEditText.text.isNullOrBlank()) searchEditText.setText("")
                    }
                }
            }
        }
        return dialog
    }

    private fun filterPackagesInBackground(
        previousSearchTerm: String?,
        previousShowSystemApps: Boolean,
        adapter: RecyclerView.Adapter<*>
    ) {
        progressBar.visibility = View.VISIBLE
        val oldFilterJob = currentFilterJob
        currentFilterJob = GlobalScope.launch {
            oldFilterJob?.join()
            while(filterUpdatingUi) delay(1)
            filterPackages(previousSearchTerm, previousShowSystemApps)
            context.runOnUiThread {
                filterUpdatingUi = true
                adapter.notifyDataSetChanged()
                progressBar.visibility = View.GONE
                filterUpdatingUi = false
            }
        }
    }

    private fun filterPackages(previousSearchTerm: String?, previousShowSystemApps: Boolean) {
        val systemAppMask = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        filteredPackets.clear()
        filteredPackets.addAll(installedPackages)
        if (labelSearchTerm != previousSearchTerm && !labelSearchTerm.isNullOrBlank()) filteredPackets.retainAll {
            labels[it.packageName]!!.contains(labelSearchTerm!!, true)
        }
        if (previousShowSystemApps != showSystemApps && !showSystemApps) {
            filteredPackets.retainAll {
                 it.flags and systemAppMask == 0
            }
        }
    }

    inner class AppViewHolder(itemView: View) : BaseViewHolder(itemView) {
        val icon = itemView.findViewById<ImageView>(R.id.icon)
        val title = itemView.findViewById<TextView>(R.id.app_name)
        private val checkBox = itemView.findViewById<CheckBox>(R.id.checkbox)

        init {
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                val packageName = itemView.tag as String
                if(!defaultChosenUnselectablePackages.contains(packageName)) {
                    if (isChecked) currentSelectedApps.add(packageName)
                    else currentSelectedApps.remove(packageName)
                }
            }
            itemView.setOnClickListener {
                val packageName = itemView.tag as String
                if(!defaultChosenUnselectablePackages.contains(packageName)) {
                    checkBox.isChecked = !checkBox.isChecked
                }
            }
        }

        fun bindApplicationInfo(info: ApplicationInfo) {
            itemView.tag = info.packageName
            icon.setImageDrawable(icons.getOrPut(info.packageName) {
                info.loadIcon(packageManager)
            })
            title.text = labels.getOrPut(info.packageName) { info.loadLabel(packageManager).toString() }
            checkBox.isChecked = currentSelectedApps.contains(info.packageName)
            if(defaultChosenUnselectablePackages.contains(info.packageName)) {
                checkBox.isChecked = blackList
                checkBox.isEnabled = false
            } else {
                checkBox.isEnabled = true
            }
        }

        override fun destroy() {
            icon.setImageDrawable(null)
            icons.clear()
            labels.clear()
        }
    }
}