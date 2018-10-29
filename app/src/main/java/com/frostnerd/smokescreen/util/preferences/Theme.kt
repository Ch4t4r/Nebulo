package com.frostnerd.smokescreen.util.preferences

import android.content.Context
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

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */

enum class Theme(val id: Int, @StyleRes val layoutStyle: Int, @StyleRes val dialogStyle: Int, @StyleRes val preferenceStyle: Int) {
    MONO(1, R.style.AppTheme_Mono, R.style.DialogTheme_Mono, R.style.PreferenceTheme_Mono),
    DARK(2, R.style.AppTheme_Dark, R.style.DialogTheme_Dark, R.style.PreferenceTheme_Dark),
    TRUE_BLACK(3, R.style.AppTheme_True_Black, R.style.DialogTheme_True_Black, R.style.PreferenceTheme_True_Black),
    BLUE(4, R.style.AppTheme_Blue, R.style.DialogTheme_Blue, R.style.PreferenceTheme_Blue);

    @ColorInt
    fun getColor(context: Context, @AttrRes attribute: Int, @ColorInt defaultValue:Int = Color.BLACK):Int {
        val ta = context.obtainStyledAttributes(layoutStyle, intArrayOf(attribute))
        @ColorInt val color = ta.getColor(0, defaultValue)
        ta.recycle()
        return color
    }

    fun getDrawable(context: Context, @AttrRes attribute:Int): Drawable? {
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
    fun getTextColor(context: Context):Int {
        return getColor(context, android.R.attr.textColor)
    }

    companion object {
        fun findById(id: Int): Theme? {
            return values().find { it.id == id }
        }

        fun ids():IntArray {
            val arr = IntArray(values().size)
            for ((i, value) in values().withIndex()) {
                arr[i] = value.id
            }
            return arr
        }
    }
}

class ThemePreference(key:String, defaultValue:Theme): PreferenceTypeWithDefault<Theme>(key, defaultValue) {

    override fun getValue(thisRef: TypedPreferences, property: KProperty<*>): Theme {
        return if(thisRef.sharedPreferences.contains(key)) Theme.findById(thisRef.sharedPreferences.getInt(key, Theme.MONO.id))!!
        else defaultValue(key)
    }

    override fun setValue(thisRef: TypedPreferences, property: KProperty<*>, value: Theme) {
        thisRef.edit {
            putInt(key, value.id)
        }
    }

}