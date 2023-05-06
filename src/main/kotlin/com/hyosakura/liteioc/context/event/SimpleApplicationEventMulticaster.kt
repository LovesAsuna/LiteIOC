package com.hyosakura.liteioc.context.event

import com.hyosakura.liteioc.bean.factory.BeanFactory
import com.hyosakura.liteioc.context.ApplicationListener
import com.hyosakura.liteioc.context.PayloadApplicationEvent
import com.hyosakura.liteioc.core.ResolvableType
import com.hyosakura.liteioc.util.ErrorHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.Executor

/**
 * @author LovesAsuna
 **/
class SimpleApplicationEventMulticaster : AbstractApplicationEventMulticaster {

    private var taskExecutor: Executor? = null

    private var errorHandler: ErrorHandler? = null

    @Volatile
    private var lazyLogger: Logger? = null

    constructor(beanFactory: BeanFactory) {
        setBeanFactory(beanFactory)
    }

    open fun getTaskExecutor(): Executor? {
        return taskExecutor
    }

    open fun getErrorHandler(): ErrorHandler? {
        return this.errorHandler
    }

    override fun multicastEvent(event: ApplicationEvent) {
        multicastEvent(event, resolveDefaultEventType(event))
    }

    override fun multicastEvent(event: ApplicationEvent, eventType: ResolvableType?) {
        val type = eventType ?: resolveDefaultEventType(event)
        val executor = getTaskExecutor()
        for (listener in getApplicationListeners(event, type)) {
            if (executor != null) {
                executor.execute { invokeListener(listener, event) }
            } else {
                invokeListener(listener, event)
            }
        }
    }

    private fun resolveDefaultEventType(event: ApplicationEvent): ResolvableType {
        return ResolvableType.forInstance(event)
    }

    @Suppress("UNCHECKED_CAST")
    open fun invokeListener(listener: ApplicationListener<*>, event: ApplicationEvent) {
        val errorHandler = getErrorHandler()
        if (errorHandler != null) {
            try {
                doInvokeListener(listener as ApplicationListener<in ApplicationEvent>, event)
            } catch (err: Throwable) {
                errorHandler.handleError(err)
            }
        } else {
            doInvokeListener(listener as ApplicationListener<in ApplicationEvent>, event)
        }
    }

    private fun doInvokeListener(listener: ApplicationListener<in ApplicationEvent>, event: ApplicationEvent) {
        try {
            listener.onApplicationEvent(event)
        } catch (ex: ClassCastException) {
            val msg = ex.message
            if (msg == null || matchesClassCastMessage(
                    msg,
                    event::class.java
                ) || (event is PayloadApplicationEvent<*> && matchesClassCastMessage(msg, event.payload!!::class.java))
            ) {
                // Possibly a lambda-defined listener which we could not resolve the generic event type for
                // -> let's suppress the exception.
                var loggerToUse = this.lazyLogger
                if (loggerToUse == null) {
                    loggerToUse = LoggerFactory.getLogger(javaClass)
                    this.lazyLogger = loggerToUse
                }
                if (loggerToUse!!.isTraceEnabled) {
                    loggerToUse.trace("Non-matching event type for listener: $listener", ex)
                }
            } else {
                throw ex
            }
        }
    }

    private fun matchesClassCastMessage(classCastMessage: String, eventClass: Class<*>): Boolean {
        // On Java 8, the message starts with the class name: "java.lang.String cannot be cast..."
        if (classCastMessage.startsWith(eventClass.name)) {
            return true
        }
        // On Java 11, the message starts with "class ..." a.k.a. Class.toString()
        if (classCastMessage.startsWith(eventClass.toString())) {
            return true
        }
        // On Java 9, the message used to contain the module name: "java.base/java.lang.String cannot be cast..."
        val moduleSeparatorIndex = classCastMessage.indexOf('/')
        return moduleSeparatorIndex != -1 && classCastMessage.startsWith(
            eventClass.name, moduleSeparatorIndex + 1
        )
    }
}