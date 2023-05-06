package com.hyosakura.liteioc.bean

import java.util.*

/**
 * @author LovesAsuna
 **/
abstract class AbstractPropertyAccessor : TypeConverterSupport(), ConfigurablePropertyAccessor {

    @Throws(BeansException::class)
    override fun setPropertyValues(pvs: PropertyValues) {
        var propertyAccessExceptions: MutableList<PropertyAccessException>? = null
        val propertyValues: List<PropertyValue> =
            if (pvs is MutablePropertyValues) pvs.getPropertyValueList() else pvs.getPropertyValues().toList()
        for (pv in propertyValues) {
            // setPropertyValue may throw any BeansException, which won't be caught
            // here, if there is a critical failure such as no matching field.
            // We can attempt to deal only with less serious exceptions.
            try {
                setPropertyValue(pv)
            } catch (ex: PropertyAccessException) {
                if (propertyAccessExceptions == null) {
                    propertyAccessExceptions = ArrayList()
                }
                propertyAccessExceptions.add(ex)
            }
        }

        // If we encountered individual exceptions, throw the composite exception.
        if (propertyAccessExceptions != null) {
            val paeArray: Array<PropertyAccessException> = propertyAccessExceptions.toTypedArray()
            throw PropertyBatchUpdateException(paeArray)
        }
    }

    @Throws(BeansException::class)
    fun setPropertyValue(pv: PropertyValue) {
        setPropertyValue(pv.name, pv.value)
    }

    @Throws(BeansException::class)
    abstract fun setPropertyValue(propertyName: String, value: Any?)

}