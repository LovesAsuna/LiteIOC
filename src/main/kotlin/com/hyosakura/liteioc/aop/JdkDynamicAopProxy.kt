package com.hyosakura.liteioc.aop

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * @author LovesAsuna
 **/
class JdkDynamicAopProxy : AopProxy, InvocationHandler {

    private val hook: HookSupport

    private val proxiedInterfaces: Array<Class<*>>

    constructor(config: HookSupport) {
        this.hook = config
        this.proxiedInterfaces = AutoProxyUtil.completeProxiedInterfaces(config)
    }

    override fun getProxy(): Any {
        return getProxy(ClassLoader.getSystemClassLoader())
    }

    override fun getProxy(classLoader: ClassLoader?): Any {
        return Proxy.newProxyInstance(classLoader, this.proxiedInterfaces, this)
    }

    override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
        val target = this.hook.getTargetSource().getTarget()
        var retVal: Any? = null

        if (!this.hook.getHookMethods().contains(method)) {
            retVal = method.invoke(target, *(args ?: emptyArray()))
        } else {
            val cache = this.hook.getHandlerForMethod(method)

            val aroundHandlers = cache.aroundHandlers
            val nonAroundHandlers = cache.nonAroundHandlers

            require(aroundHandlers.size <= 1) { "the quantity of handler of type around can not be larger than 1" }

            if (aroundHandlers.isNotEmpty()) {
                retVal = aroundHandlers[0].around(target, method, args)
            } else {
                try {
                    for (handler in nonAroundHandlers) {
                        handler.before()
                    }
                    retVal = method.invoke(target, *(args ?: emptyArray()))
                    for (handler in nonAroundHandlers) {
                        retVal = handler.afterReturning(retVal)
                    }
                } catch (t: Throwable) {
                    for (handler in nonAroundHandlers) {
                        handler.afterThrowing(t)
                    }
                } finally {
                    for (handler in nonAroundHandlers) {
                        handler.after()
                    }
                }
            }
        }

        val returnType = method.returnType
        if (retVal != null && retVal === target && returnType != Any::class.java && returnType.isInstance(proxy)) {
            retVal = proxy
        } else if (retVal == null && returnType != Void.TYPE && returnType.isPrimitive) {
            throw AopInvocationException(
                "Null return value from advice does not match primitive return type for: $method"
            )
        }
        return retVal
    }

}