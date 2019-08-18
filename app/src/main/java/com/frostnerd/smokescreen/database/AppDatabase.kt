package com.frostnerd.smokescreen.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.frostnerd.smokescreen.database.dao.CachedResponseDao
import com.frostnerd.smokescreen.database.dao.DnsQueryDao
import com.frostnerd.smokescreen.database.dao.DnsRuleDao
import com.frostnerd.smokescreen.database.dao.HostSourceDao
import com.frostnerd.smokescreen.database.entities.CachedResponse
import com.frostnerd.smokescreen.database.entities.DnsQuery
import com.frostnerd.smokescreen.database.entities.DnsRule
import com.frostnerd.smokescreen.database.entities.HostSource
import com.frostnerd.smokescreen.database.repository.CachedResponseRepository
import com.frostnerd.smokescreen.database.repository.DnsQueryRepository
import com.frostnerd.smokescreen.database.repository.DnsRuleRepository
import com.frostnerd.smokescreen.database.repository.HostSourceRepository

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

@Database(entities = [CachedResponse::class, DnsQuery::class, DnsRule::class, HostSource::class], version = AppDatabase.currentVersion)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        const val currentVersion:Int = 11
    }

    abstract fun cachedResponseDao(): CachedResponseDao
    abstract fun dnsQueryDao():DnsQueryDao
    abstract fun dnsRuleDao():DnsRuleDao
    abstract fun hostSourceDao():HostSourceDao

    fun cachedResponseRepository() = CachedResponseRepository(cachedResponseDao())
    fun dnsQueryRepository() = DnsQueryRepository(dnsQueryDao())
    fun dnsRuleRepository() = DnsRuleRepository(dnsRuleDao())
    fun hostSourceRepository() = HostSourceRepository(hostSourceDao())

    fun recreateDnsRuleIndizes() {
        this.openHelper.writableDatabase.apply {
            runInTransaction {
                execSQL("DROP INDEX `index_DnsRule_importedFrom`")
                execSQL("DROP INDEX `index_DnsRule_host_type_stagingType`")

                execSQL("CREATE  INDEX `index_DnsRule_importedFrom` ON `DnsRule` (`importedFrom`)")
                execSQL("CREATE UNIQUE INDEX `index_DnsRule_host_type_stagingType` ON `DnsRule` (`host`, `type`, `stagingType`)")
            }
        }
    }
}