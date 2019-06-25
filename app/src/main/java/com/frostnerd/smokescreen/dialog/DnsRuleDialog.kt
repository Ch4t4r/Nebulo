package com.frostnerd.smokescreen.dialog

import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.database.entities.DnsRule
import com.frostnerd.smokescreen.getPreferences
import kotlinx.android.synthetic.main.dialog_create_dnsrule.view.*
import kotlinx.android.synthetic.main.dialog_create_dnsrule.view.ipv4Address
import org.minidns.record.Record
import java.lang.Exception
import java.net.Inet4Address
import java.net.Inet6Address

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
    init {
        val view = layoutInflater.inflate(R.layout.dialog_create_dnsrule, null, false)
        setView(view)
        setTitle(R.string.dialog_newdnsrule_title)
        setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(R.string.cancel)) { dialog, _ ->
            dialog.dismiss()
        }
        setButton(DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok)) { _, _ ->

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
                        !view.ipv4Address.text.isNullOrBlank() && !view.ipv6Address.text.isNullOrBlank() -> {
                            Record.TYPE.ANY
                        }
                        !view.ipv4Address.text.isNullOrBlank() -> Record.TYPE.A
                        else -> Record.TYPE.AAAA
                    }
                    val primaryTarget = when (type) {
                        Record.TYPE.A, Record.TYPE.ANY -> view.ipv4Address.text.toString()
                        else -> view.ipv6Address.text.toString()
                    }
                    val secondaryTarget = when (type) {
                        Record.TYPE.AAAA, Record.TYPE.ANY -> view.ipv6Address.text.toString()
                        else -> null
                    }
                    val newRule = dnsRule?.copy(
                        type = type,
                        host = view.host.text.toString(),
                        target = primaryTarget,
                        ipv6Target = secondaryTarget
                    ) ?: DnsRule(Record.TYPE.A, view.host.text.toString(), view.ipv4Address.text.toString())
                    onRuleCreated(
                        newRule
                    )
                }
            }
        }
        if (dnsRule != null) {
            view.host.setText(dnsRule.host)
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