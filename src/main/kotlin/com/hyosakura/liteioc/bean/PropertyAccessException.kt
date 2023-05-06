package com.hyosakura.liteioc.bean

import java.beans.PropertyChangeEvent

/**
 * @author LovesAsuna
 **/
abstract class PropertyAccessException : BeansException {

    val propertyChangeEvent: PropertyChangeEvent?

    constructor(propertyChangeEvent: PropertyChangeEvent, msg: String, cause: Throwable?) : super(msg, cause) {
        this.propertyChangeEvent = propertyChangeEvent
    }

    constructor(msg: String, cause: Throwable?) : super(msg, cause) {
        this.propertyChangeEvent = null
    }

    open fun getPropertyName(): String? {
        return propertyChangeEvent?.propertyName
    }

    open fun getValue(): Any? {
        return propertyChangeEvent?.newValue
    }

    abstract fun getErrorCode(): String?

}