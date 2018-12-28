package com.frostnerd.smokescreen.database.repository

import androidx.room.Insert
import com.frostnerd.smokescreen.database.dao.DnsQueryDao
import com.frostnerd.smokescreen.database.entities.DnsQuery
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

class DnsQueryRepository(val dnsQueryDao: DnsQueryDao) {

    fun updateAsync(dnsQuery: DnsQuery): Job {
        return GlobalScope.launch {
            dnsQueryDao.update(dnsQuery)
        }
    }

    fun insertAllAsync(dnsQueries:List<DnsQuery>): Job {
        return GlobalScope.launch {
            dnsQueryDao.insertAll(dnsQueries)
        }
    }

    fun insertAsync(dnsQuery:DnsQuery): Job {
        return GlobalScope.launch {
            dnsQueryDao.insert(dnsQuery)
        }
    }

    suspend fun getAllAsync(coroutineScope: CoroutineScope): List<DnsQuery> {
        return coroutineScope.async(start = CoroutineStart.DEFAULT) {
            dnsQueryDao.getAll()
        }.await()
    }
}