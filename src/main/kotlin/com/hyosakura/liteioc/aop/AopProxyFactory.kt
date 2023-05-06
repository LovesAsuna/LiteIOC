package com.hyosakura.liteioc.aop

/**
 * @author LovesAsuna
 **/
interface AopProxyFactory {

    fun createAopProxy(config: HookSupport): AopProxy

}