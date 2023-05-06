package com.hyosakura.liteioc.core

/**
 * @author LovesAsuna
 **/
open class ConversionException : RuntimeException {

    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable) : super(message, cause)

}