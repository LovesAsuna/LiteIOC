package com.hyosakura.liteioc.aop

import net.bytebuddy.ByteBuddy
import net.bytebuddy.implementation.InvocationHandlerAdapter
import net.bytebuddy.matcher.ElementMatchers

/**
 * @author LovesAsuna
 **/
class ByteBuddyAopProxy : AopProxy {

    companion object {

        val bytebuddy = ByteBuddy()

    }

    private val hook: HookSupport

    constructor(config: HookSupport) {
        this.hook = config
    }

    override fun getProxy(): Any {
        return getProxy(ClassLoader.getSystemClassLoader())
    }

    override fun getProxy(classLoader: ClassLoader?): Any {
        val targetClass =
            requireNotNull(this.hook.getTargetClass()) { "Target class must be available for creating a ByteBuddy proxy" }
        val newClassName = "${targetClass.name}${"$"}${"ByteBuddy"}"
        var builder = bytebuddy.subclass(targetClass).name(newClassName)

        for (method in this.hook.getHookMethods()) {
            builder = builder.method(ElementMatchers.`is`(method)).intercept(
                InvocationHandlerAdapter.of(
                    JdkDynamicAopProxy(this.hook)
                )
            )
        }
        val unloaded = builder.make()
        return unloaded.load(classLoader).loaded.getDeclaredConstructor().newInstance()
    }

}