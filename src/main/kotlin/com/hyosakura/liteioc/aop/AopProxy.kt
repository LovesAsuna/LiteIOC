package com.hyosakura.liteioc.aop

/**
 * @author LovesAsuna
 **/
interface AopProxy {

    fun getProxy(): Any

    fun getProxy(classLoader: ClassLoader?): Any

}