package com.hyosakura.liteioc.aop

import java.lang.reflect.Method

/**
 * @author LovesAsuna
 **/
@JvmDefaultWithoutCompatibility
interface HookHandler {

    fun before()

    fun afterReturning(obj: Any?): Any?

    fun around(target: Any?, method: Method, args: Array<Any?>?): Any? {
        return method.invoke(target, *(args ?: emptyArray()))
    }

    fun afterThrowing(throwable: Throwable)

    fun after()

}