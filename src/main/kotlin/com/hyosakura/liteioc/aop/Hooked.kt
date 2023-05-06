package com.hyosakura.liteioc.aop

/**
 * @author LovesAsuna
 **/
interface Hooked : TargetClassAware {

    fun getProxiedInterfaces(): Array<Class<*>>

    fun setTargetSource(targetSource: TargetSource?)

    fun getTargetSource(): TargetSource

    fun isInterfaceProxied(intf: Class<*>): Boolean

}