package com.frostnerd.smokescreen.database.entities

import com.frostnerd.database.orm.Entity
import com.frostnerd.database.orm.annotations.Named
import com.frostnerd.database.orm.annotations.RowID
import com.frostnerd.database.orm.annotations.Table
import com.frostnerd.database.orm.annotations.ValueSerializer
import com.frostnerd.smokescreen.database.serializers.LongSerializer

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */
@Table(name = "CachedResponse")
class CachedResponse : Entity() {
    @RowID
    var rowid:Long = -1

    @Named(name="dnsName")
    lateinit var dnsName:String

    @Named(name="type")
    var type:Int = -99

    @Named(name="records")
    @ValueSerializer(usedSerializer = LongSerializer::class)
    var records:MutableMap<String, Long> = mutableMapOf()
}