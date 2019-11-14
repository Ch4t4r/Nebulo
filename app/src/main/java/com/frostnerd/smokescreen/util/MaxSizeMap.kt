package com.frostnerd.smokescreen.util

class MaxSizeMap<K, V>(val maxSize:Int, initialCapacity:Int) :LinkedHashMap<K, V>(initialCapacity) {

    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
        return size > maxSize
    }
}