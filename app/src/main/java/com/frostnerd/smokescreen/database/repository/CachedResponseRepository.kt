package com.frostnerd.smokescreen.database.repository

import androidx.room.Insert
import com.frostnerd.smokescreen.database.dao.CachedResponseDao
import com.frostnerd.smokescreen.database.entities.CachedResponse
import kotlinx.coroutines.*

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */

class CachedResponseRepository(val cachedResponseDao: CachedResponseDao) {
    @Insert
    suspend fun insertAll(cachedResponses:List<CachedResponse>): Job {
        return GlobalScope.launch {
            cachedResponseDao.insertAll(cachedResponses)
        }
    }

    suspend fun getAllAsync(coroutineScope: CoroutineScope): List<CachedResponse> {
        return coroutineScope.async(start = CoroutineStart.DEFAULT) {
            cachedResponseDao.getAll()
        }.await()
    }
}