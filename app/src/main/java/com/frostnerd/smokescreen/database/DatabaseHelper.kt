package com.frostnerd.smokescreen.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.frostnerd.database.orm.Entity
import com.frostnerd.smokescreen.database.entities.CachedResponse
import com.frostnerd.smokescreen.database.entities.UserServerConfiguration

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */

class DatabaseHelper private constructor(context: Context) :
    com.frostnerd.database.DatabaseHelper(context, NAME, VERSION, ENTITIES) {
    companion object {
        val NAME = "data"
        val VERSION = 2
        val ENTITIES: Set<Class<out Entity>> = setOf(UserServerConfiguration::class.java, CachedResponse::class.java)
        private var INSTANCE: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            if (INSTANCE == null) {
                INSTANCE = DatabaseHelper(context)
            }
            return INSTANCE!!
        }
    }

    override fun onBeforeUpgrade(p0: SQLiteDatabase?, p1: Int, p2: Int) {
    }

    override fun onAfterUpgrade(p0: SQLiteDatabase?, p1: Int, p2: Int) {
    }

    override fun onAfterCreate(p0: SQLiteDatabase?) {
    }

    override fun onBeforeCreate(p0: SQLiteDatabase?) {
    }
}

fun Context.getDatabase(): DatabaseHelper {
    return DatabaseHelper.getInstance(this)
}