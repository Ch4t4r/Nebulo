package com.frostnerd.smokescreen.database.repository

import com.frostnerd.smokescreen.database.dao.HostSourceDao
import com.frostnerd.smokescreen.database.entities.HostSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Copyright Daniel Wolf 2019
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 *
 * development@frostnerd.com
 */
class HostSourceRepository(private val hostSourceDao: HostSourceDao) {

    fun insertAllAsync(sources:Collection<HostSource>, coroutineScope: CoroutineScope = GlobalScope) {
        coroutineScope.launch {
            hostSourceDao.insertAll(sources)
        }
    }

    fun updateAllURLsAsync(sources:Collection<HostSource>, coroutineScope: CoroutineScope = GlobalScope) {
        coroutineScope.launch {
            sources.forEach {
                hostSourceDao.changeSourceURLByName(it.name, it.source)
            }
        }
    }
}