package com.frostnerd.smokescreen.activity

import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.frostnerd.navigationdraweractivity.NavigationDrawerActivity
import com.frostnerd.navigationdraweractivity.StyleOptions
import com.frostnerd.navigationdraweractivity.items.DrawerItem
import com.frostnerd.navigationdraweractivity.items.FragmentDrawerItem
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.fragment.MainFragment
import com.frostnerd.smokescreen.fragment.SettingsFragment
import com.frostnerd.smokescreen.util.Preferences
import com.frostnerd.smokescreen.getPreferences

class MainActivity : NavigationDrawerActivity() {
    private var textColor: Int = 0
    private var backgroundColor: Int = 0
    private var inputElementColor:Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Preferences.getInstance(this).getTheme().layoutStyle)
        super.onCreate(savedInstanceState)
    }

    override fun createDrawerItems(): MutableList<DrawerItem> {
        val items = mutableListOf<DrawerItem>()

        items.add(
            FragmentDrawerItem(getString(R.string.menu_dnsoverhttps),
                iconLeft = getDrawable(R.drawable.ic_menu_dnsoverhttps),
                fragmentCreator = object : FragmentDrawerItem.FragmentCreator {
                    override fun getFragment(arguments: Bundle?): Fragment {
                        return MainFragment()
                    }
                })
        )
        items.add(
            FragmentDrawerItem(getString(R.string.menu_settings),
                iconLeft =  getDrawable(R.drawable.ic_menu_settings),
                fragmentCreator = object : FragmentDrawerItem.FragmentCreator {
                    override fun getFragment(arguments: Bundle?): Fragment {
                        return SettingsFragment()
                    }
                })
        )
        return items
    }

    override fun createStyleOptions(): StyleOptions {
        backgroundColor = getPreferences().getTheme().resolveAttribute(theme, android.R.attr.colorBackground)
        textColor = getPreferences().getTheme().resolveAttribute(theme, android.R.attr.textColor)
        inputElementColor = getPreferences().getTheme().getColor(this, R.attr.inputElementColor, Color.WHITE)

        val options = StyleOptions()
        options.useDefaults()
        options.selectedListItemBackgroundColor = inputElementColor
        options.selectedListItemTextColor = textColor
        options.listItemBackgroundColor = backgroundColor
        options.listViewBackgroundColor = backgroundColor
        options.listItemTextColor = textColor
        options.headerTextColor = textColor
        options.alphaSelected = 1f
        options.iconTintLeft = getPreferences().getTheme().resolveAttribute(theme, R.attr.navDrawableColor)
        return options
    }

    override fun getConfiguration(): Configuration = Configuration.withDefaults()
    override fun getDefaultItem(): DrawerItem = drawerItems[0]
    override fun maxBackStackRecursion(): Int = 5
    override fun useItemBackStack(): Boolean = true

    override fun onItemClicked(item: DrawerItem, handle: Boolean) {

    }
}
