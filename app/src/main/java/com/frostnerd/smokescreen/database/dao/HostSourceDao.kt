package com.frostnerd.smokescreen.database.dao

import androidx.room.*
import com.frostnerd.smokescreen.database.entities.HostSource

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
interface HostSourceDao {

    @Query("SELECT * FROM HostSource WHERE id=:sourceId")
    fun findById(sourceId: Long):HostSource?

    @Insert
    fun insert(hostSource: HostSource):Long

    @Insert
    fun insertAll(hostSources: Collection<HostSource>)

    @Update
    fun update(hostSource: HostSource)

    @Query("UPDATE HostSource SET enabled=:enabled WHERE id=:sourceId")
    fun setSourceEnabled(sourceId:Long, enabled:Boolean)

    @Delete
    fun delete(hostSource: HostSource)

    @Query("SELECT * FROM HostSource ORDER BY name ASC")
    fun getAll(): List<HostSource>

    @Query("SELECT * FROM HostSource WHERE enabled > 0 ORDER BY whitelistSource DESC")
    fun getAllEnabled(): List<HostSource>

    @Query("SELECT COUNT(*) FROM HostSource")
    fun getCount(): Long

    @Query("SELECT COUNT(*) FROM HostSource WHERE enabled > 0")
    fun getEnabledCount(): Long

    @Query("UPDATE HostSource SET checksum=NULL WHERE checksum IS NOT NULL AND enabled<1")
    fun removeChecksumForDisabled()
}