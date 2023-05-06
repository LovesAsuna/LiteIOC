package com.hyosakura.liteioc.util

/**
 * @author LovesAsuna
 **/
interface MultiValueMap<K, V> : MutableMap<K, MutableList<V?>> {

    fun getFirst(key: K): V?

    fun add(key: K, value: V?)

    fun addAll(key: K, values: MutableList<V?>)

    fun addAll(values: MultiValueMap<K, V>)

    fun addIfAbsent(key: K, value: V?) {
        if (!containsKey(key)) {
            add(key, value)
        }
    }

    fun set(key: K, value: V?)

    fun setAll(values: Map<K, V>)

    fun toSingleValueMap(): Map<K, V?>

}