package com.hyosakura.liteioc.aop

import com.hyosakura.liteioc.bean.factory.config.AutowireCapableBeanFactory
import java.lang.reflect.Method
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.kotlinFunction

/**
 * @author LovesAsuna
 **/
open class HookSupport(private val beanFactory: AutowireCapableBeanFactory) : ProxyConfig(), Hooked {

    companion object {

        val EMPTY_TARGET_SOURCE: TargetSource = EmptyTargetSource.INSTANCE

    }

    private var targetSource: TargetSource = EMPTY_TARGET_SOURCE

    private val interfaces: MutableList<Class<*>> = ArrayList()

    private val handlers: MutableList<HookHandler> = ArrayList()

    private val handlerAdapter = HandlerAdapter(this, handlers)

    private var allInterfaces: Boolean = false

    private var hookMethods: MutableSet<Method> = LinkedHashSet()

    override fun isInterfaceProxied(intf: Class<*>): Boolean {
        for (proxyIntf in interfaces) {
            if (intf.isAssignableFrom(proxyIntf)) {
                return true
            }
        }
        return false
    }

    override fun getProxiedInterfaces(): Array<Class<*>> {
        return this.interfaces.toTypedArray()
    }

    override fun setTargetSource(targetSource: TargetSource?) {
        this.targetSource = targetSource ?: EMPTY_TARGET_SOURCE
    }

    override fun getTargetSource(): TargetSource {
        return this.targetSource
    }

    override fun getTargetClass(): Class<*>? {
        return this.targetSource.getTargetClass()
    }

    open fun setInterfaces(vararg interfaces: Class<*>) {
        require(interfaces.isNotEmpty()) { "Interfaces must not be null" }
        this.interfaces.clear()
        for (ifc in interfaces) {
            addInterface(ifc)
        }
    }

    open fun addInterface(intf: Class<*>) {
        require(intf.isInterface) { "[" + intf.name + "] is not an interface" }
        if (!interfaces.contains(intf)) {
            interfaces.add(intf)
        }
    }

    open fun addHandlers(vararg advisors: HookHandler) {
        addHandlers(listOf(*advisors))
    }

    open fun addHandlers(handlers: Collection<HookHandler>) {
        if (isFrozen()) {
            throw AopConfigException("Cannot add advisor: Configuration is frozen.")
        }
        if (!handlers.isEmpty()) {
            for (handler in handlers) {
                this.handlers.add(handler)
            }
            hookChanged()
        }
    }

    fun getHookMethods() = this.hookMethods

    open fun setHookMethods(vararg methods: Method) {
        require(methods.isNotEmpty()) { "Interfaces must not be null" }
        this.hookMethods.clear()
        for (method in methods) {
            addMethod(method)
        }
    }

    open fun addMethod(method: Method) {
        if (!hookMethods.contains(method)) {
            hookMethods.add(method)
        }
    }

    protected open fun hookChanged() {
        this.handlerAdapter.clear()
    }

    fun getHandlerForMethod(method: Method): HandlerAdapter.HandlerCache {
        return this.handlerAdapter.compute(method)
    }

}

class HandlerAdapter(private val hook: HookSupport, private val handlers: List<HookHandler>) {

    private val handlerCache = mutableMapOf<Method, HandlerCache>()

    fun clear() {
        handlerCache.clear()
    }

    class HandlerCache(val aroundHandlers: MutableList<HookHandler>, val nonAroundHandlers: MutableList<HookHandler>)

    fun compute(method: Method): HandlerCache {
        require(this.hook.getHookMethods().contains(method)) {
            "this is not a hooked method"
        }
        if (handlerCache.containsKey(method)) {
            return handlerCache[method]!!
        }
        val cache = handlerCache.computeIfAbsent(method) {
            HandlerCache(mutableListOf(), mutableListOf())
        }
        val neededHandlers = method.kotlinFunction!!.findAnnotation<Hook>()!!.value.map {
            it.java
        }
        for (handler in handlers) {
            if (!neededHandlers.contains(handler.javaClass)) {
                continue
            }
            try {
                handler.javaClass.getDeclaredMethod(
                    "around",
                    Any::class.java,
                    Method::class.java,
                    emptyArray<Any?>()::class.java
                )
                cache.aroundHandlers.add(handler)
            } catch (ignored: Throwable) {
                cache.nonAroundHandlers.add(handler)
            }
        }
        return cache
    }

}