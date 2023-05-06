package com.hyosakura.liteioc.bean

/**
 * @author LovesAsuna
 **/
interface PropertyAccessor {

    @Throws(BeansException::class)
    fun setPropertyValues(pvs: PropertyValues)

    fun isWritableProperty(propertyName: String): Boolean

}