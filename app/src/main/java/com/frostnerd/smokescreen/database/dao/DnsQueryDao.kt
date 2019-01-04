package com.frostnerd.smokescreen.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.frostnerd.smokescreen.database.entities.DnsQuery

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
interface DnsQueryDao {
    @Query("SELECT * FROM DnsQuery")
    fun getAll(): List<DnsQuery>

    @Query("SELECT * FROM DnsQuery")
    fun getAllLive(): LiveData<List<DnsQuery>>

    @Query("SELECT MAX(id) FROM DnsQuery")
    fun getLastInsertedId():Long

    @Query("SELECT COUNT(*) FROM DnsQuery")
    fun getCount():Int

    @Insert
    fun insertAll(vararg dnsQueries: DnsQuery)

    @Insert
    fun insertAll(dnsQueries: List<DnsQuery>)

    @Insert
    fun insert(dnsQuery: DnsQuery)

    @Update
    fun update(dnsQuery: DnsQuery)

    @Delete
    fun delete(dnsQuery: DnsQuery)

    @Query("DELETE FROM DnsQuery")
    fun deleteAll()
}