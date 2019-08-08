package com.frostnerd.smokescreen.dialog

import android.content.Context
import android.content.DialogInterface
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.database.entities.DnsRule
import com.frostnerd.smokescreen.getPreferences
import kotlinx.android.synthetic.main.dialog_create_dnsrule.view.*
import org.minidns.record.Record
import java.net.Inet4Address
import java.net.Inet6Address
import java.util.regex.Matcher

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
class DnsRuleDialog(context: Context, dnsRule: DnsRule? = null, onRuleCreated: (DnsRule) -> Unit) :
    AlertDialog(context, context.getPreferences().theme.dialogStyle) {
    private var isWhitelist = false
    companion object {
        private val matchers = mutableMapOf<String, Matcher>()

        fun printableHost(host:String): String {
            return host.replace("%%", "**").replace("%", "*")
        }

        fun databaseHostToMatcher(host:String):Matcher {
            return matchers.getOrPut(host, {
                host.replace("%%", ".*").replace("%", "[^.]*").toPattern().matcher("")
            })
        }
    }

    init {
        val view = layoutInflater.inflate(R.layout.dialog_create_dnsrule, null, false)
        setView(view)
        setTitle(if(dnsRule == null) R.string.dialog_newdnsrule_title else R.string.dialog_newdnsrule_edit)
        setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(android.R.string.cancel)) { dialog, _ ->
            dialog.dismiss()
        }
        setButton(DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok)) { _, _ ->

        }
        setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.dialog_newdnsrule_whitelist)) { _, _ ->

        }
        setOnShowListener {
            getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                var valid = true
                view.host.error = null
                view.ipv4Til.error = null
                view.ipv6Til.error = null
                if (view.host.text.isNullOrBlank()) {
                    view.host.error = context.getString(R.string.dialog_newdnsrule_host_invalid)
                    valid = false
                } else {
                    val ipv4Valid = isIpv4Address(view.ipv4Address.text.toString())
                    val ipv6Valid = isIpv6Address(view.ipv6Address.text.toString())
                    val bothEmpty = view.ipv4Address.text.isNullOrEmpty() && view.ipv6Address.text.isNullOrEmpty()
                    if (!ipv4Valid || bothEmpty) {
                        valid = false
                        view.ipv4Address.error = context.getString(R.string.dialog_newdnsrule_ipv4_invalid)
                    }
                    if (!ipv6Valid || bothEmpty) {
                        valid = false
                        view.ipv6Address.error = context.getString(R.string.dialog_newdnsrule_ipv6_invalid)
                    }
                }
                if (valid) {
                    dismiss()
                    val type = when {
                        isWhitelist -> Record.TYPE.ANY
                        !view.ipv4Address.text.isNullOrBlank() && !view.ipv6Address.text.isNullOrBlank() -> {
                            Record.TYPE.ANY
                        }
                        !view.ipv4Address.text.isNullOrBlank() -> Record.TYPE.A
                        else -> Record.TYPE.AAAA
                    }
                    val primaryTarget = if (isWhitelist) {
                        ""
                    } else when (type) {
                        Record.TYPE.A, Record.TYPE.ANY -> view.ipv4Address.text.toString()
                        else -> view.ipv6Address.text.toString()
                    }
                    val secondaryTarget = if (isWhitelist) {
                        null
                    } else when (type) {
                        Record.TYPE.AAAA, Record.TYPE.ANY -> view.ipv6Address.text.toString()
                        else -> null
                    }
                    var isWildcard = false
                    val host = view.host.text.toString().let {
                        if(it.contains("*")) {
                            isWildcard = true
                            it.replace("**", "%%").replace("*", "%")
                        } else it.replace(Regex("^www\\."), "")
                    }

                    val newRule = dnsRule?.copy(
                        type = type,
                        host = host,
                        target = primaryTarget,
                        ipv6Target = secondaryTarget,
                        isWildcard = isWildcard
                    ) ?: DnsRule(type, host, primaryTarget, secondaryTarget, isWildcard = isWildcard)
                    onRuleCreated(
                        newRule
                    )
                }
            }
            getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                isWhitelist = !isWhitelist
                getButton(DialogInterface.BUTTON_NEUTRAL).text =
                    if (isWhitelist) context.getString(R.string.dialog_newdnsrule_specify_address)
                    else context.getString(R.string.dialog_newdnsrule_whitelist)

                val visibility = if (isWhitelist) View.GONE else View.VISIBLE
                view.ipv4Til.visibility = visibility
                view.ipv6Til.visibility = visibility
            }
            if (dnsRule != null) {
                if (dnsRule.isWhitelistRule()) {
                    isWhitelist = true
                    view.host.setText(printableHost(dnsRule.host))
                    getButton(DialogInterface.BUTTON_NEUTRAL).text =
                        context.getString(R.string.dialog_newdnsrule_specify_address)
                    view.ipv4Til.visibility = View.GONE
                    view.ipv6Til.visibility = View.GONE
                } else {
                    view.host.setText(printableHost(dnsRule.host))
                    when {
                        dnsRule.type == Record.TYPE.A -> {
                            view.ipv4Address.setText(dnsRule.target)
                            view.ipv6Address.text = null
                        }
                        dnsRule.type == Record.TYPE.AAAA -> {
                            view.ipv4Address.text = null
                            view.ipv6Address.setText(dnsRule.target)
                        }
                        dnsRule.type == Record.TYPE.ANY -> {
                            view.ipv4Address.setText(dnsRule.target)
                            view.ipv6Address.setText(dnsRule.ipv6Target)
                        }
                    }
                }
            }
        }
    }

    private fun isIpv4Address(text: String?): Boolean {
        return text.isNullOrBlank() || try {
            Inet4Address.getByName(text)
            true
        } catch (ex: Exception) {
            false
        }
    }

    private fun isIpv6Address(text: String?): Boolean {
        return text.isNullOrBlank() || try {
            Inet6Address.getByName(text)
            true
        } catch (ex: Exception) {
            false
        }
    }
}