package com.frostnerd.smokescreen.util

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.frostnerd.cacheadapter.DataSource

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 * <p>
 * development@frostnerd.com
 */

class LiveDataSource<T>(
    val lifecycleOwner: LifecycleOwner,
    val liveData: LiveData<List<T>>,
    val reverse: Boolean = false,
    val onInsertAtFront:(() -> Unit)? = null
) : DataSource<T>() {
    private var data = emptyList<T>()
    fun currentSize() = data.size

    override fun loadInitialData() {
        liveData.observe(lifecycleOwner, Observer {
            if (it.size == data.size) {
                val changedRanges = mutableListOf<IntRange>()
                for ((index, item) in it.withIndex()) {
                    if (item != data[if (reverse) data.size - index - 1 else index]) {
                        var included = false
                        for (range in changedRanges) {
                            if (index + 1 == range.first) {
                                changedRanges.remove(range)
                                changedRanges.add(index..range.last)
                                included = true
                                break
                            } else if (index - 1 == range.last) {
                                changedRanges.remove(range)
                                changedRanges.add(range.first..index)
                                included = true
                                break
                            }
                        }
                        if (!included) changedRanges.add(index..index)
                    }
                }
                changedRanges.forEach { range ->
                    onItemRangeChanged(range.first, range.first - range.last + 1)
                }
            } else {
                if(reverse) onInsertAtFront?.invoke()
                onItemRangeInserted(if (reverse) 0 else data.size, it.size - data.size)
            }
            data = if (reverse) it.reversed() else it
        })
    }

    override fun cleanup() {
    }

    override suspend fun getItemAt(position: Int): T {
        return getItemAtInstant(position)
    }

    override fun getItemAtInstant(position: Int): T {
        return data[position]
    }

    override fun hasItemAt(position: Int): Boolean {
        return hasItemAtInstant(position)
    }

    override fun hasItemAtInstant(position: Int): Boolean {
        return data.size > position
    }

}