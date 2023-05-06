package com.hyosakura.liteioc.bean

/**
 * @author LovesAsuna
 **/
abstract class BeansException : Exception {

    constructor(msg: String) : super(msg)

    constructor(msg: String?, cause: Throwable?) : super(msg, cause)

}