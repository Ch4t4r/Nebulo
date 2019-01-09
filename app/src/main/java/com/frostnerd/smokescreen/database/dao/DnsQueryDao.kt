package com.frostnerd.smokescreen.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.frostnerd.smokescreen.database.entities.DnsQuery

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
@Dao
interface DnsQueryDao {
    @Query("SELECT * FROM DnsQuery")
    fun getAll(): List<DnsQuery>

    @Query("SELECT * FROM DnsQuery")
    fun getAllLive(): LiveData<List<DnsQuery>>

    @Query("SELECT MAX(id) FROM DnsQuery")
    fun getLastInsertedId():Long

    @Query("SELECT COUNT(*) FROM DnsQuery")
    fun getCount():Int

    @Insert
    fun insertAll(vararg dnsQueries: DnsQuery)

    @Insert
    fun insertAll(dnsQueries: List<DnsQuery>)

    @Insert
    fun insert(dnsQuery: DnsQuery)

    @Update
    fun update(dnsQuery: DnsQuery)

    @Delete
    fun delete(dnsQuery: DnsQuery)

    @Query("DELETE FROM DnsQuery")
    fun deleteAll()
}