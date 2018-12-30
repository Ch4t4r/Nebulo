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
    it.execSQL("DROP TABLE UserServerConfiguration")
    MIGRATION_3_4.migrate(it)
}
private val MIGRATION_3_4 = migration(3, 4) {
    it.execSQL("CREATE TABLE IF NOT EXISTS `DnsQuery` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL, `name` TEXT NOT NULL, `askedServer` TEXT, `fromCache` INTEGER NOT NULL, `questionTime` INTEGER NOT NULL, `responseTime` INTEGER NOT NULL, `responses` TEXT NOT NULL)")
}
private val MIGRATION_4_5 = migration(4,5) {
    it.execSQL("DROP TABLE UserServerConfiguration")
}


fun Context.getDatabase(): AppDatabase {
    if (INSTANCE == null) {
        INSTANCE = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "data")
            .allowMainThreadQueries()
            .addMigrations(MIGRATION_2_X, MIGRATION_3_4, MIGRATION_4_5)
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