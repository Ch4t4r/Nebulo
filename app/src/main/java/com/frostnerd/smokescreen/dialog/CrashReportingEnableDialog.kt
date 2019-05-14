package com.frostnerd.smokescreen.dialog

import android.content.Context
import android.content.DialogInterface
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import com.frostnerd.lifecyclemanagement.BaseDialog
import com.frostnerd.smokescreen.*
import io.sentry.Sentry
import kotlinx.android.synthetic.main.dialog_crashreportingusages.view.*
import kotlinx.android.synthetic.main.dialog_crashreportingusages_listgroup.view.*

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
class CrashReportingEnableDialog(
    context: Context, showTesterText: Boolean = BuildConfig.VERSION_NAME.let {
        it.contains("alpha", true) || it.contains("beta", true)
    },
    onConsentGiven: (() -> Unit)? = null
) : BaseDialog(context, context.getPreferences().theme.dialogStyle) {

    init {
        val view = layoutInflater.inflate(R.layout.dialog_crashreporting, null, false)
        setTitle(R.string.dialog_crashreporting_title)
        setMessage(context.getString(if (showTesterText) R.string.dialog_crashreporting_message else R.string.dialog_crashreporting_message_notester))
        setView(view)
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        setButton(
            DialogInterface.BUTTON_POSITIVE,
            context.getString(R.string.dialog_crashreporting_positive)
        ) { dialog, _ ->
            context.getPreferences().crashReportingEnabled = true
            context.getPreferences().crashReportingConsent = true
            context.getPreferences().crashReportingConsentAsked = true
            (context.applicationContext as SmokeScreen).initSentry(true)
            onConsentGiven?.invoke()
            dialog.dismiss()
        }
        setButton(
            DialogInterface.BUTTON_NEGATIVE,
            context.getString(R.string.dialog_crashreporting_negative)
        ) { dialog, _ ->
            context.getPreferences().crashReportingEnabled = false
            context.getPreferences().crashReportingConsent = false
            context.getPreferences().crashReportingConsentAsked = true
            Sentry.close()
            dialog.dismiss()
        }
        setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.dialog_crashreporting_neutral)) { _, _ ->

        }
        setOnShowListener {
            getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                val usagesDialog = Builder(context, context.getPreferences().theme.dialogStyle)
                usagesDialog.setTitle(R.string.dialog_crashreportingusages_title)
                usagesDialog.setMessage(R.string.dialog_crashreportingusages_message)
                val view = layoutInflater.inflate(R.layout.dialog_crashreportingusages, null, false)
                usagesDialog.setView(view)
                view.list.setAdapter(UsedDataListAdapter())
                usagesDialog.setNeutralButton(R.string.all_close) { _, _ ->

                }
                usagesDialog.setPositiveButton(R.string.menu_privacypolicy, null)
                val actualDialog = usagesDialog.create()
                actualDialog.setOnShowListener {
                    actualDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                        showPrivacyPolicyDialog(context)
                    }
                }
                actualDialog.show()
            }
        }
    }

    override fun destroy() {
    }

    private inner class UsedDataListAdapter() : BaseExpandableListAdapter() {
        private val titles = context.resources.getStringArray(R.array.dialog_crashreportingusages_data)
        private val children = listOf(
            context.resources.getStringArray(R.array.dialog_crashreportingusages_data_user),
            context.resources.getStringArray(R.array.dialog_crashreportingusages_data_device),
            context.resources.getStringArray(R.array.dialog_crashreportingusages_data_app),
            context.resources.getStringArray(R.array.dialog_crashreportingusages_data_os)
        )

        override fun getGroup(groupPosition: Int): Any = titles[groupPosition]
        override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean = false
        override fun hasStableIds(): Boolean = true
        override fun getChildrenCount(groupPosition: Int): Int = children[groupPosition].size
        override fun getChild(groupPosition: Int, childPosition: Int): Any = children[groupPosition][childPosition]
        override fun getGroupId(groupPosition: Int): Long = groupPosition.toLong()
        override fun getChildId(groupPosition: Int, childPosition: Int): Long = groupPosition*100L + childPosition
        override fun getGroupCount(): Int = titles.size

        override fun getGroupView(
            groupPosition: Int,
            isExpanded: Boolean,
            convertView: View?,
            parent: ViewGroup?
        ): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.dialog_crashreportingusages_listgroup, parent, false)
            view.text.text = titles[groupPosition]
            view.text.setTypeface(null, Typeface.BOLD)
            return view
        }

        override fun getChildView(
            groupPosition: Int,
            childPosition: Int,
            isLastChild: Boolean,
            convertView: View?,
            parent: ViewGroup?
        ): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.dialog_crashreportingusages_listitem, parent, false)
            view.text.text = children[groupPosition][childPosition]
            view.text.setTypeface(null, Typeface.BOLD)
            return view
        }
    }
}