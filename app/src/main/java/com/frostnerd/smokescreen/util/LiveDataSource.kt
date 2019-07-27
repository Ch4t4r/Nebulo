package com.frostnerd.smokescreen.util

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.frostnerd.cacheadapter.DataSource

/*
 * Copyright (C) 2019 Daniel Wolf (Ch4t4r)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * You can contact the developer at daniel.wolf@frostnerd.com.
 */

class LiveDataSource<T>(
    private val lifecycleOwner: LifecycleOwner,
    private val liveData: LiveData<List<T>>,
    private val reverse: Boolean = false,
    private val onInsertAtFront:(() -> Unit)? = null
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