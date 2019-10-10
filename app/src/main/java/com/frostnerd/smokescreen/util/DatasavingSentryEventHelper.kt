package com.frostnerd.smokescreen.util

import android.os.Build
import com.frostnerd.smokescreen.BuildConfig
import io.sentry.event.EventBuilder
import io.sentry.event.helper.EventBuilderHelper

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
class DatasavingSentryEventHelper :EventBuilderHelper {

    override fun helpBuildingEvent(eventBuilder: EventBuilder) {
        eventBuilder.withDist(BuildConfig.VERSION_CODE.toString())
        eventBuilder.withContexts(contexts)
    }

    private val contexts = mutableMapOf<String, MutableMap<String, Any>>()
    init {
        val osMap = mutableMapOf<String, Any>()
        val appMap = mutableMapOf<String, Any>()

        osMap["name"] = "Android"
        osMap["version"] = Build.VERSION.RELEASE
        osMap["build"] = Build.DISPLAY
        appMap["app_version"] = BuildConfig.VERSION_NAME
        appMap["app_identifier"] = "com.frostnerd.smokescreen"
        appMap["app_build"] = BuildConfig.VERSION_CODE

        contexts["os"] = osMap
        contexts["app"] = appMap
    }
}