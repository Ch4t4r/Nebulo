package com.frostnerd.smokescreen.database

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.minidns.record.Record
import java.io.ByteArrayInputStream
import java.io.DataInputStream

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */

private var INSTANCE: AppDatabase? = null
private val MIGRATION_2_X = migration(2) {
    it.execSQL("DROP TABLE CachedResponse")
    it.execSQL("CREATE TABLE CachedResponse(type INTEGER NOT NULL, dnsName TEXT NOT NULL, records TEXT NOT NULL, PRIMARY KEY(dnsName, type))")
    it.execSQL("ALTER TABLE UserServerConfiguration RENAME TO OLD_UserServerConfiguration")
    it.execSQL("CREATE TABLE UserServerConfiguration(id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, primaryServerUrl TEXT NOT NULL, secondaryServerUrl TEXT)")
    it.execSQL("INSERT INTO UserServerConfiguration SELECT ROWID as id, name, primaryServerUrl, secondaryServerUrl FROM OLD_UserServerConfiguration")
}


fun Context.getDatabase(): AppDatabase {
    if (INSTANCE == null) {
        INSTANCE = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "data")
            .allowMainThreadQueries()
            .addMigrations(MIGRATION_2_X)
            .addMigrations(emptyMigration(3, 4))
            .build()
    }
    return INSTANCE!!
}

private fun migration(from: Int, to: Int = AppDatabase.currentVersion, migrate: (database: SupportSQLiteDatabase) -> Unit): Migration {
    return object : Migration(from, to) {
        override fun migrate(database: SupportSQLiteDatabase) {
            migrate.invoke(database)
        }
    }
}

private fun emptyMigration(from:Int, to:Int = AppDatabase.currentVersion): Migration {
    return migration(from, to) { }
}

fun recordFromBase64(base64:String):Record<*> {
    val bytes = Base64.decode(base64, Base64.NO_WRAP)
    return Record.parse(DataInputStream(ByteArrayInputStream(bytes)), bytes)
}