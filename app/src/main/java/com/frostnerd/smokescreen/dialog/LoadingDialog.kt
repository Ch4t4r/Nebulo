package com.frostnerd.smokescreen.dialog

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import com.frostnerd.lifecyclemanagement.BaseDialog
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.getPreferences
import kotlinx.android.synthetic.main.dialog_loading.view.*

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
class LoadingDialog(context:Context, title:String, private var message: String?) :BaseDialog(context, context.getPreferences().theme.dialogStyle) {
    constructor(context: Context, @StringRes title:Int):this(context, context.getString(title), null)
    constructor(context: Context, @StringRes title:Int, @StringRes message:Int):this(context, context.getString(title), context.getString(message))
    constructor(context: Context, title:String):this(context, title, null)

    private val messageView:TextView

    init {
        setCancelable(false)
        setTitle(title)
        val view: View = layoutInflater.inflate(R.layout.dialog_loading, null, false)
        setView(view)
        messageView = view.text
        messageView.text = message
    }

    override fun setMessage(message: CharSequence?) {
        messageView.text = message
        this.message = message.toString()
    }

    fun appendToMessage(string: String) {
        messageView.text = (message ?: "") + string
    }

    override fun destroy() {}

}