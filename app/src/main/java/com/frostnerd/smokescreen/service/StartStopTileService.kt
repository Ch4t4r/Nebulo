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

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
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