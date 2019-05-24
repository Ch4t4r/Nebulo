package com.frostnerd.smokescreen.service

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.frostnerd.general.service.isServiceRunning
import com.frostnerd.smokescreen.R

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

fun Context.updateServiceTile() {
    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        TileService.requestListeningState(this, ComponentName(this, StartStopTileService::class.java))
    }
}

@RequiresApi(Build.VERSION_CODES.N)
class StartStopTileService:TileService() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateTileState()
        return Service.START_STICKY
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        if(isServiceRunning(DnsVpnService::class.java)) {
            DnsVpnService.sendCommand(this, Command.STOP)
            updateTileState(false)
        } else {
            DnsVpnService.startVpn(this)
            updateTileState(true)
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        updateTileState()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTileState()
    }

    private fun updateTileState(serviceRunning:Boolean = isServiceRunning(DnsVpnService::class.java)) {
        val tile:Tile? = qsTile
        if(tile != null) {
            if(serviceRunning) {
                tile.label = getString(R.string.quicksettings_stop_text)
                tile.icon = Icon.createWithResource(this, R.drawable.ic_stop)
                tile.state = Tile.STATE_ACTIVE
                tile.contentDescription = getString(R.string.contentdescription_stop_app)
            } else {
                tile.label = getString(R.string.quicksettings_start_text)
                tile.icon = Icon.createWithResource(this, R.drawable.ic_play)
                tile.state = Tile.STATE_INACTIVE
                tile.contentDescription = getString(R.string.contentdescription_start_app)
            }
            tile.updateTile()
        }
    }

}