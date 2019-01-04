package com.frostnerd.smokescreen.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.frostnerd.smokescreen.R
import com.frostnerd.smokescreen.database.converters.StringListConverter
import com.frostnerd.smokescreen.database.entities.DnsQuery
import com.frostnerd.smokescreen.database.getDatabase
import com.frostnerd.smokescreen.getPreferences
import com.frostnerd.smokescreen.showInfoTextDialog
import org.minidns.record.Record
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Copyright Daniel Wolf 2019
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */

class QueryImportActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getPreferences().theme.dialogStyle)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialogactivity_server_import)
        supportActionBar?.hide()
        actionBar?.hide()

        if (intent != null) {
            val uri = intent.data
            if(uri != null) {
                if(uri.scheme == "content" || uri.scheme == "file") {
                    val resolver = contentResolver
                    val inputStream = resolver.openInputStream(uri)

                    val queries = BufferedReader(InputStreamReader(inputStream)).useLines {
                        val queries = mutableListOf<DnsQuery>()
                        val iterator = it.iterator()
                        if(iterator.hasNext()) {
                            iterator.next()
                            val converter = StringListConverter()
                            for(line in iterator) {
                                val split = line.split(",")
                                queries.add(DnsQuery(
                                    name=split[0],
                                    type =  Record.TYPE.getType(split[3].toInt()),
                                    askedServer = split[4],
                                    fromCache = split[5].toBoolean(),
                                    questionTime = split[6].toLong(),
                                    responseTime = split[7].toLong(),
                                    responses = converter.stringToList(split[8].replaceFirst("\"", "").replace(Regex("\"$"), ""))
                                ))
                            }
                        }
                        queries
                    }
                    val repo = getDatabase().dnsQueryRepository()
                    repo.insertAllAsync(queries)
                    showInfoTextDialog(this, "${queries.size} Queries imported.", "No text...")
                }
            }
        }
    }
}