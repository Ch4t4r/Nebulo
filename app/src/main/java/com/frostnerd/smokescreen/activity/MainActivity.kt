package com.frostnerd.smokescreen.activity

import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.frostnerd.encrypteddnstunnelproxy.AbstractHttpsDNSHandle
import com.frostnerd.navigationdraweractivity.NavigationDrawerActivity
import com.frostnerd.navigationdraweractivity.StyleOptions
import com.frostnerd.navigationdraweractivity.items.ClickableDrawerItem
import com.frostnerd.navigationdraweractivity.items.DividerDrawerItem
import com.frostnerd.navigationdraweractivity.items.DrawerItem
import com.frostnerd.navigationdraweractivity.items.FragmentDrawerItem
import com.frostnerd.smokescreen.BuildConfig
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.fragment.MainFragment
import com.frostnerd.smokescreen.fragment.SettingsFragment
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.showInfoTextDialog

class MainActivity : NavigationDrawerActivity() {
    private var textColor: Int = 0
    private var backgroundColor: Int = 0
    private var inputElementColor:Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getPreferences().theme.layoutStyle)
        super.onCreate(savedInstanceState)
        AbstractHttpsDNSHandle // Loads the known servers.
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
        items.add(DividerDrawerItem())
        items.add(
            ClickableDrawerItem(getString(R.string.menu_about),
                iconLeft = getDrawable(R.drawable.ic_binoculars),
                clickListener = object :ClickableDrawerItem.ClickListener {
                    override fun onClick(
                        item: ClickableDrawerItem,
                        drawerActivity: NavigationDrawerActivity,
                        arguments: Bundle?
                    ): Boolean {
                        showInfoTextDialog(this@MainActivity,
                            getString(R.string.menu_about),
                            getString(R.string.about_app, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                        return false
                    }

                    override fun onLongClick(
                        item: ClickableDrawerItem,
                        drawerActivity: NavigationDrawerActivity
                    ): Boolean {
                       return false
                    }
                })
        )
        return items
    }

    override fun createStyleOptions(): StyleOptions {
        backgroundColor = getPreferences().theme.resolveAttribute(theme, android.R.attr.colorBackground)
        textColor = getPreferences().theme.resolveAttribute(theme, android.R.attr.textColor)
        inputElementColor = getPreferences().theme.getColor(this, R.attr.inputElementColor, Color.WHITE)

        val options = StyleOptions()
        options.useDefaults()
        options.selectedListItemBackgroundColor = inputElementColor
        options.selectedListItemTextColor = textColor
        options.listItemBackgroundColor = backgroundColor
        options.listViewBackgroundColor = backgroundColor
        options.listItemTextColor = textColor
        options.headerTextColor = textColor
        options.alphaSelected = 1f
        options.iconTintLeft = getPreferences().theme.resolveAttribute(theme, R.attr.navDrawableColor)
        return options
    }

    override fun getConfiguration(): Configuration = Configuration.withDefaults()
    override fun getDefaultItem(): DrawerItem = drawerItems[0]
    override fun maxBackStackRecursion(): Int = 5
    override fun useItemBackStack(): Boolean = true

    override fun onItemClicked(item: DrawerItem, handle: Boolean) {

    }
}
