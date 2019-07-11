package com.frostnerd.smokescreen.dialog

import android.content.Context
import android.content.DialogInterface
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.SimpleExpandableListAdapter
import com.frostnerd.lifecyclemanagement.BaseDialog
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.getPreferences
import kotlinx.android.synthetic.main.dialog_crashreportingusages_listgroup.view.*
import kotlinx.android.synthetic.main.dialog_licenses.view.*

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
class LicensesDialog(context: Context):BaseDialog(context, context.getPreferences().theme.dialogStyle) {

    init {
        val libraries = mutableMapOf<String, String>()
        libraries["sentry-java (BSD 3-Clause revised)"] = context.getString(R.string.license_sentry)
        libraries["Material Design Icons (Apache License Version 2.0)"] = context.getString(R.string.license_apache2)
        libraries["FABProgressCircle (Apache License Version 2.0)"] = context.getString(R.string.license_apache2)
        libraries["LeakCanary (Apache License Version 2.0)"] = context.getString(R.string.license_apache2)

        setTitle(R.string.dialog_about_licenses)
        val view = layoutInflater.inflate(R.layout.dialog_licenses, null, false)
        view.list.setAdapter(LicenseAdapter(libraries))
        setView(view)
        setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.all_close)) { dialog, _ ->
            dialog.dismiss()
        }
    }

    override fun destroy() {
    }

    private inner class LicenseAdapter(val licenses:Map<String, String>):BaseExpandableListAdapter() {
        private var titles = licenses.keys.toList().sorted()
        override fun getGroup(groupPosition: Int): Any = titles[groupPosition]
        override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean = false
        override fun hasStableIds(): Boolean = true
        override fun getChildrenCount(groupPosition: Int): Int = 1
        override fun getChild(groupPosition: Int, childPosition: Int): Any = licenses.getValue(titles[groupPosition])
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
            view.text.text = getChild(groupPosition, childPosition).toString()
            view.text.setTypeface(null, Typeface.BOLD)
            return view
        }
    }

}