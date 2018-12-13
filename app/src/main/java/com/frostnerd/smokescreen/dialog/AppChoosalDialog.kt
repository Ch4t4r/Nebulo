package com.frostnerd.smokescreen.dialog

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.frostnerd.cacheadapter.ListDataSource
import com.frostnerd.cacheadapter.ModelAdapterBuilder
import com.frostnerd.lifecyclemanagement.BaseViewHolder
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.getPreferences
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.IllegalStateException

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */
class AppChoosalDialog(
    private val context: Activity,
    selectedApps: Set<String>,
    private val hiddenAppPackages:Set<String> = emptySet(),
    val dataCallback: (selectedApps: MutableSet<String>) -> Unit
) {
    private val dialogBuilder by lazy { AlertDialog.Builder(context, context.getPreferences().theme.dialogStyle) }
    private val layoutInflater by lazy { LayoutInflater.from(context) }
    private val view by lazy { layoutInflater.inflate(R.layout.dialog_app_choosal, null, false) }
    private val packageManager by lazy { context.packageManager }
    private val installedPackages:MutableList<ApplicationInfo> by lazy {
        packageManager.getInstalledApplications(PackageManager.GET_META_DATA).filter {
            !hiddenAppPackages.contains(it.packageName)
        }.sortedBy {
            labels.getOrPut(it.packageName) { it.loadLabel(packageManager).toString() }.toLowerCase()
        }.toMutableList()
    }
    private val currentSelectedApps: MutableSet<String> = HashSet(selectedApps)
    private val icons = mutableMapOf<String, Drawable>()
    private val labels = mutableMapOf<String, String>()

    fun createDialog(): AlertDialog {
        dialogBuilder.setCancelable(true)
        dialogBuilder.setPositiveButton(R.string.ok) { dialog, _ ->
            dataCallback(currentSelectedApps)
            dialog.dismiss()
        }
        dialogBuilder.setNegativeButton(R.string.cancel) { dialog, _ ->
            dialog.dismiss()
        }
        dialogBuilder.setView(view)
        dialogBuilder.setOnCancelListener {
            icons.clear()
            labels.clear()
            installedPackages.clear()
        }
        dialogBuilder.setOnDismissListener {
            icons.clear()
            labels.clear()
            installedPackages.clear()
        }

        val dialog = dialogBuilder.create()
        dialog.setOnShowListener {
            GlobalScope.launch {
                val list = view.findViewById<RecyclerView>(R.id.list)
                val progressBar = view.findViewById<ProgressBar>(R.id.progress)
                val adapter = ModelAdapterBuilder.withModelAndViewHolder<ApplicationInfo, AppViewHolder> {
                    this.getItemCount = { installedPackages.size }
                    this.runOnUiThread = { block -> context.runOnUiThread(block) }
                    this.viewBuilder = { parent, _ ->
                        layoutInflater.inflate(R.layout.item__dialog_app_choosal, parent, false)
                    }
                    this.viewHolderBuilder = {view ->
                        AppViewHolder(view)
                    }
                    this.bindNonModelView = { _, _ -> throw IllegalStateException() }
                    this.bindModelView = { viewHolder, _, data ->
                        viewHolder.bindApplicationInfo(data)
                    }
                    dataSources.add(ListDataSource(installedPackages))
                }.build()
                context.runOnUiThread {
                    list.layoutManager = LinearLayoutManager(context)
                    list.adapter = adapter
                    progressBar.visibility = View.GONE
                }
            }
        }
        return dialog
    }

    inner class AppViewHolder(itemView: View) : BaseViewHolder(itemView) {
        val icon = itemView.findViewById<ImageView>(R.id.icon)
        val title = itemView.findViewById<TextView>(R.id.app_name)
        val checkBox = itemView.findViewById<CheckBox>(R.id.checkbox)

        init {
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) currentSelectedApps.add(itemView.tag as String)
                else currentSelectedApps.remove(itemView.tag as String)
            }
            itemView.setOnClickListener {
                checkBox.isChecked = !checkBox.isChecked
            }
        }

        fun bindApplicationInfo(info: ApplicationInfo) {
            itemView.tag = info.packageName
            icon.setImageDrawable(icons.getOrPut(info.packageName) {
                info.loadIcon(packageManager)
            })
            title.text = labels.getOrPut(info.packageName) { info.loadLabel(packageManager).toString() }
            checkBox.isChecked = currentSelectedApps.contains(info.packageName)
        }

        override fun destroy() {
            icon.setImageDrawable(null)
            icons.clear()
            labels.clear()
        }
    }
}