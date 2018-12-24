package com.frostnerd.smokescreen.database.repository

import androidx.room.Insert
import com.frostnerd.smokescreen.database.dao.CachedResponseDao
import com.frostnerd.smokescreen.database.dao.UserServerConfigurationDao
import com.frostnerd.smokescreen.database.entities.CachedResponse
import com.frostnerd.smokescreen.database.entities.UserServerConfiguration
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

class UserServerConfigurationRepository(val userServerConfigurationDao: UserServerConfigurationDao) {
    @Insert
    suspend fun insertAll(userConfigurations:List<UserServerConfiguration>): Job {
        return GlobalScope.launch {
            userServerConfigurationDao.insertAll(userConfigurations)
        }
    }

    suspend fun getAllAsync(coroutineScope: CoroutineScope): List<UserServerConfiguration> {
        return coroutineScope.async(start = CoroutineStart.DEFAULT) {
            userServerConfigurationDao.getAll()
        }.await()
    }
}