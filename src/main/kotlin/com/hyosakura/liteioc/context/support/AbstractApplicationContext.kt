package com.hyosakura.liteioc.context.support

import com.hyosakura.liteioc.bean.BeansException
import com.hyosakura.liteioc.bean.factory.BeanFactory
import com.hyosakura.liteioc.bean.factory.NoSuchBeanDefinitionException
import com.hyosakura.liteioc.bean.factory.ObjectProvider
import com.hyosakura.liteioc.bean.factory.config.ConfigurableListableBeanFactory
import com.hyosakura.liteioc.bean.factory.support.BeanDefinitionReader
import com.hyosakura.liteioc.context.*
import com.hyosakura.liteioc.context.ConfigurableApplicationContext.Companion.SHUTDOWN_HOOK_THREAD_NAME
import com.hyosakura.liteioc.context.event.*
import com.hyosakura.liteioc.core.ResolvableType
import com.hyosakura.liteioc.core.env.ConfigurableEnvironment
import com.hyosakura.liteioc.core.env.Environment
import com.hyosakura.liteioc.core.env.StandardEnvironment
import com.hyosakura.liteioc.core.io.DefaultResourceLoader
import com.hyosakura.liteioc.core.io.ResourceLoader
import com.hyosakura.liteioc.util.ObjectUtil
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author LovesAsuna
 **/
abstract class AbstractApplicationContext : DefaultResourceLoader, ConfigurableApplicationContext {

    lateinit var beanDefinitionReader: BeanDefinitionReader

    lateinit var configLocation: String

    private var applicationEventMulticaster: ApplicationEventMulticaster? = null

    private val applicationListeners: MutableSet<ApplicationListener<*>> = LinkedHashSet()

    private var earlyApplicationListeners: MutableSet<ApplicationListener<*>>? = null

    private var earlyApplicationEvents: MutableSet<ApplicationEvent>? = null

    protected val singletonObjects: MutableMap<String, Any?> = ConcurrentHashMap()

    private var parent: ApplicationContext? = null

    private var environment: ConfigurableEnvironment? = null

    private val active = AtomicBoolean()

    private val closed = AtomicBoolean()

    protected val logger = LoggerFactory.getLogger(javaClass)

    private var id: String = ObjectUtil.identityToString(this)

    private var displayName: String = ObjectUtil.identityToString(this)

    private var shutdownHook: Thread? = null

    private val startupShutdownMonitor = Any()

    constructor()

    constructor(parent: ApplicationContext?) {
        setParent(parent)
    }

    @Throws(IllegalStateException::class)
    protected abstract fun refreshBeanFactory()

    @Throws(IllegalStateException::class)
    abstract override fun getBeanFactory(): ConfigurableListableBeanFactory

    protected open fun obtainFreshBeanFactory(): ConfigurableListableBeanFactory {
        refreshBeanFactory()
        return getBeanFactory()
    }

    open fun getInternalParentBeanFactory(): BeanFactory? {
        return if (getParent() is ConfigurableApplicationContext) (getParent() as ConfigurableApplicationContext).getBeanFactory() else getParent()
    }

    override fun setParent(parent: ApplicationContext?) {
        this.parent = parent
        if (parent != null) {
            val parentEnvironment: Environment = parent.getEnvironment()
            if (parentEnvironment is ConfigurableEnvironment) {
                getEnvironment().merge(parentEnvironment)
            }
        }
    }

    override fun getParent(): ApplicationContext? {
        return parent
    }

    open fun onClose() {
        // For subclasses: do nothing by default.
    }

    override fun isActive(): Boolean {
        return this.active.get()
    }

    open fun assertBeanFactoryActive() {
        if (!this.active.get()) {
            if (this.closed.get()) {
                throw IllegalStateException(getDisplayName() + " has been closed already")
            } else {
                throw IllegalStateException(getDisplayName() + " has not been refreshed yet")
            }
        }
    }

    override fun setId(id: String) {
        this.id = id
    }

    override fun getId(): String = this.id

    open fun setDisplayName(displayName: String) {
        this.displayName = displayName
    }

    override fun setEnvironment(environment: ConfigurableEnvironment) {
        this.environment = environment
    }

    override fun addApplicationListener(listener: ApplicationListener<*>) {
        applicationEventMulticaster?.addApplicationListener(listener)
        this.applicationListeners.add(listener)
    }

    override fun registerShutdownHook() {
        if (this.shutdownHook == null) {
            // No shutdown hook registered yet.
            this.shutdownHook = object : Thread(SHUTDOWN_HOOK_THREAD_NAME) {
                override fun run() {
                    synchronized(startupShutdownMonitor) { doClose() }
                }
            }
            Runtime.getRuntime().addShutdownHook(this.shutdownHook)
        }
    }

    @Throws(BeansException::class)
    open fun <T> getBean(requiredType: Class<T>): T {
        assertBeanFactoryActive()
        return getBeanFactory().getBean(requiredType)
    }

    @Throws(BeansException::class)
    override fun <T> getBean(requiredType: Class<T>, vararg args: Any?): T {
        assertBeanFactoryActive()
        return getBeanFactory().getBean(requiredType, args)
    }

    override fun getBean(name: String, vararg args: Any?): Any {
        assertBeanFactoryActive()
        return getBeanFactory().getBean(name, args)
    }

    override fun <T> getBean(name: String, requiredType: Class<T>): T {
        assertBeanFactoryActive()
        return getBeanFactory().getBean(name, requiredType)
    }

    @Throws(NoSuchBeanDefinitionException::class)
    override fun getType(name: String): Class<*>? {
        assertBeanFactoryActive()
        return getBeanFactory().getType(name)
    }

    override fun <T> getBeanProvider(requiredType: Class<T>): ObjectProvider<T> {
        assertBeanFactoryActive()
        return getBeanFactory().getBeanProvider(requiredType)
    }

    override fun <T> getBeanProvider(requiredType: ResolvableType): ObjectProvider<T> {
        assertBeanFactoryActive()
        return getBeanFactory().getBeanProvider(requiredType)
    }

    override fun <T> getBeanProvider(requiredType: Class<T>, allowEagerInit: Boolean): ObjectProvider<T> {
        assertBeanFactoryActive()
        return getBeanFactory().getBeanProvider(requiredType, allowEagerInit)
    }

    override fun <T> getBeanProvider(requiredType: ResolvableType, allowEagerInit: Boolean): ObjectProvider<T> {
        assertBeanFactoryActive()
        return getBeanFactory().getBeanProvider(requiredType, allowEagerInit)
    }

    override fun getBeanNamesForType(
        type: Class<*>?,
        includeNonSingletons: Boolean,
        allowEagerInit: Boolean
    ): Array<String> {
        assertBeanFactoryActive()
        return getBeanFactory().getBeanNamesForType(type, includeNonSingletons, allowEagerInit)
    }

    override fun getBeanNamesForType(type: ResolvableType): Array<String> {
        assertBeanFactoryActive()
        return getBeanFactory().getBeanNamesForType(type)
    }

    override fun isTypeMatch(name: String, typeToMatch: Class<*>): Boolean {
        assertBeanFactoryActive()
        return getBeanFactory().isTypeMatch(name, typeToMatch)
    }

    override fun isTypeMatch(name: String, typeToMatch: ResolvableType): Boolean {
        assertBeanFactoryActive()
        return getBeanFactory().isTypeMatch(name, typeToMatch)
    }

    override fun containsBean(name: String): Boolean = getBeanFactory().containsBean(name)

    @Throws(NoSuchBeanDefinitionException::class)
    override fun isSingleton(name: String): Boolean {
        assertBeanFactoryActive()
        return getBeanFactory().isSingleton(name)
    }

    open fun destroyBeans() {
        getBeanFactory().destroySingletons()
    }

    override fun getDisplayName(): String = this.displayName

    @Throws(Exception::class)
    override fun refresh() {
        synchronized(this.startupShutdownMonitor) {
            prepareRefresh()
            val beanFactory = obtainFreshBeanFactory()
            prepareBeanFactory(beanFactory)
            try {
                invokeClassScan(beanFactory)
                initApplicationEventMulticaster()
                onRefresh()

                // Check for listener beans and register them.
                registerListeners()
                finishBeanFactoryInitialization(beanFactory)
                finishRefresh()
            } catch (ex: BeansException) {
                logger.warn("Exception encountered during context initialization - cancelling refresh attempt: $ex")
                destroyBeans()
                cancelRefresh(ex)
                throw ex
            }
        }
    }

    open fun prepareRefresh() {
        this.closed.set(false)
        this.active.set(true)

        if (logger.isDebugEnabled) {
            if (logger.isTraceEnabled) {
                logger.trace("Refreshing $this")
            } else {
                logger.debug("Refreshing " + getDisplayName())
            }
        }

        initPropertySources()

        // Store pre-refresh ApplicationListeners...
        if (this.earlyApplicationListeners == null) {
            this.earlyApplicationListeners = LinkedHashSet(this.applicationListeners)
        } else {
            // Reset local application listeners to pre-refresh state.
            this.applicationListeners.clear()
            this.applicationListeners.addAll(this.earlyApplicationListeners!!)
        }

        this.earlyApplicationEvents = LinkedHashSet()
    }

    open fun initPropertySources() {
        // For subclasses: do nothing by default.
    }

    open fun initApplicationEventMulticaster() {
        this.applicationEventMulticaster = SimpleApplicationEventMulticaster(getBeanFactory())
    }

    open fun invokeClassScan(beanFactory: ConfigurableListableBeanFactory) {
        ClassScanner.invokeScan(beanFactory)
    }

    fun finishBeanFactoryInitialization(beanFactory: ConfigurableListableBeanFactory) {
        beanFactory.preInstantiateSingletons()
    }

    open fun finishRefresh() {
        publishEvent(ContextRefreshedEvent(this))
    }

    open fun cancelRefresh(ex: BeansException) {
        active.set(false)
    }

    @Throws(BeansException::class)
    protected open fun onRefresh() {
        // For subclasses: do nothing by default.
    }

    protected open fun registerListeners() {
        // Register statically specified listeners first.
        for (listener in this.applicationListeners) {
            getApplicationEventMulticaster().addApplicationListener(listener)
        }

        // Do not initialize FactoryBeans here: We need to leave all regular beans
        // uninitialized to let post-processors apply to them!
        val listenerBeanNames = getBeanNamesForType(ApplicationListener::class.java, true, false)
        for (listenerBeanName in listenerBeanNames) {
            getApplicationEventMulticaster().addApplicationListenerBean(listenerBeanName)
        }

        // Publish early application events now that we finally have a multicaster...
        val earlyEventsToProcess = earlyApplicationEvents
        this.earlyApplicationEvents = null
        if (!earlyEventsToProcess.isNullOrEmpty()) {
            for (earlyEvent in earlyEventsToProcess) {
                getApplicationEventMulticaster().multicastEvent(earlyEvent)
            }
        }
    }

    open fun prepareBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
        beanFactory.registerResolvableDependency(BeanFactory::class.java, beanFactory)
        beanFactory.registerResolvableDependency(ResourceLoader::class.java, this)
        beanFactory.registerResolvableDependency(ApplicationEventPublisher::class.java, this)
        beanFactory.registerResolvableDependency(ApplicationContext::class.java, this)
    }

    override fun containsBeanDefinition(beanName: String): Boolean {
        return getBeanFactory().containsBeanDefinition(beanName)
    }

    override fun getBeanDefinitionCount(): Int {
        return getBeanFactory().getBeanDefinitionCount()
    }

    override fun getBeanDefinitionNames(): Array<String> {
        return getBeanFactory().getBeanDefinitionNames()
    }

    override fun getEnvironment(): ConfigurableEnvironment {
        if (this.environment == null) {
            this.environment = createEnvironment()
        }
        return this.environment!!
    }

    open fun createEnvironment(): ConfigurableEnvironment {
        return StandardEnvironment()
    }

    open fun doClose() {
        // Check whether an actual close attempt is necessary...
        if (active.get() && closed.compareAndSet(false, true)) {
            if (logger.isDebugEnabled) {
                logger.debug("Closing $this")
            }
            try {
                // Publish shutdown event.
                publishEvent(ContextClosedEvent(this))
            } catch (ex: Throwable) {
                logger.warn("Exception thrown from ApplicationListener handling ContextClosedEvent", ex)
            }

            // Destroy all cached singletons in the context's BeanFactory.
            destroyBeans()

            // Close the state of this context itself.
            closeBeanFactory()

            // Let subclasses do some final clean-up if they wish...
            onClose()

            // Reset local application listeners to pre-refresh state.
            if (this.earlyApplicationListeners != null) {
                applicationListeners.clear()
                applicationListeners.addAll(this.earlyApplicationListeners!!)
            }

            // Switch to inactive.
            active.set(false)
        }
    }

    abstract fun closeBeanFactory()

    override fun publishEvent(event: Any) {
        publishEvent(event, null)
    }

    override fun publishEvent(event: ApplicationEvent) {
        publishEvent(event, null)
    }

    open fun publishEvent(event: Any, eventType: ResolvableType?) {
        var eventType = eventType
        // Decorate event as an ApplicationEvent if necessary
        val applicationEvent: ApplicationEvent
        if (event is ApplicationEvent) {
            applicationEvent = event
        } else {
            applicationEvent = PayloadApplicationEvent(this, event, eventType)
            if (eventType == null) {
                eventType = (applicationEvent as PayloadApplicationEvent<*>).payloadType
            }
        }

        // Multicast right now if possible - or lazily once the multicaster is initialized
        if (this.earlyApplicationEvents != null) {
            this.earlyApplicationEvents!!.add(applicationEvent)
        } else {
            getApplicationEventMulticaster().multicastEvent(applicationEvent, eventType)
        }

        // Publish event via parent context as well...
        if (parent != null) {
            if (parent is AbstractApplicationContext) {
                (parent as AbstractApplicationContext?)!!.publishEvent(event, eventType)
            } else {
                parent!!.publishEvent(event)
            }
        }
    }

    @Throws(IllegalStateException::class)
    open fun getApplicationEventMulticaster(): ApplicationEventMulticaster {
        checkNotNull(applicationEventMulticaster) {
            "ApplicationEventMulticaster not initialized - call 'refresh' before multicasting events via the context: $this"
        }
        return applicationEventMulticaster as ApplicationEventMulticaster
    }

    override fun close() {
        synchronized(startupShutdownMonitor) {
            doClose()
            // If we registered a JVM shutdown hook, we don't need it anymore now:
            // We've already explicitly closed the context.
            if (shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook)
                } catch (ex: IllegalStateException) {
                    // ignore - VM is already shutting down
                }
            }
        }
    }

}