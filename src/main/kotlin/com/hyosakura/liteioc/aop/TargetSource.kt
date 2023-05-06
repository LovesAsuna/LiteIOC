package com.hyosakura.liteioc.aop

/**
 * @author LovesAsuna
 **/
interface TargetSource {

    fun getTargetClass(): Class<*>?

    fun isStatic(): Boolean

    @Throws(Exception::class)
    fun getTarget(): Any?

    @Throws(Exception::class)
    fun releaseTarget(target: Any)

}