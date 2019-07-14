package com.frostnerd.smokescreen.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.frostnerd.smokescreen.activity.MainActivity

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

enum class DeepActionState {
    DNS_RULES;


    fun intentTo(context:Context): Intent {
        return Intent(context, MainActivity::class.java).putExtra("deep_action", this)
    }

    fun pendingIntentTo(context: Context):PendingIntent {
        return PendingIntent.getActivity(context, 1, intentTo(context), PendingIntent.FLAG_CANCEL_CURRENT)
    }
}