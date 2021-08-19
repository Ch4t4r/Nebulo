package com.frostnerd.smokescreen

import android.app.Activity
import android.widget.Toast
import com.frostnerd.smokescreen.util.AppUpdater
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.tasks.Task

/*
 * Copyright (C) 2021 Daniel Wolf (Ch4t4r)
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
class AppUpdaterImpl: AppUpdater {
    override fun checkAndTriggerUpdate(context: Activity, requestCode: Int) {
        if(BuildConfig.IN_APP_UPDATES) {
            try {
                val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)
                val appUpdateInfoTask: Task<AppUpdateInfo> = appUpdateManager.appUpdateInfo
                appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
                    try {
                        if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                            val stalenessDays = appUpdateInfo.clientVersionStalenessDays() ?: Int.MIN_VALUE
                            val shouldHoldUpdate: Boolean = context.getPreferences().holdUpdateUntil?.let { System.currentTimeMillis() >= it } ?: false
                            val shouldUpdateImmediate = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) && appUpdateInfo.updatePriority() >= 4
                            var shouldUpdate = !shouldHoldUpdate && stalenessDays >= 24
                            shouldUpdate =
                                shouldUpdate || stalenessDays >= 14 && !shouldHoldUpdate && appUpdateInfo.updatePriority() >= 1
                            shouldUpdate =
                                shouldUpdate || stalenessDays >= 7 && !shouldHoldUpdate && appUpdateInfo.updatePriority() >= 2
                            shouldUpdate =
                                shouldUpdate || stalenessDays >= 3 && appUpdateInfo.updatePriority() >= 3
                            shouldUpdate =
                                shouldUpdate || stalenessDays >= 1 && appUpdateInfo.updatePriority() >= 4
                            shouldUpdate = shouldUpdate || appUpdateInfo.updatePriority() >= 5
                            if (shouldUpdate) {
                                appUpdateManager.startUpdateFlowForResult(
                                    appUpdateInfo,
                                    if (shouldUpdateImmediate) AppUpdateType.IMMEDIATE else AppUpdateType.FLEXIBLE,
                                    context,
                                    requestCode
                                )
                            }
                        }
                    } catch (ex2: Throwable) {
                        ex2.printStackTrace()
                    }
                }
            } catch (ex: Throwable) {
                ex.printStackTrace()
            }
        }
    }

}