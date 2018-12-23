package com.frostnerd.smokescreen.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.frostnerd.smokescreen.database.dao.CachedResponseDao
import com.frostnerd.smokescreen.database.dao.UserServerConfigurationDao
import com.frostnerd.smokescreen.database.entities.CachedResponse
import com.frostnerd.smokescreen.database.entities.UserServerConfiguration
import com.frostnerd.smokescreen.database.repository.CachedResponseRepository

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */

@Database(entities = [UserServerConfiguration::class, CachedResponse::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userServerConfigurationDao(): UserServerConfigurationDao
    abstract fun cachedResponseDao(): CachedResponseDao

    fun cachedResponseRepository() = CachedResponseRepository(cachedResponseDao())
}