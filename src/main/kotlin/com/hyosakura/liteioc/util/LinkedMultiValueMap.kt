package com.hyosakura.liteioc.util

import java.io.Serializable

/**
 * @author LovesAsuna
 **/
class LinkedMultiValueMap<K, V> : MultiValueMapAdapter<K, V>, Serializable, Cloneable {

    constructor() : super(LinkedHashMap())

    constructor(expectedSize: Int) : super(LinkedHashMap(expectedSize))

    constructor(otherMap: MutableMap<K, MutableList<V?>>) : super(LinkedHashMap(otherMap))

    fun deepCopy(): LinkedMultiValueMap<K, V?> {
        val copy = LinkedMultiValueMap<K, V?>(this.size)
        forEach { (key, values) -> copy[key] = ArrayList(values) }
        return copy
    }

    override fun clone(): LinkedMultiValueMap<K, V?> {
        return LinkedMultiValueMap(this)
    }

}