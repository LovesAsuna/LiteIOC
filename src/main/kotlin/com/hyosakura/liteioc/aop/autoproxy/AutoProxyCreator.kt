package com.hyosakura.liteioc.aop.autoproxy

import com.hyosakura.liteioc.aop.*
import com.hyosakura.liteioc.bean.BeanInstantiationException
import com.hyosakura.liteioc.bean.factory.config.AutowireCapableBeanFactory
import com.hyosakura.liteioc.bean.factory.config.ConfigurableBeanFactory
import com.hyosakura.liteioc.bean.factory.config.ConfigurableListableBeanFactory
import com.hyosakura.liteioc.core.SmartClassLoader
import com.hyosakura.liteioc.util.ClassUtil
import com.hyosakura.liteioc.util.ReflectionUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.jvmName
import kotlin.reflect.jvm.kotlinFunction

open class AutoProxyCreator(private val beanFactory: AutowireCapableBeanFactory) : ProxyProcessorSupport() {

    companion object {

        var DO_NOT_PROXY: Array<Any>? = null

    }

    private val annotationName: String = Hook::class.java.name

    private val earlyProxyReferences: MutableMap<Any, Any> = ConcurrentHashMap(16)

    private val targetSourcedBeans = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>(16))

    val logger: Logger = LoggerFactory.getLogger(javaClass)

    private val advisedBeans: MutableMap<Any, Boolean> = ConcurrentHashMap(256)

    fun applyAutoProxy(bean: Any, beanName: String): Any {
        val cacheKey = getCacheKey(bean.javaClass, beanName)
        if (earlyProxyReferences.remove(cacheKey) !== bean) {
            return wrapIfNecessary(bean, beanName, cacheKey)
        }
        return bean
    }

    private fun wrapIfNecessary(bean: Any, beanName: String, cacheKey: Any): Any {
        if (beanName.isNotEmpty() && this.targetSourcedBeans.contains(beanName)) {
            return bean
        }
        if (false == this.advisedBeans[cacheKey]) {
            return bean
        }
        if (isInfrastructureClass(bean.javaClass)) {
            this.advisedBeans[cacheKey] = false
            return bean
        }

        val specificMethods = getHookMethodsForBean(bean.javaClass)
        if (!specificMethods.contentEquals(DO_NOT_PROXY)) {
            this.advisedBeans[cacheKey] = true
            return createProxy(
                bean.javaClass, beanName, specificMethods!!, SingletonTargetSource(bean)
            )
        }

        this.advisedBeans[cacheKey] = false
        return bean
    }

    fun createProxy(
        beanClass: Class<*>, beanName: String?, specificMethods: Array<Method>, targetSource: TargetSource
    ): Any {
        val proxyFactory = ProxyFactory(beanFactory)
        proxyFactory.copyFrom(this)

        if (proxyFactory.isProxyTargetClass()) {
            if (Proxy.isProxyClass(beanClass)) {
                for (ifc in beanClass.interfaces) {
                    proxyFactory.addInterface(ifc)
                }
            }
        } else {
            if (shouldProxyTargetClass(beanClass, beanName)) {
                proxyFactory.setProxyTargetClass(true)
            } else {
                evaluateProxyInterfaces(beanClass, proxyFactory)
            }
        }

        val handlers = buildHandlers(specificMethods)
        proxyFactory.setTargetSource(targetSource)
        proxyFactory.setHookMethods(*specificMethods)
        proxyFactory.addHandlers(*handlers)

        var classLoader = this.proxyClassLoader
        if (classLoader is SmartClassLoader && classLoader != beanClass.classLoader) {
            classLoader = (classLoader as SmartClassLoader).getOriginalClassLoader()
        }
        return proxyFactory.getProxy(classLoader)
    }

    fun getCacheKey(beanClass: Class<*>, beanName: String?): Any {
        return if (!beanName.isNullOrEmpty()) {
            beanName
        } else {
            beanClass
        }
    }

    fun isInfrastructureClass(beanClass: Class<*>): Boolean {
        val retVal = HookHandler::class.java.isAssignableFrom(beanClass)
        if (retVal && logger.isTraceEnabled) {
            logger.trace("Did not attempt to auto-proxy infrastructure class [" + beanClass.name + "]")
        }
        return retVal
    }

    open fun shouldProxyTargetClass(beanClass: Class<*>, beanName: String?): Boolean {
        return beanFactory is ConfigurableListableBeanFactory && AutoProxyUtil.shouldProxyTargetClass(
            beanFactory, beanName
        )
    }

    fun getHookMethodsForBean(beanClass: Class<*>): Array<Method>? {
        val methods = LinkedList<Method>()
        for (method in beanClass.kotlin.declaredFunctions) {
            if (method.hasAnnotation<Hook>()) {
                method.javaMethod?.let { methods.add(it) }
            }
        }
        return if (methods.isEmpty()) {
            null
        } else {
            methods.toTypedArray()
        }
    }

    private fun buildHandlers(methods: Array<Method>): Array<HookHandler> {
        val handlersList = LinkedList<HookHandler>()
        for (method in methods) {
            val hook = method.kotlinFunction!!.findAnnotation<Hook>()!!
            val handlers = hook.value
            for (handlerClass in handlers) {
                // first try get handler in BeanFactory
                val handler = try {
                    this.beanFactory.getBean(handlerClass.java)
                } catch (ignored: Exception) {
                    try {
                        ReflectionUtil.accessibleConstructor(handlerClass.java).newInstance()
                    } catch (ex: Exception) {
                        throw BeanInstantiationException(handlerClass.java, "no primary constructor in handler", ex)
                    }
                }
                handlersList.add(handler)
                if (this.beanFactory is ConfigurableBeanFactory) {
                    this.beanFactory.registerSingleton(ClassUtil.getShortName(handlerClass.jvmName), handler)
                }
            }
        }
        return handlersList.toTypedArray()
    }

}