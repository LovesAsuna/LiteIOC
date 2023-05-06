package com.hyosakura.liteioc.context.event

import com.hyosakura.liteioc.aop.support.AopUtil
import com.hyosakura.liteioc.context.ApplicationListener
import com.hyosakura.liteioc.core.Ordered
import com.hyosakura.liteioc.core.ResolvableType
import java.util.concurrent.ConcurrentHashMap

/**
 * @author LovesAsuna
 **/
class GenericApplicationListenerAdapter : GenericApplicationListener {

    companion object {

        private val eventTypeCache: MutableMap<Class<*>, ResolvableType> = ConcurrentHashMap()

        private fun resolveDeclaredEventType(listener: ApplicationListener<ApplicationEvent>): ResolvableType? {
            var declaredEventType = resolveDeclaredEventType(listener.javaClass)
            if (declaredEventType == null || declaredEventType.isAssignableFrom(ApplicationEvent::class.java)) {
                val targetClass = AopUtil.getTargetClass(listener)
                if (targetClass != listener.javaClass) {
                    declaredEventType = resolveDeclaredEventType(targetClass)
                }
            }
            return declaredEventType
        }

        fun resolveDeclaredEventType(listenerType: Class<*>): ResolvableType? {
            var eventType: ResolvableType? = eventTypeCache[listenerType]
            if (eventType == null) {
                eventType = ResolvableType.forClass(listenerType).`as`(ApplicationListener::class.java).getGeneric()
                eventTypeCache[listenerType] = eventType
            }
            return if (eventType !== ResolvableType.NONE) eventType else null
        }

    }

    private val delegate: ApplicationListener<ApplicationEvent>

    private var declaredEventType: ResolvableType? = null

    @Suppress("UNCHECKED_CAST")
    constructor(delegate: ApplicationListener<*>) {
        this.delegate = delegate as ApplicationListener<ApplicationEvent>
        declaredEventType = resolveDeclaredEventType(this.delegate)
    }

    override fun onApplicationEvent(event: ApplicationEvent) {
        delegate.onApplicationEvent(event)
    }

    @Suppress("UNCHECKED_CAST")
    override fun supportsEventType(eventType: ResolvableType): Boolean {
        return when (delegate) {
            is GenericApplicationListener -> {
                delegate.supportsEventType(eventType)
            }

            is SmartApplicationListener -> {
                val eventClass = eventType.resolve() as Class<out ApplicationEvent>
                delegate.supportsEventType(eventClass)
            }

            else -> {
                declaredEventType == null || declaredEventType!!.isAssignableFrom(eventType)
            }
        }
    }

    override fun supportsSourceType(sourceType: Class<*>?): Boolean {
        return delegate !is SmartApplicationListener || delegate.supportsSourceType(sourceType)
    }

    override fun getOrder(): Int {
        return if (delegate is Ordered) (delegate as Ordered).getOrder() else Ordered.LOWEST_PRECEDENCE
    }

    override fun getListenerId(): String {
        return if (delegate is SmartApplicationListener) delegate.getListenerId() else ""
    }

}