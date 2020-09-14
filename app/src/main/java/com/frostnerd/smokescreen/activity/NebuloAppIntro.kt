package com.frostnerd.smokescreen.activity

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.getPreferences
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment
import com.github.appintro.AppIntroPageTransformerType

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

class NebuloAppIntro:AppIntro() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getPreferences().theme.id)
        super.onCreate(savedInstanceState)
        setTransformer(AppIntroPageTransformerType.Zoom)
        isImmersive = false
        showStatusBar(true)

        val textColor = getPreferences().theme.getTextColor(this)
        val backgroundColor = getPreferences().theme.resolveAttribute(theme, android.R.attr.colorBackground)
        addSlide(
            AppIntroFragment.newInstance(
                title = getString(R.string.appintro_first_title),
                description = getString(R.string.appintro_first_description),
            titleColor = textColor,
            descriptionColor = textColor, backgroundColor = backgroundColor
        ))

        addSlide(
            AppIntroFragment.newInstance(
                title = getString(R.string.appintro_second_title),
                description = getString(R.string.appintro_second_description),
                titleColor = textColor,
                descriptionColor = textColor, backgroundColor = backgroundColor
            ))
        addSlide(
            AppIntroFragment.newInstance(
                title = getString(R.string.appintro_third_title),
                description = getString(R.string.appintro_third_description),
                titleColor = textColor,
                descriptionColor = textColor, backgroundColor = backgroundColor
            ))
        if(!resources.getBoolean(R.bool.hide_adblocking_servers)) addSlide(
            AppIntroFragment.newInstance(
                title = getString(R.string.appintro_forth_title),
                description = getString(R.string.appintro_forth_description),
                titleColor = textColor,
                descriptionColor = textColor, backgroundColor = backgroundColor
            ))
    }

    override fun onSkipPressed(currentFragment: Fragment?) {
        val startIntent = Intent(this, MainActivity::class.java)
        startIntent.putExtras(intent?.extras?.getBundle("extras") ?: Bundle())
        startActivity(PinActivity.passPin(startIntent))
        finish()
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        onSkipPressed(currentFragment)
    }
}