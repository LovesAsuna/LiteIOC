package com.hyosakura.liteioc.bean

/**
 * @author LovesAsuna
 **/
open class FatalBeanException : BeansException {

    constructor(msg: String) : super(msg)

    constructor(msg: String, cause: Throwable?) : super(msg, cause)

}