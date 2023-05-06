package com.hyosakura.liteioc.core.env

import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.stream.Stream

/**
 * @author LovesAsuna
 **/
open class MutablePropertySources : PropertySources {

    private val propertySourceList: MutableList<PropertySource<*>> = CopyOnWriteArrayList()

    constructor ()

    constructor(propertySources: PropertySources) {
        for (propertySource in propertySources) {
            addLast(propertySource)
        }
    }

    override operator fun iterator(): Iterator<PropertySource<*>> {
        return propertySourceList.iterator()
    }

    fun spliterator(): Spliterator<PropertySource<*>?>? {
        return Spliterators.spliterator(propertySourceList, 0)
    }

    override fun stream(): Stream<PropertySource<*>> {
        return propertySourceList.stream()
    }

    override fun contains(name: String?): Boolean {
        for (propertySource in propertySourceList) {
            if (propertySource.name == name) {
                return true
            }
        }
        return false
    }

    override operator fun get(name: String?): PropertySource<*>? {
        for (propertySource in propertySourceList) {
            if (propertySource.name == name) {
                return propertySource
            }
        }
        return null
    }

    fun addFirst(propertySource: PropertySource<*>) {
        synchronized(propertySourceList) {
            removeIfPresent(propertySource)
            propertySourceList.add(0, propertySource)
        }
    }

    fun addLast(propertySource: PropertySource<*>) {
        synchronized(propertySourceList) {
            removeIfPresent(propertySource)
            propertySourceList.add(propertySource)
        }
    }

    fun addBefore(relativePropertySourceName: String, propertySource: PropertySource<*>) {
        assertLegalRelativeAddition(relativePropertySourceName, propertySource)
        synchronized(propertySourceList) {
            removeIfPresent(propertySource)
            val index = assertPresentAndGetIndex(relativePropertySourceName)
            addAtIndex(index, propertySource)
        }
    }

    fun addAfter(relativePropertySourceName: String, propertySource: PropertySource<*>) {
        assertLegalRelativeAddition(relativePropertySourceName, propertySource)
        synchronized(propertySourceList) {
            removeIfPresent(propertySource)
            val index = assertPresentAndGetIndex(relativePropertySourceName)
            addAtIndex(index + 1, propertySource)
        }
    }

    fun precedenceOf(propertySource: PropertySource<*>): Int {
        return propertySourceList.indexOf(propertySource)
    }

    fun remove(name: String): PropertySource<*>? {
        synchronized(propertySourceList) {
            val index = propertySourceList.indexOf(PropertySource.named(name))
            return if (index != -1) propertySourceList.removeAt(index) else null
        }
    }

    fun replace(name: String, propertySource: PropertySource<*>) {
        synchronized(propertySourceList) {
            val index = assertPresentAndGetIndex(name)
            propertySourceList.set(index, propertySource)
        }
    }

    fun size(): Int = propertySourceList.size

    override fun toString(): String = propertySourceList.toString()

    open fun assertLegalRelativeAddition(relativePropertySourceName: String, propertySource: PropertySource<*>) {
        val newPropertySourceName = propertySource.name
        require(relativePropertySourceName != newPropertySourceName) { "PropertySource named '$newPropertySourceName' cannot be added relative to itself" }
    }

    open fun removeIfPresent(propertySource: PropertySource<*>) {
        propertySourceList.remove(propertySource)
    }

    private fun addAtIndex(index: Int, propertySource: PropertySource<*>) {
        removeIfPresent(propertySource)
        propertySourceList.add(index, propertySource)
    }

    private fun assertPresentAndGetIndex(name: String): Int {
        val index = propertySourceList.indexOf(PropertySource.named(name))
        require(index != -1) { "PropertySource named '$name' does not exist" }
        return index
    }

}