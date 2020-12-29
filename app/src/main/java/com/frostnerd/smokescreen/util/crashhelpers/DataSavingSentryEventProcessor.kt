package com.frostnerd.smokescreen.util.crashhelpers

import android.os.Build
import com.frostnerd.smokescreen.BuildConfig
import io.sentry.EventProcessor
import io.sentry.SentryEvent
import io.sentry.protocol.App
import io.sentry.protocol.Device
import io.sentry.protocol.OperatingSystem

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
class DataSavingSentryEventProcessor: EventProcessor {

    override fun process(event: SentryEvent, hint: Any?): SentryEvent {
        event.contexts.device = Device()
        event.contexts.app = App().apply {
            appVersion = BuildConfig.VERSION_NAME
            appIdentifier = "com.frostnerd.smokescreen"
            appName = "Nebulo"
            appBuild = BuildConfig.VERSION_CODE.toString()
        }
        event.contexts.operatingSystem = OperatingSystem().apply {
            name = "Android"
            version = Build.VERSION.RELEASE
            build = Build.DISPLAY
        }
        event.dist = BuildConfig.VERSION_CODE.toString()
        return event
    }
}