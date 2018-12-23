package com.frostnerd.smokescreen.database

import android.content.Context
import androidx.room.Room

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */

private var INSTANCE:AppDatabase? = null

fun Context.getDatabase():AppDatabase {
    if(INSTANCE == null) {
        INSTANCE = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "data").allowMainThreadQueries().build()
    }
    return INSTANCE!!
}