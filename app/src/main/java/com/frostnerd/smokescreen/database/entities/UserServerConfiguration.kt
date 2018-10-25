package com.frostnerd.smokescreen.database.entities

import com.frostnerd.database.orm.Entity
import com.frostnerd.database.orm.annotations.RowID
import com.frostnerd.database.orm.annotations.Table
import com.frostnerd.encrypteddnstunnelproxy.ServerConfiguration

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */
@Table(name = "UserServerConfiguration")
class UserServerConfiguration():Entity() {
    @RowID
    var rowid:Long = -1
    lateinit var name:String
    lateinit var primaryServerUrl:String
    var secondaryServerUrl:String? = null

    constructor(name:String, primaryServerUrl:String, secondaryServerUrl:String?):this() {
        this.name = name
        this.primaryServerUrl = primaryServerUrl
        this.secondaryServerUrl = secondaryServerUrl
    }

    fun createPrimaryServerConfiguration():ServerConfiguration = ServerConfiguration.createSimpleServerConfig(primaryServerUrl)
    fun createSecondaryServerConfiguration():ServerConfiguration? {
        return if(secondaryServerUrl != null) {
            ServerConfiguration.createSimpleServerConfig(secondaryServerUrl!!)
        } else null
    }
}