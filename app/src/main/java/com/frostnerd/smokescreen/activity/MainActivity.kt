package com.frostnerd.smokescreen.activity

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.frostnerd.encrypteddnstunnelproxy.AbstractHttpsDNSHandle
import com.frostnerd.encrypteddnstunnelproxy.tls.AbstractTLSDnsHandle
import com.frostnerd.navigationdraweractivity.NavigationDrawerActivity
import com.frostnerd.navigationdraweractivity.StyleOptions
import com.frostnerd.navigationdraweractivity.items.BasicDrawerItem
import com.frostnerd.navigationdraweractivity.items.DrawerItem
import com.frostnerd.navigationdraweractivity.items.createMenu
import com.frostnerd.navigationdraweractivity.items.singleInstanceFragment
import com.frostnerd.smokescreen.*
import com.frostnerd.smokescreen.database.AppDatabase
import com.frostnerd.smokescreen.dialog.ChangelogDialog
import com.frostnerd.smokescreen.dialog.CrashReportingEnableDialog
import com.frostnerd.smokescreen.dialog.LicensesDialog
import com.frostnerd.smokescreen.dialog.NewServerDialog
import com.frostnerd.smokescreen.fragment.MainFragment
import com.frostnerd.smokescreen.fragment.QueryLogFragment
import com.frostnerd.smokescreen.fragment.SettingsFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.dialog_privacypolicy.view.*

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
class MainActivity : NavigationDrawerActivity() {
    private var textColor: Int = 0
    private var backgroundColor: Int = 0
    private var inputElementColor: Int = 0
    private var startedActivity = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getPreferences().theme.layoutStyle)
        super.onCreate(savedInstanceState)
        Logger.enabledGlobally = getPreferences().loggingEnabled
        AbstractHttpsDNSHandle // Loads the known servers.
        AbstractTLSDnsHandle
        /*setCardView { viewParent, suggestedHeight ->
            val view = layoutInflater.inflate(R.layout.menu_cardview, viewParent, false)
            view
        }*/
        supportActionBar?.elevation = 0f
        ChangelogDialog.showNewVersionChangelog(this)
        if(getPreferences().shouldShowCrashReportingConsentDialog()) {
            CrashReportingEnableDialog(this, onConsentGiven = {
                val bar = Snackbar.make(findViewById<View>(android.R.id.content), R.string.crashreporting_thankyou, Snackbar.LENGTH_INDEFINITE)
                    bar.setAction(R.string.crashreporting_thankyou_gotit) {
                        bar.dismiss()
                    }.show()
            }).show()
        }
    }

    override fun createDrawerItems(): MutableList<DrawerItem> {
        return createMenu {
            fragmentItem(getString(R.string.menu_dnsoverhttps),
                iconLeft = getDrawable(R.drawable.ic_menu_dnsoverhttps),
                fragmentCreator = singleInstanceFragment { MainFragment() }
            )
            fragmentItem(getString(R.string.menu_settings),
                iconLeft = getDrawable(R.drawable.ic_menu_settings),
                fragmentCreator = singleInstanceFragment { SettingsFragment() })
            if (getPreferences().queryLoggingEnabled) {
                divider()
                fragmentItem(getString(R.string.menu_querylogging),
                    iconLeft = getDrawable(R.drawable.ic_eye),
                    fragmentCreator = {
                        QueryLogFragment()
                    })
            }
            divider()
            clickableItem(getString(R.string.menu_create_shortcut),
                iconLeft = getDrawable(R.drawable.ic_external_link),
                onLongClick = null,
                onSimpleClick = { _, _, _ ->
                    NewServerDialog(
                        this@MainActivity,
                        title = getString(R.string.menu_create_shortcut),
                        onServerAdded = {
                            ShortcutActivity.createShortcut(this@MainActivity, it)
                        },
                        dnsOverHttps = true
                    ).show()
                    false
                })
            divider()
            if (isPackageInstalled(this@MainActivity, "com.android.vending")) {
                clickableItem(getString(R.string.menu_rate),
                    iconLeft = getDrawable(R.drawable.ic_star),
                    onLongClick = null,
                    onSimpleClick = { _, _, _ ->
                        rateApp()
                        false
                    })
            }
            if (isPackageInstalled(this@MainActivity, "org.fdroid.fdroid")) {
                clickableItem("Show on F-Droid",
                    iconLeft = getDrawable(R.drawable.ic_box_open),
                    onLongClick = null,
                    onSimpleClick = { _, _, _ ->
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).setPackage("org.fdroid.fdroid"))
                        false
                    }
                )
            }
            clickableItem(getString(R.string.menu_share_app),
                iconLeft = getDrawable(R.drawable.ic_share),
                onLongClick = null,
                onSimpleClick = { _, _, _ ->
                    val intent = Intent(Intent.ACTION_SEND)
                    intent.type = "text/plain"
                    intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                    intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.app_share_text))
                    startActivity(Intent.createChooser(intent, getString(R.string.menu_share_app)))
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
            if (isPackageInstalled(this@MainActivity, "org.telegram.messenger")) {
                clickableItem(getString(R.string.menu_telegram_group),
                    onLongClick = null,
                    iconLeft = getDrawable(R.drawable.ic_comments),
                    onSimpleClick = { _, _, _ ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://join?invite=I54nRleveRGP8IPmcIdySg"))
                        startActivity(intent)
                        false
                    })
            }
            clickableItem(getString(R.string.menu_privacypolicy),
                iconLeft = getDrawable(R.drawable.ic_gavel),
                onLongClick = null,
                onSimpleClick = { _, _ ,_ ->
                    showPrivacyPolicyDialog(this@MainActivity)
                    false
                })
            clickableItem(getString(R.string.menu_credits),
                iconLeft = getDrawable(R.drawable.ic_thumbs_up),
                onLongClick = null,
                onSimpleClick = { _, _ , _ ->
                    println(getString(R.string.dialog_credits_message))
                    showInfoTextDialog(this@MainActivity,
                        getString(R.string.dialog_credits_title),
                        getString(R.string.dialog_credits_message))
                    false
                })
            clickableItem(getString(R.string.menu_about),
                iconLeft = getDrawable(R.drawable.ic_binoculars),
                onLongClick = null,
                onSimpleClick = { _, _, _ ->
                    showInfoTextDialog(
                        this@MainActivity,
                        getString(R.string.menu_about),
                        getString(
                            R.string.about_app,
                            BuildConfig.VERSION_NAME + if(BuildConfig.DEBUG) " DEBUG" else "",
                            BuildConfig.VERSION_CODE,
                            AppDatabase.currentVersion,
                            if(getPreferences().crashReportingEnabled) getPreferences().crashReportingUUID else "---"
                        ),
                        positiveButton = getString(R.string.dialog_about_changelog) to { dialog, _ ->
                            dialog.dismiss()
                            ChangelogDialog(this@MainActivity, 22, showOptOut = false, showInfoText = false).show()
                        },
                        negativeButton = getString(R.string.dialog_about_licenses) to { dialog, _ ->
                            dialog.dismiss()
                            LicensesDialog(this@MainActivity).show()
                        }
                    )
                    false
                })
        }
    }

    override fun onBackPressed() {
        val fragment = currentFragment
        if (fragment != null && fragment is BackpressFragment) {
            if (!fragment.onBackPressed()) super.onBackPressed()
        } else {
            super.onBackPressed()
        }
    }

    private fun rateApp() {
        val appPackageName = this.packageName
        val openStore = {
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
        AlertDialog.Builder(this, getPreferences().theme.dialogStyle)
            .setMessage(R.string.dialog_rate_confirmation)
            .setPositiveButton(R.string.all_yes) { _, _ ->
                openStore()
            }
            .setNegativeButton(R.string.all_no) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
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
        options.separatorColor = options.iconTintLeft
        return options
    }

    override fun getConfiguration(): Configuration = Configuration.withDefaults()
    override fun getDefaultItem(): DrawerItem = drawerItems[0]
    override fun maxBackStackRecursion(): Int = 5
    override fun useItemBackStack(): Boolean = true

    override fun onItemClicked(item: DrawerItem, handle: Boolean) {
        if (item is BasicDrawerItem) {
            log("Menu item was clicked: '${item.title}'")
        }
    }
}
