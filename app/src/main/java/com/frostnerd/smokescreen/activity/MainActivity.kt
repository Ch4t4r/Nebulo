package com.frostnerd.smokescreen.activity

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.frostnerd.encrypteddnstunnelproxy.AbstractHttpsDNSHandle
import com.frostnerd.navigationdraweractivity.NavigationDrawerActivity
import com.frostnerd.navigationdraweractivity.StyleOptions
import com.frostnerd.navigationdraweractivity.items.*
import com.frostnerd.smokescreen.*
import com.frostnerd.smokescreen.fragment.MainFragment
import com.frostnerd.smokescreen.fragment.SettingsFragment

class MainActivity : NavigationDrawerActivity() {
    private var textColor: Int = 0
    private var backgroundColor: Int = 0
    private var inputElementColor: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getPreferences().theme.layoutStyle)
        super.onCreate(savedInstanceState)
        AbstractHttpsDNSHandle // Loads the known servers.
    }

    override fun createDrawerItems(): MutableList<DrawerItem> {
        return createMenu {
            fragmentItem(getString(R.string.menu_dnsoverhttps),
                iconLeft = getDrawable(R.drawable.ic_menu_dnsoverhttps),
                fragmentCreator = {
                    MainFragment()
                }
            )
            fragmentItem(getString(R.string.menu_settings),
                iconLeft = getDrawable(R.drawable.ic_menu_settings),
                fragmentCreator = {
                    SettingsFragment()
                })
            divider()
            clickableItem(getString(R.string.menu_rate),
                iconLeft = getDrawable(R.drawable.ic_star),
                onLongClick = null,
                onSimpleClick = { _, _, _ ->
                    rateApp()
                    false
                })
            clickableItem(getString(R.string.menu_contact_developer),
                iconLeft = getDrawable(R.drawable.ic_envelope),
                onLongClick = null,
                onSimpleClick = { _, _, _ ->
                    showEmailChooser(
                        getString(R.string.menu_contact_developer),
                        getString(R.string.app_name),
                        getString(R.string.support_contact_mail),
                        "\n\n\n\n\n\nSystem:\nApp version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                                "Android: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE} - ${Build.VERSION.CODENAME})"
                    )
                    false
                })
            clickableItem(getString(R.string.menu_about),
                iconLeft = getDrawable(R.drawable.ic_binoculars),
                onLongClick = null,
                onSimpleClick = { _, _, _ ->
                    showInfoTextDialog(
                        this@MainActivity,
                        getString(R.string.menu_about),
                        getString(R.string.about_app, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
                    )
                    false
                })
        }
    }

    private fun rateApp() {
        val appPackageName = this.packageName
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName")))
        } catch (e: android.content.ActivityNotFoundException) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")
                )
            )
        }
        getPreferences().hasRatedApp = true
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
