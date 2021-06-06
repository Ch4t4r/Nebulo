package com.frostnerd.smokescreen

import android.content.Context
import com.frostnerd.encrypteddnstunnelproxy.QuicEngine
import com.frostnerd.encrypteddnstunnelproxy.quic.QuicUpstreamAddress
import com.google.android.gms.net.CronetProviderInstaller
import org.chromium.net.CronetEngine
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
    companion object {
        var providerInstalled:Boolean = false
        private set

        fun installNetworkEngine(context: Context, onSuccess:(() -> Unit)? = null) {
            try {
                if(CronetProviderInstaller.isInstalled()) {
                    providerInstalled = true
                    onSuccess?.invoke()
                } else {
                    CronetProviderInstaller.installProvider(context).addOnCompleteListener {
                        providerInstalled = true
                        onSuccess?.invoke()
                    }
                }
            } catch (ex:Throwable) {
                ex.printStackTrace()
            }
        }
    }
    private var engine: CronetEngine? = null

    init {
        installNetworkEngine(context) {
            engine = createEngine(context, *addresses)
        }
    }

    private fun createEngine(context: Context, vararg addresses:QuicUpstreamAddress): CronetEngine {
        val cacheDir = context.cacheDir.resolve("cronetcache")
        cacheDir.mkdir()
        return CronetEngine.Builder(context)
            .apply {
                addresses.forEach {
                    addQuicHint(it.host, it.port, 443)
                }
            }.enableHttp2(!quicOnly)
            .enableBrotli(true)
            .enableQuic(true)
            .setStoragePath(cacheDir.path)
            .build()
    }

    override fun openConnection(url: URL): URLConnection {
        return engine!!.openConnection(url)
    }

    override fun shutdown() {
        engine?.shutdown()
    }

    fun usable():Boolean {
        return providerInstalled && engine != null
    }
}