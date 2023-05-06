package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.factory.*
import com.hyosakura.liteioc.bean.factory.config.SingletonBeanRegistry
import com.hyosakura.liteioc.util.StringUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * @author LovesAsuna
 **/
open class DefaultSingletonBeanRegistry : SingletonBeanRegistry {

    companion object {

        private const val SUPPRESSED_EXCEPTIONS_LIMIT = 100

    }

    private val singletonObjects: MutableMap<String, Any> = ConcurrentHashMap(256)

    private val singletonFactories: MutableMap<String, ObjectFactory<*>> = HashMap(16)

    private val earlySingletonObjects: MutableMap<String, Any> = ConcurrentHashMap(16)

    private val registeredSingletons: MutableSet<String> = LinkedHashSet(256)

    private val singletonsCurrentlyInCreation = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>(16))

    private val inCreationCheckExclusions = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>(16))

    private var suppressedExceptions: MutableSet<Exception>? = null

    private val containedBeanMap: MutableMap<String, Set<String>> = ConcurrentHashMap(16)

    private val dependentBeanMap: MutableMap<String, MutableSet<String>> = ConcurrentHashMap(64)

    private val dependenciesForBeanMap: MutableMap<String, MutableSet<String>> = ConcurrentHashMap(64)

    private var singletonsCurrentlyInDestruction = false

    private val disposableBeans: MutableMap<String, Any> = LinkedHashMap()

    val logger: Logger = LoggerFactory.getLogger(javaClass)

    override fun registerSingleton(beanName: String, singletonObject: Any) {
        synchronized(singletonObjects) {
            // 根据beanName从单例池中拿到对象
            val oldObject = singletonObjects[beanName]
            check(oldObject == null) {
                "Could not register object [$singletonObject] under bean name '$beanName': there is already object [$oldObject] bound"
            }
            addSingleton(beanName, singletonObject)
        }
    }

    open fun destroySingletons() {
        if (logger.isTraceEnabled) {
            logger.trace("Destroying singletons in $this")
        }
        synchronized(singletonObjects) { singletonsCurrentlyInDestruction = true }
        var disposableBeanNames: Array<String>
        synchronized(disposableBeans) {
            disposableBeanNames = disposableBeans.keys.toTypedArray()
        }
        for (i in disposableBeanNames.indices.reversed()) {
            destroySingleton(disposableBeanNames[i])
        }
        containedBeanMap.clear()
        dependentBeanMap.clear()
        dependenciesForBeanMap.clear()
        clearSingletonCache()
    }

    open fun clearSingletonCache() {
        synchronized(singletonObjects) {
            singletonObjects.clear()
            singletonFactories.clear()
            earlySingletonObjects.clear()
            registeredSingletons.clear()
            singletonsCurrentlyInDestruction = false
        }
    }

    override fun getSingleton(beanName: String): Any? {
        return getSingleton(beanName, true)
    }

    override fun containsSingleton(beanName: String): Boolean {
        return singletonObjects.containsKey(beanName)
    }

    override fun getSingletonNames(): Array<String> {
        synchronized(singletonObjects) { return StringUtil.toStringArray(registeredSingletons) }
    }

    override fun getSingletonCount(): Int {
        synchronized(singletonObjects) { return registeredSingletons.size }
    }

    protected fun getSingleton(beanName: String, allowEarlyReference: Boolean): Any? {
        // Quick check for existing instance without full singleton lock
        var singletonObject = this.singletonObjects[beanName]
        // 判断当前单例bean是否正在创建中
        if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
            singletonObject = this.earlySingletonObjects[beanName]
            // 是否允许从singletonFactories中通过getObject拿到对象
            if (singletonObject == null && allowEarlyReference) {
                synchronized(this.singletonObjects) {

                    // Consistent creation of early reference within full singleton lock
                    singletonObject = this.singletonObjects[beanName]
                    if (singletonObject == null) {
                        singletonObject = earlySingletonObjects[beanName]
                        if (singletonObject == null) {
                            val singletonFactory = singletonFactories[beanName]
                            if (singletonFactory != null) {
                                singletonObject = singletonFactory.getObject()
                                // 从singletonFactories中移除，并放入earlySingletonObjects中，也就是二级缓存中
                                earlySingletonObjects[beanName] = singletonObject!!
                                singletonFactories.remove(beanName)
                            }
                        }
                    }
                }
            }
        }
        return singletonObject
    }

    open fun getSingleton(beanName: String, singletonFactory: ObjectFactory<*>): Any {
        synchronized(this.singletonObjects) {
            var singletonObject = this.singletonObjects[beanName]
            if (singletonObject == null) {
                if (this.singletonsCurrentlyInDestruction) {
                    throw BeanCreationNotAllowedException(
                        beanName,
                        "Singleton bean creation not allowed while singletons of this factory are in destruction " + "(Do not request a bean from a BeanFactory in a destroy method implementation!)"
                    )
                }
                if (logger.isDebugEnabled) {
                    logger.debug("Creating shared instance of singleton bean '$beanName'")
                }
                beforeSingletonCreation(beanName)
                var newSingleton = false
                val recordSuppressedExceptions = suppressedExceptions == null
                if (recordSuppressedExceptions) {
                    suppressedExceptions = LinkedHashSet<Exception>()
                }
                try {
                    singletonObject = singletonFactory.getObject()
                    newSingleton = true
                } catch (ex: IllegalStateException) {
                    // Has the singleton object implicitly appeared in the meantime ->
                    // if yes, proceed with it since the exception indicates that state.
                    singletonObject = this.singletonObjects[beanName]
                    if (singletonObject == null) {
                        throw ex
                    }
                } catch (ex: BeanCreationException) {
                    if (recordSuppressedExceptions) {
                        for (suppressedException in suppressedExceptions!!) {
                            ex.addRelatedCause(suppressedException)
                        }
                    }
                    throw ex
                } finally {
                    if (recordSuppressedExceptions) {
                        suppressedExceptions = null
                    }
                    afterSingletonCreation(beanName)
                }
                if (newSingleton) {
                    addSingleton(beanName, singletonObject!!)
                }
            }
            return singletonObject!!
        }
    }

    open fun onSuppressedException(ex: Exception) {
        synchronized(singletonObjects) {
            if (this.suppressedExceptions != null && this.suppressedExceptions!!.size < SUPPRESSED_EXCEPTIONS_LIMIT) {
                this.suppressedExceptions!!.add(ex)
            }
        }
    }

    open fun addSingletonFactory(beanName: String, singletonFactory: ObjectFactory<*>) {
        synchronized(singletonObjects) {
            if (!singletonObjects.containsKey(beanName)) {
                singletonFactories[beanName] = singletonFactory
                earlySingletonObjects.remove(beanName)
                registeredSingletons.add(beanName)
            }
        }
    }

    open fun registerDisposableBean(beanName: String, bean: DisposableBean) {
        synchronized(disposableBeans) {
            disposableBeans.put(beanName, bean)
        }
    }


    open fun registerDependentBean(beanName: String, dependentBeanName: String) {
        val canonicalName: String = beanName
        synchronized(dependentBeanMap) {
            val dependentBeans =
                dependentBeanMap.computeIfAbsent(
                    canonicalName
                ) { _: String ->
                    LinkedHashSet(
                        8
                    )
                }
            if (!dependentBeans.add(dependentBeanName)) {
                return
            }
        }
        synchronized(dependenciesForBeanMap) {
            val dependenciesForBean =
                dependenciesForBeanMap.computeIfAbsent(
                    dependentBeanName
                ) { _: String ->
                    LinkedHashSet(
                        8
                    )
                }
            dependenciesForBean.add(canonicalName)
        }
    }

    open fun hasDependentBean(beanName: String): Boolean {
        return dependentBeanMap.containsKey(beanName)
    }

    open fun getDependentBeans(beanName: String): Array<String> {
        val dependentBeans = dependentBeanMap[beanName] ?: return emptyArray()
        synchronized(dependentBeanMap) {
            return dependentBeans.toTypedArray()
        }
    }

    open fun beforeSingletonCreation(beanName: String) {
        if (!this.inCreationCheckExclusions.contains(beanName) && !singletonsCurrentlyInCreation.add(beanName)) {
            throw BeanCurrentlyInCreationException(beanName)
        }
    }

    open fun afterSingletonCreation(beanName: String) {
        if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.remove(beanName)) {
            throw IllegalStateException("Singleton '$beanName' isn't currently in creation")
        }
    }

    open fun setCurrentlyInCreation(beanName: String, inCreation: Boolean) {
        if (!inCreation) {
            inCreationCheckExclusions.add(beanName)
        } else {
            inCreationCheckExclusions.remove(beanName)
        }
    }

    open fun isCurrentlyInCreation(beanName: String): Boolean {
        return !this.inCreationCheckExclusions.contains(beanName) && isActuallyInCreation(beanName)
    }

    protected open fun isActuallyInCreation(beanName: String): Boolean {
        return isSingletonCurrentlyInCreation(beanName)
    }

    fun isSingletonCurrentlyInCreation(beanName: String): Boolean {
        return this.singletonsCurrentlyInCreation.contains(beanName)
    }

    protected fun addSingleton(beanName: String, singletonObject: Any) {
        synchronized(singletonObjects) {
            singletonObjects[beanName] = singletonObject
            this.singletonFactories.remove(beanName)
            this.earlySingletonObjects.remove(beanName)
            this.registeredSingletons.add(beanName)
        }
    }

    fun destroySingleton(beanName: String) {
        // Remove a registered singleton of the given name, if any.
        removeSingleton(beanName)

        // Destroy the corresponding DisposableBean instance.
        var disposableBean: DisposableBean?
        synchronized(this.disposableBeans) { disposableBean = this.disposableBeans.remove(beanName) as DisposableBean? }
        destroyBean(beanName, disposableBean)
    }

    protected open fun removeSingleton(beanName: String?) {
        synchronized(singletonObjects) {
            singletonObjects.remove(beanName)
            singletonFactories.remove(beanName)
            earlySingletonObjects.remove(beanName)
            registeredSingletons.remove(beanName)
        }
    }

    protected open fun destroyBean(beanName: String, bean: DisposableBean?) {
        // Trigger destruction of dependent beans first...
        var dependencies: Set<String>?
        synchronized(this.dependentBeanMap) {
            // Within full synchronization in order to guarantee a disconnected Set
            dependencies = this.dependentBeanMap.remove(beanName)
        }
        if (dependencies != null) {
            if (logger.isTraceEnabled) {
                logger.trace("Retrieved dependent beans for bean '$beanName': $dependencies")
            }
            for (dependentBeanName in dependencies!!) {
                destroySingleton(dependentBeanName)
            }
        }

        // Actually destroy the bean now...
        if (bean != null) {
            try {
                bean.destroy()
            } catch (ex: Throwable) {
                if (logger.isWarnEnabled) {
                    logger.warn("Destruction of bean with name '$beanName' threw an exception", ex)
                }
            }
        }

        // Trigger destruction of contained beans...
        var containedBeans: Set<String>?
        synchronized(this.containedBeanMap) {
            // Within full synchronization in order to guarantee a disconnected Set
            containedBeans = this.containedBeanMap.remove(beanName)
        }
        if (containedBeans != null) {
            for (containedBeanName in containedBeans!!) {
                destroySingleton(containedBeanName)
            }
        }

        // Remove destroyed bean from other beans' dependencies.
        synchronized(this.dependentBeanMap) {
            val it = this.dependentBeanMap.entries.iterator()
            while (it.hasNext()) {
                val (_, dependenciesToClean) = it.next()
                dependenciesToClean.remove(beanName)
                if (dependenciesToClean.isEmpty()) {
                    it.remove()
                }
            }
        }

        // Remove destroyed bean's prepared dependency information.
        this.dependenciesForBeanMap.remove(beanName)
    }

}