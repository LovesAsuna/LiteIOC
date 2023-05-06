package com.hyosakura.liteioc.context.event

import com.hyosakura.liteioc.bean.factory.BeanFactory
import com.hyosakura.liteioc.bean.factory.BeanFactoryAware
import com.hyosakura.liteioc.bean.factory.NoSuchBeanDefinitionException
import com.hyosakura.liteioc.bean.factory.config.ConfigurableBeanFactory
import com.hyosakura.liteioc.context.ApplicationListener
import com.hyosakura.liteioc.core.ResolvableType
import com.hyosakura.liteioc.util.ObjectUtil
import java.util.concurrent.ConcurrentHashMap

/**
 * @author LovesAsuna
 **/
abstract class AbstractApplicationEventMulticaster : ApplicationEventMulticaster, BeanFactoryAware {

    private lateinit var beanFactory: ConfigurableBeanFactory

    private val defaultRetriever: DefaultListenerRetriever = DefaultListenerRetriever()

    private val retrieverCache: MutableMap<ListenerCacheKey, CachedListenerRetriever> = ConcurrentHashMap(64)

    override fun setBeanFactory(beanFactory: BeanFactory) {
        check(beanFactory is ConfigurableBeanFactory) { "Not running in a ConfigurableBeanFactory: $beanFactory" }
        this.beanFactory = beanFactory
    }

    override fun addApplicationListener(listener: ApplicationListener<*>) {
        this.defaultRetriever.applicationListeners.add(listener)
    }

    override fun addApplicationListenerBean(listenerBeanName: String) {
        synchronized(this.defaultRetriever) {
            this.defaultRetriever.applicationListenerBeans.add(listenerBeanName)
            this.retrieverCache.clear()
        }
    }

    override fun removeApplicationListener(listener: ApplicationListener<*>) {
        this.defaultRetriever.applicationListeners.remove(listener)
    }

    override fun removeAllListeners() {
        this.defaultRetriever.applicationListeners.clear()
        this.defaultRetriever.applicationListenerBeans.clear()
    }

    protected open fun getApplicationListeners(
        event: ApplicationEvent, eventType: ResolvableType
    ): Collection<ApplicationListener<*>> {
        val source = event.source
        val sourceType = source?.javaClass
        val cacheKey = ListenerCacheKey(eventType, sourceType)

        // Potential new retriever to populate
        var newRetriever: CachedListenerRetriever? = null

        // Quick check for existing entry on ConcurrentHashMap
        var existingRetriever = this.retrieverCache[cacheKey]
        if (existingRetriever == null) {
            // Caching a new ListenerRetriever if possible
            newRetriever = CachedListenerRetriever()
            existingRetriever = this.retrieverCache.putIfAbsent(cacheKey, newRetriever)
            if (existingRetriever != null) {
                newRetriever = null // no need to populate it in retrieveApplicationListeners
            }
        }
        if (existingRetriever != null) {
            val result = existingRetriever.getApplicationListeners()
            if (result != null) {
                return result
            }
            // If result is null, the existing retriever is not fully populated yet by another thread.
            // Proceed like caching wasn't possible for this current local attempt.
        }
        return retrieveApplicationListeners(eventType, sourceType, newRetriever)
    }

    open fun retrieveApplicationListeners(
        eventType: ResolvableType, sourceType: Class<*>?, retriever: CachedListenerRetriever?
    ): Collection<ApplicationListener<*>> {
        val allListeners = ArrayList<ApplicationListener<*>>()
        val filteredListeners = if (retriever != null) LinkedHashSet<ApplicationListener<*>>() else null
        val filteredListenerBeans = if (retriever != null) LinkedHashSet<String>() else null
        var listeners: MutableSet<ApplicationListener<*>>
        var listenerBeans: MutableSet<String>
        synchronized(defaultRetriever) {
            listeners = LinkedHashSet(defaultRetriever.applicationListeners)
            listenerBeans = LinkedHashSet(defaultRetriever.applicationListenerBeans)
        }

        // Add programmatically registered listeners, including ones coming
        // from ApplicationListenerDetector (singleton beans and inner beans).
        for (listener in listeners) {
            if (supportsEvent(listener, eventType, sourceType)) {
                if (retriever != null) {
                    filteredListeners!!.add(listener)
                }
                allListeners.add(listener)
            }
        }

        // Add listeners by bean name, potentially overlapping with programmatically
        // registered listeners above - but here potentially with additional metadata.
        if (listenerBeans.isNotEmpty()) {
            val beanFactory = getBeanFactory()
            for (listenerBeanName in listenerBeans) {
                try {
                    if (supportsEvent(beanFactory, listenerBeanName, eventType)) {
                        val listener = beanFactory.getBean(listenerBeanName, ApplicationListener::class.java)
                        if (!allListeners.contains(listener) && supportsEvent(listener, eventType, sourceType)) {
                            if (retriever != null) {
                                if (beanFactory.isSingleton(listenerBeanName)) {
                                    filteredListeners!!.add(listener)
                                } else {
                                    filteredListenerBeans!!.add(listenerBeanName)
                                }
                            }
                            allListeners.add(listener)
                        }
                    } else {
                        // Remove non-matching listeners that originally came from
                        // ApplicationListenerDetector, possibly ruled out by additional
                        // BeanDefinition metadata (e.g. factory method generics) above.
                        val listener = beanFactory.getSingleton(listenerBeanName)
                        if (retriever != null) {
                            filteredListeners!!.remove(listener)
                        }
                        allListeners.remove(listener)
                    }
                } catch (ex: NoSuchBeanDefinitionException) {
                    // Singleton listener instance (without backing bean definition) disappeared -
                    // probably in the middle of the destruction phase
                }
            }
        }
        if (retriever != null) {
            if (filteredListenerBeans!!.isEmpty()) {
                retriever.applicationListeners = LinkedHashSet(allListeners)
                retriever.applicationListenerBeans = filteredListenerBeans
            } else {
                retriever.applicationListeners = filteredListeners
                retriever.applicationListenerBeans = filteredListenerBeans
            }
        }
        return allListeners
    }

    private fun supportsEvent(
        beanFactory: ConfigurableBeanFactory, listenerBeanName: String, eventType: ResolvableType
    ): Boolean {
        val listenerType = beanFactory.getType(listenerBeanName)
        if (listenerType == null || GenericApplicationListener::class.java.isAssignableFrom(listenerType) || SmartApplicationListener::class.java.isAssignableFrom(
                listenerType
            )
        ) {
            return true
        }
        return if (!supportsEvent(listenerType, eventType)) {
            false
        } else try {
            val bd = beanFactory.getMergedBeanDefinition(listenerBeanName)
            val genericEventType = bd.getResolvableType().`as`(ApplicationListener::class.java).getGeneric()
            genericEventType == ResolvableType.NONE || genericEventType.isAssignableFrom(eventType)
        } catch (ex: NoSuchBeanDefinitionException) {
            // Ignore - no need to check resolvable type for manually registered singleton
            true
        }
    }

    open fun supportsEvent(listenerType: Class<*>, eventType: ResolvableType?): Boolean {
        val declaredEventType = GenericApplicationListenerAdapter.resolveDeclaredEventType(listenerType)
        return declaredEventType == null || declaredEventType.isAssignableFrom(eventType!!)
    }

    open fun supportsEvent(
        listener: ApplicationListener<*>, eventType: ResolvableType, sourceType: Class<*>?
    ): Boolean {
        val smartListener: GenericApplicationListener =
            if (listener is GenericApplicationListener) listener else GenericApplicationListenerAdapter(
                listener
            )
        return smartListener.supportsEventType(eventType) && smartListener.supportsSourceType(sourceType)
    }

    private fun getBeanFactory() = beanFactory

    inner class CachedListenerRetriever {

        @Volatile
        var applicationListeners: Set<ApplicationListener<*>>? = null

        @Volatile
        var applicationListenerBeans: Set<String>? = null

        fun getApplicationListeners(): Collection<ApplicationListener<*>>? {
            val applicationListeners = applicationListeners
            val applicationListenerBeans = applicationListenerBeans
            if (applicationListeners == null || applicationListenerBeans == null) {
                // Not fully populated yet
                return null
            }
            val allListeners =
                ArrayList<ApplicationListener<*>>(applicationListeners.size + applicationListenerBeans.size)
            allListeners.addAll(applicationListeners)
            if (applicationListenerBeans.isNotEmpty()) {
                val beanFactory = getBeanFactory()
                for (listenerBeanName in applicationListenerBeans) {
                    try {
                        allListeners.add(beanFactory.getBean(listenerBeanName, ApplicationListener::class.java))
                    } catch (ex: NoSuchBeanDefinitionException) {
                        // Singleton listener instance (without backing bean definition) disappeared -
                        // probably in the middle of the destruction phase
                    }
                }
            }
            return allListeners
        }
    }

    private inner class DefaultListenerRetriever {

        val applicationListeners = LinkedHashSet<ApplicationListener<*>>()

        val applicationListenerBeans = LinkedHashSet<String>()

        fun getApplicationListeners(): Collection<ApplicationListener<*>> {
            val allListeners = ArrayList<ApplicationListener<*>>(
                applicationListeners.size + applicationListenerBeans.size
            )
            allListeners.addAll(applicationListeners)
            if (applicationListenerBeans.isNotEmpty()) {
                val beanFactory: BeanFactory = getBeanFactory()
                for (listenerBeanName in applicationListenerBeans) {
                    try {
                        val listener = beanFactory.getBean(listenerBeanName, ApplicationListener::class.java)
                        if (!allListeners.contains(listener)) {
                            allListeners.add(listener)
                        }
                    } catch (ex: NoSuchBeanDefinitionException) {
                        // Singleton listener instance (without backing bean definition) disappeared -
                        // probably in the middle of the destruction phase
                    }
                }
            }
            return allListeners
        }
    }

    private class ListenerCacheKey(val eventType: ResolvableType, val sourceType: Class<*>?) :
        Comparable<ListenerCacheKey> {

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            return if (other !is ListenerCacheKey) {
                false
            } else eventType == other.eventType && ObjectUtil.nullSafeEquals(sourceType, other.sourceType)
        }

        override fun hashCode(): Int {
            return eventType.hashCode() * 29 + ObjectUtil.nullSafeHashCode(sourceType)
        }

        override fun toString(): String {
            return "ListenerCacheKey [eventType = $eventType, sourceType = $sourceType]"
        }

        override operator fun compareTo(other: ListenerCacheKey): Int {
            var result: Int = eventType.toString().compareTo(other.eventType.toString())
            if (result == 0) {
                if (sourceType == null) {
                    return if (other.sourceType == null) 0 else -1
                }
                if (other.sourceType == null) {
                    return 1
                }
                result = sourceType.name.compareTo(other.sourceType.name)
            }
            return result
        }

    }

}

