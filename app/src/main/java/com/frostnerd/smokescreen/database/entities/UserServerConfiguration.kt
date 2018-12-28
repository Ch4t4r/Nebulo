package com.frostnerd.smokescreen.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.frostnerd.encrypteddnstunnelproxy.ServerConfiguration
import com.frostnerd.encrypteddnstunnelproxy.createSimpleServerConfig

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */

@Entity(tableName = "UserServerConfiguration")
data class UserServerConfiguration(
    @PrimaryKey(autoGenerate = true) var id: Int =0,
    @ColumnInfo(name = "name") var name: String,
    @ColumnInfo(name = "primaryServerUrl") var primaryServerUrl: String,
    @ColumnInfo(name = "secondaryServerUrl") var secondaryServerUrl: String? = null
) {
    fun createPrimaryServerConfiguration(): ServerConfiguration =
        ServerConfiguration.createSimpleServerConfig(primaryServerUrl)

    fun createSecondaryServerConfiguration(): ServerConfiguration? {
        return if (secondaryServerUrl != null) {
            ServerConfiguration.createSimpleServerConfig(secondaryServerUrl!!)
        } else null
    }
}