package com.frostnerd.smokescreen.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.frostnerd.smokescreen.database.dao.CachedResponseDao
import com.frostnerd.smokescreen.database.dao.DnsQueryDao
import com.frostnerd.smokescreen.database.entities.CachedResponse
import com.frostnerd.smokescreen.database.entities.DnsQuery
import com.frostnerd.smokescreen.database.repository.CachedResponseRepository
import com.frostnerd.smokescreen.database.repository.DnsQueryRepository

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

@Database(entities = [CachedResponse::class, DnsQuery::class], version = AppDatabase.currentVersion)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        const val currentVersion:Int = 5
    }

    abstract fun cachedResponseDao(): CachedResponseDao
    abstract fun dnsQueryDao():DnsQueryDao

    fun cachedResponseRepository() = CachedResponseRepository(cachedResponseDao())
    fun dnsQueryRepository() = DnsQueryRepository(dnsQueryDao())
}