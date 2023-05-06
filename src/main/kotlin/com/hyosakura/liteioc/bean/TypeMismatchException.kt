package com.hyosakura.liteioc.bean

import com.hyosakura.liteioc.util.ClassUtil
import java.beans.PropertyChangeEvent

/**
 * @author LovesAsuna
 **/
open class TypeMismatchException : PropertyAccessException {

    companion object {

        const val ERROR_CODE = "typeMismatch"

    }

    private var propertyName: String? = null

    @Transient
    private var value: Any? = null

    private var requiredType: Class<*>? = null

    constructor(propertyChangeEvent: PropertyChangeEvent?, requiredType: Class<*>?) : this(
        propertyChangeEvent, requiredType, null
    )

    constructor(
        propertyChangeEvent: PropertyChangeEvent, requiredType: Class<*>?, cause: Throwable?
    ) : super(
        propertyChangeEvent,
        ("Failed to convert property value of type '" + ClassUtil.getDescriptiveType(propertyChangeEvent.newValue)) + "'" + (if (requiredType != null) (" to required type '" + ClassUtil.getQualifiedName(
            requiredType
        )) + "'" else "") + if (propertyChangeEvent.propertyName != null) " for property '" + propertyChangeEvent.propertyName + "'" else "",
        cause
    ) {

        this.propertyName = propertyChangeEvent.propertyName
        value = propertyChangeEvent.newValue
        this.requiredType = requiredType
    }

    constructor(value: Any?, requiredType: Class<*>?) : this(value, requiredType, null)

    constructor(value: Any?, requiredType: Class<*>?, cause: Throwable?) : super(
        (("Failed to convert value of type '" + ClassUtil.getDescriptiveType(value)) + "'" + (if (requiredType != null) (" to required type '" + ClassUtil.getQualifiedName(
            requiredType
        )) + "'" else "")), cause
    ) {

        this.value = value
        this.requiredType = requiredType
    }

    fun initPropertyName(propertyName: String) {
        this.propertyName = propertyName
    }

    override fun getPropertyName(): String? {
        return propertyName
    }

    override fun getValue(): Any? {
        return value
    }

    fun getRequiredType(): Class<*>? {
        return requiredType
    }

    override fun getErrorCode(): String {
        return ERROR_CODE
    }

}