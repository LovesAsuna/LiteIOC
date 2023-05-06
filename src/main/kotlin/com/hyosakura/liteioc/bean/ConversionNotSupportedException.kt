package com.hyosakura.liteioc.bean

import java.beans.PropertyChangeEvent

/**
 * @author LovesAsuna
 **/
class ConversionNotSupportedException : TypeMismatchException {

    constructor(
        propertyChangeEvent: PropertyChangeEvent?, requiredType: Class<*>?, cause: Throwable?
    ) : super(propertyChangeEvent, requiredType, cause)

    constructor(
        value: Any?, requiredType: Class<*>?, cause: Throwable?
    ) : super(value, requiredType, cause)

}