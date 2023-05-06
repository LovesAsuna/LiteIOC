package com.hyosakura.liteioc.util

import java.io.Serializable

/**
 * @author LovesAsuna
 **/
open class MultiValueMapAdapter<K, V> : MultiValueMap<K, V>, Serializable {

    private val targetMap: MutableMap<K, MutableList<V?>>

    constructor(targetMap: MutableMap<K, MutableList<V?>>) {
        this.targetMap = targetMap
    }


    override fun getFirst(key: K): V? {
        val values = targetMap[key]
        return if (!values.isNullOrEmpty()) values[0] else null
    }

    override fun add(key: K, value: V?) {
        val values = targetMap.computeIfAbsent(
            key
        ) { _: K -> ArrayList(1) }
        values.add(value)
    }

    override fun addAll(key: K, values: MutableList<V?>) {
        val currentValues = targetMap.computeIfAbsent(
            key
        ) { k: K -> ArrayList(1) }
        currentValues.addAll(values)
    }

    override fun addAll(values: MultiValueMap<K, V>) {
        for (entry in values.entries) {
            addAll(entry.key, entry.value)
        }
    }

    override operator fun set(key: K, value: V?) {
        val values = ArrayList<V?>(1)
        values.add(value)
        targetMap[key] = values
    }

    override fun setAll(values: Map<K, V>) {
        values.forEach { (key: K, value: V) -> this[key] = value }
    }

    override fun toSingleValueMap(): Map<K, V?> {
        val singleValueMap = LinkedHashMap<K, V?>()
        targetMap.forEach { (key, values) ->
            if (values.isNotEmpty()) {
                singleValueMap[key] = values[0]
            }
        }
        return singleValueMap
    }

    override val size: Int
        get() = targetMap.size

    override fun isEmpty(): Boolean = targetMap.isEmpty()

    override fun containsKey(key: K): Boolean = targetMap.containsKey(key)

    override fun containsValue(value: MutableList<V?>): Boolean = targetMap.containsValue(value)

    override fun get(key: K): MutableList<V?>? = targetMap[key]

    override fun put(key: K, value: MutableList<V?>): MutableList<V?>? = targetMap.put(key, value)

    override fun remove(key: K): MutableList<V?>? = targetMap.remove(key)

    override fun putAll(from: Map<out K, MutableList<V?>>) = targetMap.putAll(from)

    override fun clear() = targetMap.clear()

    override val keys: MutableSet<K>
        get() = targetMap.keys

    override val values: MutableCollection<MutableList<V?>>
        get() = targetMap.values

    override val entries: MutableSet<MutableMap.MutableEntry<K, MutableList<V?>>>
        get() = targetMap.entries

    override fun equals(other: Any?): Boolean {
        return this === other || targetMap == other
    }

    override fun hashCode(): Int = targetMap.hashCode()

    override fun toString(): String = targetMap.toString()

}