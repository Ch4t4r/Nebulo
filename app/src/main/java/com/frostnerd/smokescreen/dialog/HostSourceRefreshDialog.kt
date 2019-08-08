package com.frostnerd.smokescreen.dialog

import android.content.Context
import android.content.DialogInterface
import android.view.View
import android.widget.ArrayAdapter
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.getPreferences
import kotlinx.android.synthetic.main.dialog_host_source_refresh.view.*

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
class HostSourceRefreshDialog(context:Context,
                              runRefresh:() -> Unit,
                              refreshConfigChanged:() -> Unit):AlertDialog(context, context.getPreferences().theme.dialogStyle) {

    init {
        setTitle(R.string.dialog_hostsourcerefresh_title)
        val view = layoutInflater.inflate(R.layout.dialog_host_source_refresh, null, false)
        setView(view)
        view.refreshNow.setOnClickListener {
            runRefresh()
        }
        val changeAutomaticRefreshStatus:(Boolean) -> Unit = { isChecked ->
            view.refreshWifiOnly.isEnabled = isChecked
            view.timeAmountTil.isEnabled = isChecked
            view.timeUnit.isEnabled = isChecked
            view.refreshTimeWrap.visibility = if(isChecked) View.VISIBLE else View.INVISIBLE
        }
        view.automaticRefresh.setOnCheckedChangeListener { _, isChecked ->
            changeAutomaticRefreshStatus(isChecked)
        }
        view.automaticRefresh.isChecked = context.getPreferences().automaticHostRefresh
        view.refreshWifiOnly.isChecked = context.getPreferences().automaticHostRefreshWifiOnly
        view.timeAmount.setText(context.getPreferences().automaticHostRefreshTimeAmount.toString())
        view.timeUnit.setSelection(context.getPreferences().automaticHostRefreshTimeUnit.ordinal)
        changeAutomaticRefreshStatus(view.automaticRefresh.isChecked)
        val adapter: ArrayAdapter<CharSequence> = ArrayAdapter.createFromResource(
            context,
            R.array.dialog_hostsourcerefresh_timeunits,
            R.layout.item_tasker_action_spinner_item
        )
        adapter.setDropDownViewResource(R.layout.item_tasker_action_spinner_dropdown_item)
        view.timeUnit.adapter = adapter
        setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(android.R.string.cancel)) { dialog, _ ->
            dialog.dismiss()
        }
        setButton(DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok)) { dialog, _ ->
            dialog.dismiss()
            context.getPreferences().automaticHostRefresh = view.automaticRefresh.isChecked
            context.getPreferences().automaticHostRefreshWifiOnly = view.refreshWifiOnly.isChecked
            context.getPreferences().automaticHostRefreshTimeAmount = view.timeAmount.text.toString().toInt()
            context.getPreferences().automaticHostRefreshTimeUnit = TimeUnit.values().find { it.ordinal == view.timeUnit.selectedItemPosition }!!
            refreshConfigChanged()
        }
    }

    @Keep
    enum class TimeUnit {
        HOURS, DAYS, WEEKS
    }
}