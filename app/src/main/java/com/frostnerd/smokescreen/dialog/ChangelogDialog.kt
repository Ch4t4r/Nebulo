package com.frostnerd.smokescreen.dialog

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.frostnerd.cacheadapter.AdapterBuilder
import com.frostnerd.lifecyclemanagement.BaseViewHolder
import com.frostnerd.smokescreen.BuildConfig
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.getPreferences
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

class ChangelogDialog(context: Context, versionToStartFrom:Int, val showOptOut:Boolean, val showInfoText:Boolean = true):AlertDialog(context, context.getPreferences().theme.dialogStyle) {
    companion object {
        fun showNewVersionChangelog(context: Context) {
            val previousVersion = context.getPreferences().previousInstalledVersion
            if(previousVersion != BuildConfig.VERSION_CODE) {
                context.getPreferences().previousInstalledVersion = BuildConfig.VERSION_CODE
                if(context.getPreferences().showChangelog) {
                    ChangelogDialog(context, previousVersion+1, true).show()
                }
            }
        }
    }
    private val changes = TreeMap<Int, List<String>>()

    init {
        val resources = context.resources
        var misses = 0
        for(versionCode in versionToStartFrom..Integer.MAX_VALUE) {
            val identifier = resources.getIdentifier("changelog_build_$versionCode", "array", context.packageName)
            if(identifier != 0) {
                val changelog = resources.getStringArray(identifier)
                changes[versionCode] = changelog.toList()
            } else {
                misses++
                if(misses > 5) break
            }
        }
        setOnShowListener {
            if(changes.isEmpty()) dismiss()
        }
        if(!changes.isEmpty()) {
            val view = layoutInflater.inflate(R.layout.dialog_changelog, null, false)
            setView(view)
            setTitle(R.string.dialog_changelog_title)
            setButton(
                DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.all_close)
            ) { _, _ -> }

            val versions = changes.descendingKeySet().toList()
            val dontShowAgain = view.findViewById<CheckBox>(R.id.dontShowAgain)
            if(!showOptOut) {
                dontShowAgain.visibility = View.GONE
            }
            if(!showInfoText) view.findViewById<TextView>(R.id.changelogInfoText).visibility = View.GONE
            val adapter = AdapterBuilder.withViewHolder<ChangelogViewHolder> {
                this.viewBuilder = { parent, _ ->
                    layoutInflater.inflate(R.layout.item__dialog_changelog, parent, false)
                }
                this.viewHolderBuilder = { view, _ ->
                    ChangelogViewHolder(view)
                }
                this.getItemCount = {
                    changes.size
                }
                this.bindView = { viewHolder, position ->
                    val changeLog = changes[versions[position]]!!
                    viewHolder.versionName.text = changeLog[0]
                    viewHolder.versionChangelog.text = buildString {
                        for(index in 1 until changeLog.size) {
                            append("● ${changeLog[index]}\n")
                        }
                    }
                }
            }.build()
            val list = view.findViewById<RecyclerView>(R.id.list)
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = adapter
            setOnDismissListener {
                if(dontShowAgain.isChecked) {
                    context.getPreferences().showChangelog = false
                }
            }
        }
    }

    private class ChangelogViewHolder(itemView: View): BaseViewHolder(itemView) {
        val versionName = itemView.findViewById<TextView>(R.id.versionName)
        val versionChangelog = itemView.findViewById<TextView>(R.id.versionChangelog)

        override fun destroy() {}
    }
}