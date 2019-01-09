package com.frostnerd.smokescreen.util.preferences

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import com.frostnerd.preferenceskt.typedpreferences.TypedPreferences
import com.frostnerd.preferenceskt.typedpreferences.types.PreferenceTypeWithDefault
import com.frostnerd.smokescreen.R
import kotlin.reflect.KProperty

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

enum class Theme(val id: Int, @StyleRes val layoutStyle: Int, @StyleRes val dialogStyle: Int, @StyleRes val preferenceStyle: Int) {
    MONO(1, R.style.AppTheme_Mono, R.style.DialogTheme_Mono, R.style.PreferenceTheme_Mono),
    DARK(2, R.style.AppTheme_Dark, R.style.DialogTheme_Dark, R.style.PreferenceTheme_Dark),
    TRUE_BLACK(3, R.style.AppTheme_True_Black, R.style.DialogTheme_True_Black, R.style.PreferenceTheme_True_Black),
    BLUE(4, R.style.AppTheme_Blue, R.style.DialogTheme_Blue, R.style.PreferenceTheme_Blue);

    @ColorInt
    fun getColor(context: Context, @AttrRes attribute: Int, @ColorInt defaultValue: Int = Color.BLACK): Int {
        val ta = context.obtainStyledAttributes(layoutStyle, intArrayOf(attribute))
        @ColorInt val color = ta.getColor(0, defaultValue)
        ta.recycle()
        return color
    }

    fun getDrawable(context: Context, @AttrRes attribute: Int): Drawable? {
        val ta = context.obtainStyledAttributes(layoutStyle, intArrayOf(attribute))
        val drawable = ta.getDrawable(0)
        ta.recycle()
        return drawable
    }

    fun resolveAttribute(theme: Resources.Theme, @AttrRes attribute: Int): Int {
        val value = TypedValue()
        theme.resolveAttribute(attribute, value, true)
        return value.data
    }

    @ColorInt
    fun getSelectedItemColor(context: Context): Int {
        return getColor(context, R.attr.inputElementColor, -1)
    }

    @ColorInt
    fun getTextColor(context: Context): Int {
        return getColor(context, android.R.attr.textColor)
    }

    companion object {
        fun findById(id: Int): Theme? {
            return values().find { it.id == id }
        }

        fun ids(): IntArray {
            val arr = IntArray(values().size)
            for ((i, value) in values().withIndex()) {
                arr[i] = value.id
            }
            return arr
        }
    }
}

class ThemePreference(key: String, defaultValue: Theme) :
    PreferenceTypeWithDefault<SharedPreferences, Theme>(key, defaultValue) {

    override fun getValue(thisRef: TypedPreferences<SharedPreferences>, property: KProperty<*>): Theme {
        return if (thisRef.sharedPreferences.contains(key)) Theme.findById(
            thisRef.sharedPreferences.getString(
                key,
                Theme.MONO.id.toString()
            )!!.toInt()
        )!!
        else defaultValue(key)
    }

    override fun setValue(thisRef: TypedPreferences<SharedPreferences>, property: KProperty<*>, value: Theme) {
        thisRef.edit { listener ->
            putString(key, value.id.toString())
            listener(key, value)
        }
    }

}