package com.frostnerd.smokescreen.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.get
import androidx.fragment.app.Fragment
import com.frostnerd.smokescreen.BuildConfig
import com.frostnerd.smokescreen.R
import com.github.appintro.SlidePolicy
import kotlinx.android.synthetic.main.fragment_appintro_serverchoose.view.*

/*
 * Copyright (C) 2020 Daniel Wolf (Ch4t4r)
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

class AppIntroServerChooseFragment: Fragment(), SlidePolicy {
    var serverType:ServerType? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_appintro_serverchoose, container, false)
        if(BuildConfig.SHOW_ALL_SERVERS) {
            view.buttonGroup[4].visibility = View.VISIBLE
        }
        view.buttonGroup.setOnCheckedChangeListener { _, checkedId ->
            serverType = when(checkedId) {
                1 -> ServerType.FASTEST
                2 -> ServerType.FASTEST_PRIVACY
                3 -> ServerType.PRIVACY
                4 -> ServerType.ANTI_MALWARE
                5 -> ServerType.ADS
                else -> ServerType.FASTEST
            }
        }
        return view
    }

    override val isPolicyRespected: Boolean
        get() = serverType != null

    override fun onUserIllegallyRequestedNextPage() {

    }

    enum class ServerType {
        FASTEST, FASTEST_PRIVACY, PRIVACY, ANTI_MALWARE, ADS
    }

}