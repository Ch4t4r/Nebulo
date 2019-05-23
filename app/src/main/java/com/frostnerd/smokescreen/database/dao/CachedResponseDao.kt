package com.frostnerd.smokescreen.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.frostnerd.smokescreen.database.entities.CachedResponse

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
interface CachedResponseDao {
    @Query("SELECT * FROM CachedResponse")
    fun getAll(): List<CachedResponse>

    @Insert
    fun insertAll(vararg cachedResponses: CachedResponse)

    @Insert
    fun insertAll(cachedResponses: List<CachedResponse>)


    @Insert
    fun insert(cachedResponse: CachedResponse)

    @Delete
    fun delete(cachedResponse: CachedResponse)

    @Query("DELETE FROM CachedResponse")
    fun deleteAll()
}