package com.frostnerd.smokescreen.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.frostnerd.smokescreen.database.entities.CachedResponse

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
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