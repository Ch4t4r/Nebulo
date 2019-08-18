package com.frostnerd.smokescreen.database

import android.content.Context
import android.util.Base64
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.Fragment
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.frostnerd.smokescreen.Logger
import org.minidns.record.Record
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.lang.IllegalStateException

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

private var INSTANCE: AppDatabase? = null
@VisibleForTesting
val MIGRATION_2_X = migration(2) {
    Logger.logIfOpen("DB_MIGRATION", "Migrating from 2 to the current version (${AppDatabase.currentVersion}")
    it.execSQL("DROP TABLE CachedResponse")
    it.execSQL("CREATE TABLE CachedResponse(type INTEGER NOT NULL, dnsName TEXT NOT NULL, records TEXT NOT NULL, PRIMARY KEY(dnsName, type))")
    it.execSQL("DROP TABLE IF EXISTS UserServerConfiguration")
    MIGRATION_3_4.migrate(it)
    MIGRATION_5_6.migrate(it)
    Logger.logIfOpen("DB_MIGRATION", "Migration from 2 to current version completed")
}
@VisibleForTesting
val MIGRATION_3_4 = migration(3, 4) {
    Logger.logIfOpen("DB_MIGRATION", "Migrating from 3 to 4")
    it.execSQL("CREATE TABLE IF NOT EXISTS `DnsQuery` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL, `name` TEXT NOT NULL, `askedServer` TEXT, `fromCache` INTEGER NOT NULL, `questionTime` INTEGER NOT NULL, `responseTime` INTEGER NOT NULL, `responses` TEXT NOT NULL)")
    Logger.logIfOpen("DB_MIGRATION", "Migration from 3 to 4 completed")
}
@VisibleForTesting
val MIGRATION_4_5 = migration(4, 5) {
    Logger.logIfOpen("DB_MIGRATION", "Migrating from 4 to 5")
    it.execSQL("DROP TABLE IF EXISTS UserServerConfiguration")
    Logger.logIfOpen("DB_MIGRATION", "Migration from 4 to 5 completed")
}
@VisibleForTesting
val MIGRATION_5_6 = migration(5, 6) {
    Logger.logIfOpen("DB_MIGRATION", "Migrating from 5 to 6")
    it.execSQL("CREATE TABLE IF NOT EXISTS `DnsRule` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `stagingType` INTEGER NOT NULL, `type` INTEGER NOT NULL, `host` TEXT NOT NULL, `target` TEXT NOT NULL, `ipv6Target` TEXT, `importedFrom` INTEGER, FOREIGN KEY(`importedFrom`) REFERENCES `HostSource`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
    it.execSQL("CREATE TABLE IF NOT EXISTS `HostSource` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `enabled` INTEGER NOT NULL, `name` TEXT NOT NULL, `source` TEXT NOT NULL)")
    it.execSQL("CREATE INDEX IF NOT EXISTS `index_DnsRule_importedFrom` ON `DnsRule` (`importedFrom`)")
    it.execSQL("CREATE UNIQUE INDEX `index_DnsRule_host_type_stagingType` ON `DnsRule` (`host`, `type`, `stagingType`)")
    Logger.logIfOpen("DB_MIGRATION", "Migration from 5 to 6 completed")
}
@VisibleForTesting
val MIGRATION_6_7 = migration(6,7) {
    Logger.logIfOpen("DB_MIGRATION", "Migrating from 6 to 7")
    it.execSQL("DROP INDEX IF EXISTS `index_DnsRule_host`")
    it.execSQL("DROP INDEX IF EXISTS `index_DnsRule_host_type`")
    it.execSQL("DELETE FROM `DnsRule`")
    it.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_DnsRule_host_type_stagingType` ON `DnsRule` (`host`, `type`, `stagingType`)")
    Logger.logIfOpen("DB_MIGRATION", "Migration from 6 to 7 completed")
}
@VisibleForTesting
val MIGRATION_7_8 = migration(7,8) {
    Logger.logIfOpen("DB_MIGRATION", "Migrating from 7 to 8")
    it.execSQL("DROP TABLE `DnsRule`")
    it.execSQL("CREATE TABLE IF NOT EXISTS `DnsRule` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `stagingType` INTEGER NOT NULL, `type` INTEGER NOT NULL, `host` TEXT NOT NULL, `target` TEXT NOT NULL, `ipv6Target` TEXT, `importedFrom` INTEGER, FOREIGN KEY(`importedFrom`) REFERENCES `HostSource`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
    it.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_DnsRule_host_type_stagingType` ON `DnsRule` (`host`, `type`, `stagingType`)")
    it.execSQL("CREATE INDEX IF NOT EXISTS `index_DnsRule_importedFrom` ON `DnsRule` (`importedFrom`)")
    Logger.logIfOpen("DB_MIGRATION", "Migration from 7 to 8 completed")
}
@VisibleForTesting
val MIGRATION_8_9 = migration(8, 9) {
    Logger.logIfOpen("DB_MIGRATION", "Migrating from 8 to 9")
    it.execSQL("ALTER TABLE `HostSource` ADD COLUMN `whitelistSource` INTEGER NOT NULL DEFAULT 0")
    it.execSQL("ALTER TABLE `HostSource` ADD COLUMN `ruleCount` INTEGER")
    Logger.logIfOpen("DB_MIGRATION", "Migration from 8 to 9 completed")
}
@VisibleForTesting
val MIGRATION_9_10 = migration(9, 10) {
    Logger.logIfOpen("DB_MIGRATION", "Migrating from 9 to 10")
    it.execSQL("ALTER TABLE `DnsRule` ADD COLUMN `isWildcard` INTEGER NOT NULL DEFAULT 0")
    it.execSQL("ALTER TABLE `HostSource` ADD COLUMN `checksum` TEXT DEFAULT NULL")
    Logger.logIfOpen("DB_MIGRATION", "Migration from 9 to 10 completed")
}

val MIGRATION_10_11 = migration(10, 11) {
    Logger.logIfOpen("DB_MIGRATION", "Migrating from 10 to 11")
    it.execSQL("CREATE TABLE `DnsQuery_tmp` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL, `name` TEXT NOT NULL, `askedServer` TEXT, `responseSource` TEXT NOT NULL, `questionTime` INTEGER NOT NULL, `responseTime` INTEGER NOT NULL, `responses` TEXT NOT NULL)")
    it.execSQL("INSERT INTO `DnsQuery_tmp`(id, type, name, askedServer, questionTime, responseTime, responses, responseSource) SELECT id, type, name, askedServer, questionTime, responseTime, responses, CASE WHEN fromCache=1 THEN 'CACHE' else 'UPSTREAM' END as `responseSource` FROM DnsQuery")
    it.execSQL("DROP TABLE `DnsQuery`")
    it.execSQL("ALTER TABLE `DnsQuery_tmp` RENAME TO `DnsQuery`")
    Logger.logIfOpen("DB_MIGRATION", "Migration from 10 to 11 completed")
}


fun Context.getDatabase(): AppDatabase {
    if (INSTANCE == null) {
        INSTANCE = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "data")
            .allowMainThreadQueries()
            .addMigrations(MIGRATION_2_X, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
            .build()
    }
    return INSTANCE!!
}

fun Fragment.getDatabase():AppDatabase {
    return context!!.getDatabase()
}

private fun migration(
    from: Int,
    to: Int = AppDatabase.currentVersion,
    migrate: (database: SupportSQLiteDatabase) -> Unit
): Migration {
    if(from < 0 || to >AppDatabase.currentVersion || from > to) throw IllegalStateException("Version out of bounds $from->$to with bounds 0 -- ${AppDatabase.currentVersion}")
    return object : Migration(from, to) {
        override fun migrate(database: SupportSQLiteDatabase) {
            migrate.invoke(database)
        }
    }
}

private fun emptyMigration(from: Int, to: Int = AppDatabase.currentVersion): Migration {
    return migration(from, to) { }
}

fun recordFromBase64(base64: String): Record<*> {
    val bytes = Base64.decode(base64, Base64.NO_WRAP)
    return Record.parse(DataInputStream(ByteArrayInputStream(bytes)), bytes)
}