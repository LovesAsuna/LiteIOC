package com.hyosakura.liteioc.util

/**
 * @author LovesAsuna
 **/
fun interface ErrorHandler {

    fun handleError(t: Throwable)

}