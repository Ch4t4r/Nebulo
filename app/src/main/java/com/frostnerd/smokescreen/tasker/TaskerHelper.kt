package com.frostnerd.smokescreen.tasker

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

internal object TaskerHelper {
    val ACTION_FIRE = "com.twofortyfouram.locale.intent.action.FIRE_SETTING"
    val EXTRAS_BUNDLE_KEY = "com.twofortyfouram.locale.intent.extra.BUNDLE"
    val EXTRAS_BLURB_KEY = "com.twofortyfouram.locale.intent.extra.BLURB"

    val DATA_KEY_ACTION = "action"
    val DATA_KEY_STARTIFRUNNING = "start_if_running"
    val DATA_KEY_PRIMARYSERVER = "primaryServer"
    val DATA_KEY_SECONDARYSERVER = "secondaryServer"

}