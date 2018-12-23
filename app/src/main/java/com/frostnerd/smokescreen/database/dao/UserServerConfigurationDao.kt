package com.frostnerd.smokescreen.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.frostnerd.smokescreen.database.entities.UserServerConfiguration

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
interface UserServerConfigurationDao {
    @Query("SELECT * FROM UserServerConfiguration")
    fun getAll(): List<UserServerConfiguration>

    @Insert
    fun insertAll(vararg configurations: UserServerConfiguration)

    @Insert
    fun insert(configuration: UserServerConfiguration)

    @Delete
    fun delete(user: UserServerConfiguration)
}