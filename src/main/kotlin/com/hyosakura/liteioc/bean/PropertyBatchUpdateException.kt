package com.hyosakura.liteioc.bean

/**
 * @author LovesAsuna
 **/
class PropertyBatchUpdateException : BeansException {

    val propertyAccessExceptions: Array<PropertyAccessException>

    constructor(propertyAccessExceptions: Array<PropertyAccessException>) : super(null, null) {
        require(propertyAccessExceptions.isNotEmpty()) { "At least 1 PropertyAccessException required" }
        this.propertyAccessExceptions = propertyAccessExceptions
    }

    fun getExceptionCount(): Int = propertyAccessExceptions.size

}