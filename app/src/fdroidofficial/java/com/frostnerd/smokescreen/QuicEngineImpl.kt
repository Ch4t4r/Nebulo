package com.frostnerd.smokescreen

import android.content.Context
import com.frostnerd.encrypteddnstunnelproxy.QuicEngine
import com.frostnerd.encrypteddnstunnelproxy.quic.QuicUpstreamAddress
import java.net.URL
import java.net.URLConnection

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
class QuicEngineImpl(context: Context, private val quicOnly:Boolean, vararg addresses:QuicUpstreamAddress):QuicEngine(context, *addresses) {
	// NO-OP Implementation.
    companion object {
        val providerInstalled = false
        fun installNetworkEngine(context: Context, onSuccess:(() -> Unit)? = null) {

        }
    }

    override fun openConnection(url: URL): URLConnection {
        error("NOIMPL")
    }

    override fun shutdown() {
        error("NOIMPL")
    }
}