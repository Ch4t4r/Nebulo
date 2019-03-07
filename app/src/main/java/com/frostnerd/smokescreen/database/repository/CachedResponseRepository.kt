package com.frostnerd.smokescreen.database.repository

import androidx.room.Insert
import com.frostnerd.smokescreen.database.dao.CachedResponseDao
import com.frostnerd.smokescreen.database.entities.CachedResponse
import kotlinx.coroutines.*

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