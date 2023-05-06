package com.hyosakura.liteioc.bean

/**
 * @author LovesAsuna
 **/
interface PropertyValues : Iterable<PropertyValue> {

    override fun iterator(): Iterator<PropertyValue> {
        return getPropertyValues().iterator()
    }

    fun getPropertyValues(): Array<PropertyValue>

    fun getPropertyValue(propertyName: String?): PropertyValue?

    fun contains(propertyName: String?): Boolean

    fun isEmpty(): Boolean

}