package com.frostnerd.smokescreen.fragment

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.frostnerd.smokescreen.*
import com.frostnerd.smokescreen.database.AppDatabase
import com.frostnerd.smokescreen.dialog.ChangelogDialog
import com.frostnerd.smokescreen.dialog.LicensesDialog
import com.frostnerd.smokescreen.dialog.QueryGeneratorDialog
import com.frostnerd.smokescreen.util.preferences.Crashreporting
import kotlinx.android.synthetic.main.fragment_about.view.*
import java.util.*

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
class AboutFragment : Fragment() {
    private var queryGenStepOne = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.credits.setOnClickListener {
            showInfoTextDialog(
                requireContext(),
                getString(R.string.dialog_credits_title),
                getString(R.string.dialog_credits_message)
            )
        }
        view.privacyPolicy.setOnClickListener {
            showPrivacyPolicyDialog(requireContext())
        }
        if(isPackageInstalled(requireContext(), "org.telegram.messenger")) {
            view.group.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://join?invite=I54nRleveRGP8IPmcIdySg"))
                startActivity(intent)
            }
        } else {
            view.group.visibility = View.GONE
        }
        view.contact.setOnClickListener {
            requireContext().showEmailChooser(
                getString(R.string.about_contact_developer),
                getString(R.string.app_name),
                getString(R.string.support_contact_mail),
                "\n\n\n\n\n\nSystem:\nApp version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE};${BuildConfig.COMMIT_HASH})\n" +
                        "Android: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE} - ${Build.VERSION.CODENAME})"
            )
        }
        view.share.setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
            intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.app_share_text))
            startActivity(Intent.createChooser(intent, getString(R.string.about_share)))
        }
        view.changelog.setOnClickListener {
            ChangelogDialog(requireContext(), 22, showOptOut = false, showInfoText = false).show()
        }
        view.licenses.setOnClickListener {
            LicensesDialog(requireContext()).show()
        }
        view.about.setOnClickListener {
            showInfoTextDialog(
                requireContext(),
                getString(R.string.menu_about),
                getString(
                    R.string.about_app,
                    BuildConfig.VERSION_NAME + (if (BuildConfig.DEBUG) " DEBUG" else "") + " (Commit ${BuildConfig.COMMIT_HASH})",
                    BuildConfig.VERSION_CODE,
                    AppDatabase.currentVersion,
                    if (requireContext().getPreferences().crashreportingType == Crashreporting.FULL) requireContext().getPreferences().crashReportingUUID else "---"
                )
            )
        }
        val languageCode = Locale.getDefault().toString()
        if (!resources.getStringArray(R.array.missing_languages).any {
                languageCode.startsWith(it)
            }) view.translating.visibility = View.GONE
        else view.translating.setOnClickListener {
            showInfoTextDialog(requireContext(), getString(R.string.about_help_translating),
                getString(R.string.dialog_help_translating_message),
                neutralButton = getString(R.string.all_close) to null)
        }
        view.privacyPolicy.setOnLongClickListener {
            queryGenStepOne = true
            true
        }
        view.about.setOnLongClickListener {
            if(queryGenStepOne) QueryGeneratorDialog(requireContext())
            true
        }
    }
}