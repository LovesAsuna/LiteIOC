package com.hyosakura.liteioc.aop

/**
 * @author LovesAsuna
 **/
interface TargetClassAware {

    fun getTargetClass(): Class<*>?

}