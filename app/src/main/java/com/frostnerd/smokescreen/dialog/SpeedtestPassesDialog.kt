package com.frostnerd.smokescreen.dialog

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.getPreferences
import kotlinx.android.synthetic.main.dialog_speedtest_passes.view.*

/*
 * Copyright (C) 2020 Daniel Wolf (Ch4t4r)
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
class SpeedtestPassesDialog(context: Context, onSelected:(passes:Int) -> Unit):AlertDialog(context, context.getPreferences().theme.dialogStyle) {

    init {
        val view = layoutInflater.inflate(R.layout.dialog_speedtest_passes, null, false)
        val passes = view.testPasses
        setTitle(R.string.dialog_speedtestpasses_title)
        setView(view)
        setButton(BUTTON_POSITIVE, context.getText(android.R.string.ok)) { _, _ ->
            passes.text.toString().toIntOrNull()?.let(onSelected)
        }
        setButton(BUTTON_NEGATIVE, context.getString(android.R.string.cancel)) { _, _ ->
            dismiss()
        }
    }

}