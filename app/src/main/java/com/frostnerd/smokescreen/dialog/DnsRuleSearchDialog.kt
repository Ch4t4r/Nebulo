package com.frostnerd.smokescreen.dialog

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.database.entities.DnsRule
import com.frostnerd.smokescreen.database.entities.HostSource
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.getPreferences
import kotlinx.android.synthetic.main.dialog_dnsrule_search.view.*
import kotlinx.coroutines.*
import org.minidns.record.Record
import kotlin.coroutines.CoroutineContext

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
class DnsRuleSearchDialog(
    context: Context
):AlertDialog(context, context.getPreferences().theme.dialogStyle), CoroutineScope {
    private val supervisor = SupervisorJob()
    override val coroutineContext: CoroutineContext = supervisor + Dispatchers.IO
    var currentSearchJob:Job? = null

    private val watcher = object :TextWatcher{
        private var previousSearch = ""

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            val search = s.toString().trim()
            if (search == previousSearch)
                return
            previousSearch = search

            currentSearchJob?.cancel()
            if(search.isNotBlank()) currentSearchJob = this@DnsRuleSearchDialog.launch {
                delay(500) // debounce
                if (search != previousSearch)
                    return@launch

                val rule = findRuleForSearch(search)
                val hostSource = rule?.importedFrom?.let { getHostSourceForId(it) }
                if(isActive) launch(Dispatchers.Main) {
                    if(rule == null) displayRuleSource(wasFound = false, isUserRule = false, null)
                    else {
                        displayRuleSource(wasFound = true, isUserRule = rule.importedFrom == null, hostSource)
                        val readableHost = DnsRuleDialog.printableHost(rule.host)
                        if(readableHost != rule.host) {
                            view.searchResultRule.text = readableHost
                        }
                    }
                }
            }
            else clearSearchResultText()
        }

        override fun afterTextChanged(s: Editable?) = Unit
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    }
    val view: View

    init {
        view = layoutInflater.inflate(R.layout.dialog_dnsrule_search, null, false)
        setTitle(R.string.dialog_dnsrules_search_title)
        setView(view)
        setButton(BUTTON_POSITIVE, context.getText(R.string.all_close)) {_,_ ->
            supervisor.cancel()
        }
        view.searchTerm.addTextChangedListener(watcher)
        setOnDismissListener {
            supervisor.cancel()
        }
    }

    private fun findRuleForSearch(searchTerm: String): DnsRule? {
        var source: DnsRule? = null

        if (searchTerm.startsWith("www", ignoreCase = true)) {
            source = context.getDatabase().dnsRuleDao().findRuleTargetEntity(
                searchTerm.replaceFirst("www.", "", ignoreCase = true),
                Record.TYPE.ANY,
                true
            )
        }
        source = source ?: context.getDatabase().dnsRuleDao()
            .findRuleTargetEntity(searchTerm, Record.TYPE.ANY, true)

        return source ?: context.getDatabase().dnsRuleDao().findPossibleWildcardRuleTarget(
            searchTerm, type = Record.TYPE.ANY,
            useUserRules = true,
            includeWhitelistEntries = false,
            includeNonWhitelistEntries = true
        ).firstOrNull {
            DnsRuleDialog.databaseHostToMatcher(it.host).reset(searchTerm).matches()
        }
    }

    private fun getHostSourceForId(id:Long):HostSource? {
        return context.getDatabase().hostSourceDao().findById(id)
    }
    
    private fun displayRuleSource(wasFound:Boolean, isUserRule:Boolean, hostSource: HostSource?) {
        val text = if(wasFound) {
            if(isUserRule) {
                context.getString(R.string.dialog_dnsrules_status_userrule)
            } else {
                if(hostSource == null) {
                    context.getString(R.string.dialog_dnsrules_status_sourcenotfound)
                } else {
                    context.getString(R.string.dialog_dnsrules_status_fromsource, hostSource.name)
                }
            }
        } else {
            context.getString(R.string.dialog_dnsrules_status_not_found)
        }
        view.searchResult.text = text
    }

    private fun clearSearchResultText() {
        view.searchResult.text = ""
        view.searchResultRule.text = ""
    }
}