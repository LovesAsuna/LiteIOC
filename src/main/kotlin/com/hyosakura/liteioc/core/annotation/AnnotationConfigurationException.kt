package com.hyosakura.liteioc.core.annotation

/**
 * @author LovesAsuna
 **/
class AnnotationConfigurationException : RuntimeException {

    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable) : super(message, cause)

}