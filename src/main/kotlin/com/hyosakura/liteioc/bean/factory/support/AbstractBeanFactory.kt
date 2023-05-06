package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.*
import com.hyosakura.liteioc.bean.BeanDefinition.Companion.SCOPE_SINGLETON
import com.hyosakura.liteioc.bean.factory.*
import com.hyosakura.liteioc.bean.factory.config.ConfigurableBeanFactory
import com.hyosakura.liteioc.bean.factory.config.Scope
import com.hyosakura.liteioc.core.DecoratingClassLoader
import com.hyosakura.liteioc.core.NamedThreadLocal
import com.hyosakura.liteioc.core.ResolvableType
import com.hyosakura.liteioc.core.convert.ConversionService
import com.hyosakura.liteioc.util.ClassUtil
import com.hyosakura.liteioc.util.ObjectUtil
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * @author LovesAsuna
 **/
abstract class AbstractBeanFactory : DefaultSingletonBeanRegistry, ConfigurableBeanFactory {

    private var parentBeanFactory: BeanFactory? = null

    private var beanClassLoader: ClassLoader? = ClassUtil.getDefaultClassLoader()

    private val scopes: MutableMap<String, Scope> = LinkedHashMap<String, Scope>(8)

    private val mergedBeanDefinitions: MutableMap<String, RootBeanDefinition> = ConcurrentHashMap(256)

    private val cacheBeanMetadata = true

    private var conversionService: ConversionService? = null

    private val prototypesCurrentlyInCreation: ThreadLocal<Any> =
        NamedThreadLocal("Prototype beans currently in creation")

    private var typeConverter: TypeConverter? = null

    private val alreadyCreated = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>(256))

    private val dependentBeanMap: MutableMap<String, MutableSet<String>> = ConcurrentHashMap(64)

    private val dependenciesForBeanMap: MutableMap<String, MutableSet<String>> = ConcurrentHashMap(64)

    private var tempClassLoader: ClassLoader? = null

    constructor()

    constructor(parentBeanFactory: BeanFactory) {
        this.parentBeanFactory = parentBeanFactory
    }

    open fun setBeanClassLoader(beanClassLoader: ClassLoader?) {
        this.beanClassLoader = beanClassLoader ?: ClassUtil.getDefaultClassLoader()
    }

    override fun getBeanClassLoader(): ClassLoader? {
        return beanClassLoader
    }

    open fun setTempClassLoader(tempClassLoader: ClassLoader?) {
        this.tempClassLoader = tempClassLoader
    }

    open fun getTempClassLoader(): ClassLoader? {
        return this.tempClassLoader
    }

    override fun <T> getBean(name: String, requiredType: Class<T>): T {
        return doGetBean(name, requiredType, null, false)
    }

    override fun getBean(name: String, vararg args: Any?): Any {
        return doGetBean(name, null, if (args.isEmpty()) null else arrayOf(*args), false)
    }

    @Throws(BeansException::class)
    open fun <T> getBean(name: String, requiredType: Class<T>?, vararg args: Any?): T {
        return doGetBean(name, requiredType, arrayOf(*args), false)
    }

    @Throws(NoSuchBeanDefinitionException::class)
    override fun getType(name: String): Class<*>? {
        return getType(name, true)
    }

    @Throws(NoSuchBeanDefinitionException::class)
    open fun getType(name: String, allowFactoryBeanInit: Boolean): Class<*>? {
        val beanName: String = name

        // Check manually registered singletons.
        val beanInstance = getSingleton(beanName, false)
        if (beanInstance != null && beanInstance.javaClass != NullBean::class.java) {
            return beanInstance.javaClass
        }

        // No singleton instance found -> check bean definition.
        val parentBeanFactory = getParentBeanFactory()
        if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
            // No bean definition found in this factory -> delegate to parent.
            return parentBeanFactory.getType(name)
        }
        val mbd = getMergedLocalBeanDefinition(beanName)

        // Check decorated bean definition, if any: We assume it'll be easier
        // to determine the decorated bean's type than the proxy's type.
        val dbd = mbd.getDecoratedDefinition()
        if (dbd != null) {
            val tbd = getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd)
            val targetClass = predictBeanType(dbd.getBeanName(), tbd)
            if (targetClass != null) {
                return targetClass
            }
        }

        return predictBeanType(beanName, mbd)
    }

    @Suppress("UNCHECKED_CAST")
    protected fun <T> doGetBean(
        name: String, requiredType: Class<*>?, args: Array<Any?>?, typeCheckOnly: Boolean
    ): T {
        val beanName: String = name
        val beanInstance: Any?

        // Eagerly check singleton cache for manually registered singletons.
        var sharedInstance = getSingleton(beanName)
        if (sharedInstance != null && args == null) {
            if (logger.isTraceEnabled) {
                if (isSingletonCurrentlyInCreation(beanName)) {
                    logger.trace(
                        "Returning eagerly cached instance of singleton bean '" + beanName +
                                "' that is not fully initialized yet - a consequence of a circular reference"
                    )
                } else {
                    logger.trace("Returning cached instance of singleton bean '$beanName'")
                }
            }
            beanInstance = getObjectForBeanInstance(sharedInstance, name, beanName, null)
        } else {
            // Fail if we're already creating this bean instance:
            // We're assumably within a circular reference.
            if (isPrototypeCurrentlyInCreation(beanName)) {
                throw BeanCurrentlyInCreationException(beanName)
            }
            // Check if bean definition exists in this factory.
            val parentBeanFactory = getParentBeanFactory()
            if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
                // Not found -> check parent.
                return if (parentBeanFactory is AbstractBeanFactory) {
                    parentBeanFactory.doGetBean(name, requiredType, args, typeCheckOnly)
                } else if (args != null) {
                    // Delegation to parent with explicit args.
                    parentBeanFactory.getBean(name, args, typeCheckOnly) as T
                } else if (requiredType != null) {
                    // No args -> delegate to standard getBean method.
                    parentBeanFactory.getBean(name, requiredType) as T
                } else {
                    parentBeanFactory.getBean(name) as T
                }
            }

            if (!typeCheckOnly) {
                markBeanAsCreated(beanName)
            }

            try {
                val mbd = getMergedLocalBeanDefinition(beanName)
                checkMergedBeanDefinition(mbd, beanName, args)

                // Guarantee initialization of beans that the current bean depends on.
                val dependsOn = mbd.getDependsOn()
                if (!dependsOn.isNullOrEmpty()) {
                    for (dep in dependsOn) {
                        if (isDependent(beanName, dep)) {
                            throw BeanCreationException(
                                beanName, "Circular depends-on relationship between '$beanName' and '$dep'"
                            )
                        }
                        registerDependentBean(dep, beanName)
                        try {
                            getBean(dep)
                        } catch (ex: NoSuchBeanDefinitionException) {
                            throw BeanCreationException(beanName, "'$beanName' depends on missing bean '$dep'", ex)
                        }
                    }
                }

                // Create bean instance.
                if (mbd.isSingleton()) {
                    sharedInstance = getSingleton(beanName) {
                        try {
                            return@getSingleton createBean(beanName, mbd, args)
                        } catch (ex: BeansException) {
                            // Explicitly remove instance from singleton cache: It might have been put there
                            // eagerly by the creation process, to allow for circular reference resolution.
                            // Also remove any beans that received a temporary reference to the bean.
                            destroySingleton(beanName)
                            throw ex
                        }
                    }
                    beanInstance = getObjectForBeanInstance(sharedInstance, name, beanName, mbd)
                } else if (mbd.isPrototype()) {
                    // It's a prototype -> create a new instance.
                    val prototypeInstance: Any
                    try {
                        beforePrototypeCreation(beanName)
                        prototypeInstance = createBean(beanName, mbd, args)
                    } finally {
                        afterPrototypeCreation(beanName)
                    }
                    beanInstance = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd)
                } else {
                    val scopeName = mbd.getScope()
                    if (scopeName.isNullOrEmpty()) {
                        throw IllegalStateException("No scope name defined for bean '$beanName'")
                    }
                    val scope = this.scopes[scopeName]
                        ?: throw IllegalStateException("No Scope registered for scope name '$scopeName'")
                    try {
                        val scopedInstance = scope.get(beanName) {
                            beforePrototypeCreation(beanName)
                            try {
                                return@get createBean(beanName, mbd, args)
                            } finally {
                                afterPrototypeCreation(beanName)
                            }
                        }
                        beanInstance = getObjectForBeanInstance(scopedInstance, name, beanName, mbd)
                    } catch (ex: IllegalStateException) {
                        throw ScopeNotActiveException(beanName, scopeName, ex)
                    }
                }
            } catch (ex: BeansException) {
                cleanupAfterBeanCreationFailure(beanName)
                throw ex
            }
        }
        return adaptBeanInstance(name, beanInstance, requiredType)
    }

    @Suppress("UNCHECKED_CAST")
    open fun <T> adaptBeanInstance(name: String, bean: Any, requiredType: Class<*>?): T {
        // Check if required type matches the type of the actual bean instance.
        return if (requiredType != null && !requiredType.isInstance(bean)) {
            try {
                val convertedBean =
                    getTypeConverter().convertIfNecessary(bean, requiredType) ?: throw BeanNotOfRequiredTypeException(
                        name, requiredType, bean.javaClass
                    )
                convertedBean as T
            } catch (ex: TypeMismatchException) {
                if (logger.isTraceEnabled) {
                    logger.trace(
                        "Failed to convert bean '$name' to required type '" + ClassUtil.getQualifiedName(
                            requiredType
                        ) + "'", ex
                    )
                }
                throw BeanNotOfRequiredTypeException(name, requiredType, bean.javaClass)
            }
        } else bean as T
    }

    override fun setParentBeanFactory(parentBeanFactory: BeanFactory?) {
        check(!(this.parentBeanFactory != null && this.parentBeanFactory !== parentBeanFactory)) { "Already associated with parent BeanFactory: " + this.parentBeanFactory }
        check(this !== parentBeanFactory) { "Cannot set parent bean factory to self" }
        this.parentBeanFactory = parentBeanFactory
    }

    protected fun isPrototypeCurrentlyInCreation(beanName: String): Boolean {
        val curVal = this.prototypesCurrentlyInCreation.get()
        return (curVal != null && (curVal.equals(beanName) || run {
            if (curVal is Set<*>) {
                return@run curVal.contains(beanName)
            }
            return@run false
        }))
    }

    @Throws(NoSuchBeanDefinitionException::class)
    override fun isSingleton(name: String): Boolean {
        val beanName: String = name
        val beanInstance = getSingleton(beanName, false)
        if (beanInstance != null) {
            return true
        }

        // No singleton instance found -> check bean definition.
        val parentBeanFactory = getParentBeanFactory()
        if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
            // No bean definition found in this factory -> delegate to parent.
            return parentBeanFactory.isSingleton(name)
        }
        val mbd = getMergedLocalBeanDefinition(beanName)

        // In case of FactoryBean, return singleton status of created object if not a dereference.
        return mbd.isSingleton()
    }

    override fun getTypeConverter(): TypeConverter {
        val customConverter = getCustomTypeConverter()
        return if (customConverter != null) {
            customConverter
        } else {
            // Build default TypeConverter, registering custom editors.
            val typeConverter = SimpleTypeConverter()
            typeConverter.setConversionService(getConversionService())
//            registerCustomEditors(typeConverter)
            typeConverter
        }
    }

    fun getConversionService(): ConversionService? = this.conversionService

    fun setConversionService(conversionService: ConversionService?) {
        this.conversionService = conversionService
    }

    protected open fun isDependent(beanName: String, dependentBeanName: String): Boolean {
        synchronized(this.dependentBeanMap) { return isDependent(beanName, dependentBeanName, null) }
    }

    @Throws(BeanCreationException::class)
    protected abstract fun createBean(beanName: String, mbd: RootBeanDefinition, args: Array<Any?>?): Any

    override fun registerDependentBean(beanName: String, dependentBeanName: String) {
        val canonicalName = beanName
        synchronized(dependentBeanMap) {
            val dependentBeans: MutableSet<String> = dependentBeanMap.computeIfAbsent(canonicalName) {
                LinkedHashSet<String>(8)
            }
            if (!dependentBeans.add(dependentBeanName)) {
                return
            }
        }
        synchronized(this.dependenciesForBeanMap) {
            val dependenciesForBean: MutableSet<String> =
                this.dependenciesForBeanMap.computeIfAbsent(dependentBeanName) {
                    LinkedHashSet(8)
                }
            dependenciesForBean.add(canonicalName)
        }
    }

    override fun containsBean(name: String): Boolean {
        if (containsSingleton(name) || containsBeanDefinition(name)) {
            return true
        }
        // Not found -> check parent.
        val parentBeanFactory = getParentBeanFactory()
        return parentBeanFactory != null && parentBeanFactory.containsBean(name)
    }

    @Throws(NoSuchBeanDefinitionException::class)
    override fun isTypeMatch(name: String, typeToMatch: Class<*>): Boolean {
        return isTypeMatch(name, ResolvableType.forRawClass(typeToMatch))
    }

    @Throws(NoSuchBeanDefinitionException::class)
    override fun isTypeMatch(name: String, typeToMatch: ResolvableType): Boolean {
        return isTypeMatch(name, typeToMatch, true)
    }

    @Throws(NoSuchBeanDefinitionException::class)
    protected open fun isTypeMatch(name: String, typeToMatch: ResolvableType, allowFactoryBeanInit: Boolean): Boolean {
        val beanName: String = name

        // Check manually registered singletons.
        val beanInstance = getSingleton(beanName, false)
        if (beanInstance != null && beanInstance.javaClass != NullBean::class.java) {
            if (typeToMatch.isInstance(beanInstance)) {
                // Direct match for exposed instance?
                return true
            } else if (typeToMatch.hasGenerics() && containsBeanDefinition(beanName)) {
                // Generics potentially only match on the target class, not on the proxy...
                val mbd = getMergedLocalBeanDefinition(beanName)
                val targetType = mbd.getTargetType()
                if (targetType != null && targetType != ClassUtil.getUserClass(beanInstance)) {
                    // Check raw class match as well, making sure it's exposed on the proxy.
                    val classToMatch = typeToMatch.resolve()
                    if (classToMatch != null && !classToMatch.isInstance(beanInstance)) {
                        return false
                    }
                    if (typeToMatch.isAssignableFrom(targetType)) {
                        return true
                    }
                }
                val resolvableType = mbd.targetType
                return resolvableType != null && typeToMatch.isAssignableFrom(resolvableType)
            }
            return false
        } else if (containsSingleton(beanName) && !containsBeanDefinition(beanName)) {
            // null instance registered
            return false
        }

        // No singleton instance found -> check bean definition.
        val parentBeanFactory = getParentBeanFactory()
        if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
            // No bean definition found in this factory -> delegate to parent.
            return parentBeanFactory.isTypeMatch(name, typeToMatch)
        }

        // Retrieve corresponding bean definition.
        val mbd = getMergedLocalBeanDefinition(beanName)
        val dbd = mbd.getDecoratedDefinition()

        // Setup the types that we want to match against
        val classToMatch = typeToMatch.resolve() ?: return false

        // Attempt to predict the bean type
        // If we couldn't use the target type, try regular prediction.
        val predictedType = predictBeanType(beanName, mbd, classToMatch) ?: return false

        // Attempt to get the actual ResolvableType for the bean.
        var beanType: ResolvableType? = null

        // We don't have an exact type but if bean definition target type or the factory
        // method return type matches the predicted type then we can use that.
        val definedType = mbd.targetType
        if (definedType != null && definedType.resolve() == predictedType) {
            beanType = definedType
        }

        // If we have a bean type use it so that generics are considered
        return if (beanType != null) {
            typeToMatch.isAssignableFrom(beanType)
            // If we don't have a bean type, fallback to the predicted type
        } else typeToMatch.isAssignableFrom(predictedType)
    }

    private fun isDependent(
        beanName: String, dependentBeanName: String, alreadySeen: MutableSet<String>?
    ): Boolean {
        if (alreadySeen != null && alreadySeen.contains(beanName)) {
            return false
        }
        val dependentBeans = this.dependentBeanMap[beanName] ?: return false
        if (dependentBeans.contains(dependentBeanName)) {
            return true
        }
        for (transitiveDependency in dependentBeans) {
            val alreadySeen = alreadySeen ?: HashSet()
            alreadySeen.add(beanName)
            if (isDependent(transitiveDependency, dependentBeanName, alreadySeen)) {
                return true
            }
        }
        return false
    }

    @Throws(BeanDefinitionStoreException::class)
    protected open fun checkMergedBeanDefinition(
        mbd: RootBeanDefinition, beanName: String, args: Array<out Any?>?
    ) {
        if (mbd.isAbstract()) {
            throw BeanIsAbstractException(beanName)
        }
    }

    @Throws(BeanDefinitionStoreException::class)
    protected fun getMergedLocalBeanDefinition(beanName: String): RootBeanDefinition {
        return mergedBeanDefinitions[beanName] ?: getMergedBeanDefinition(beanName, getBeanDefinition(beanName), null)
    }

    open fun markBeanAsCreated(beanName: String?) {
        if (!this.alreadyCreated.contains(beanName)) {
            synchronized(mergedBeanDefinitions) {
                if (!this.alreadyCreated.contains(beanName)) {
                    // Let the bean definition get re-merged now that we're actually creating
                    // the bean... just in case some of its metadata changed in the meantime.
                    clearMergedBeanDefinition(beanName)
                    this.alreadyCreated.add(beanName)
                }
            }
        }
    }

    open fun clearMergedBeanDefinition(beanName: String?) {
        val bd = mergedBeanDefinitions[beanName]
        if (bd != null) {
            bd.stale = true
        }
    }

    @Throws(BeanDefinitionStoreException::class)
    fun getMergedBeanDefinition(
        beanName: String, bd: BeanDefinition, containingBd: BeanDefinition?
    ): RootBeanDefinition {
        synchronized(this.mergedBeanDefinitions) {
            var mbd: RootBeanDefinition? = null
            var previous: RootBeanDefinition? = null


            if (containingBd == null) {
                mbd = this.mergedBeanDefinitions[beanName]
            }

            if (mbd == null || mbd.stale) {
                previous = mbd
                if (bd.getParentName() == null) {
                    mbd = if (bd is RootBeanDefinition) {
                        bd.cloneBeanDefinition()
                    } else {
                        RootBeanDefinition(bd)
                    }
                } else {
                    val pbd: BeanDefinition?
                    try {
                        val parentBeanName = bd.getParentName()!!
                        pbd = if (beanName != parentBeanName) {
                            getMergedLocalBeanDefinition(parentBeanName)
                        } else {
                            val parent = getParentBeanFactory()
                            if (parent is ConfigurableBeanFactory) {
                                parent.getMergedBeanDefinition(parentBeanName)
                            } else {
                                throw NoSuchBeanDefinitionException(
                                    parentBeanName,
                                    "Parent name '$parentBeanName' is equal to bean name '$beanName': cannot be resolved without a ConfigurableBeanFactory parent"
                                )
                            }
                        }
                    } catch (ex: NoSuchBeanDefinitionException) {
                        throw BeanDefinitionStoreException(
                            "Could not resolve parent bean definition '" + bd.getParentName() + "'", ex
                        )
                    }
                    mbd = RootBeanDefinition(pbd)
                    mbd.overrideFrom(bd)
                }

                if (mbd.getScope().isNullOrEmpty()) {
                    mbd.setScope(SCOPE_SINGLETON)
                }

                if (containingBd != null && !containingBd.isSingleton() && mbd.isSingleton()) {
                    mbd.setScope(containingBd.getScope())
                }

                if (containingBd == null && isCacheBeanMetadata()) {
                    this.mergedBeanDefinitions[beanName] = mbd
                }
            }
            if (previous != null) {
                copyRelevantMergedBeanDefinitionCaches(previous, mbd)
            }
            return mbd
        }
    }

    open fun setTypeConverter(typeConverter: TypeConverter) {
        this.typeConverter = typeConverter
    }

    open fun getCustomTypeConverter(): TypeConverter? {
        return typeConverter
    }

    private fun copyRelevantMergedBeanDefinitionCaches(previous: RootBeanDefinition, mbd: RootBeanDefinition) {
        if (ObjectUtil.nullSafeEquals(mbd.getBeanClassName(), previous.getBeanClassName())) {
            mbd.resolvedTargetType = previous.resolvedTargetType
        }
    }

    override fun getMergedBeanDefinition(beanName: String): BeanDefinition {
        if (!containsBeanDefinition(beanName)) {
            val parent = getParentBeanFactory()
            if (parent is ConfigurableBeanFactory) {
                return parent.getMergedBeanDefinition(beanName)
            }
        }
        return getMergedLocalBeanDefinition(beanName)
    }

    open fun requiresDestruction(bean: Any, mbd: RootBeanDefinition): Boolean {
        return bean.javaClass != NullBean::class.java && bean is DisposableBean
    }

    open fun registerDisposableBeanIfNecessary(beanName: String, bean: Any, mbd: RootBeanDefinition) {
        if (!mbd.isPrototype() && requiresDestruction(bean, mbd)) {
            if (mbd.isSingleton()) {
                // Register a DisposableBean implementation that performs all destruction
                // work for the given bean: DestructionAwareBeanPostProcessors,
                // DisposableBean interface, custom destroy method.
                registerDisposableBean(
                    beanName, DisposableBeanAdapter(bean, beanName, mbd)
                )
            } else {
                // A bean with a custom scope...
                val scope = scopes[mbd.getScope()]
                    ?: throw IllegalStateException("No Scope registered for scope name '${mbd.getScope()}'")
                scope.registerDestructionCallback(beanName, DisposableBeanAdapter(bean, beanName, mbd))
            }
        }
    }

    abstract fun containsBeanDefinition(beanName: String): Boolean

    override fun isCacheBeanMetadata(): Boolean = this.cacheBeanMetadata

    @Throws(BeansException::class)
    abstract fun getBeanDefinition(beanName: String): BeanDefinition

    fun getParentBeanFactory() = parentBeanFactory

    open fun hasBeanCreationStarted(): Boolean {
        return alreadyCreated.isNotEmpty()
    }

    @Throws(CannotLoadBeanClassException::class)
    open fun resolveBeanClass(
        mbd: RootBeanDefinition, beanName: String, vararg typesToMatch: Class<*>
    ): Class<*>? {
        return try {
            if (mbd.hasBeanClass()) {
                mbd.getBeanClass()
            } else doResolveBeanClass(mbd, *typesToMatch)
        } catch (ex: ClassNotFoundException) {
            throw CannotLoadBeanClassException(beanName, mbd.getBeanClassName(), ex)
        } catch (err: LinkageError) {
            throw CannotLoadBeanClassException(beanName, mbd.getBeanClassName(), err)
        }
    }

    @Throws(ClassNotFoundException::class)
    private fun doResolveBeanClass(mbd: RootBeanDefinition, vararg typesToMatch: Class<*>): Class<*>? {
        var dynamicLoader = beanClassLoader
        var freshResolve = false

        if (typesToMatch.isNotEmpty()) {
            // When just doing type checks (i.e. not creating an actual instance yet),
            // use the specified temporary class loader (e.g. in a weaving scenario).
            if (tempClassLoader != null) {
                dynamicLoader = tempClassLoader
                freshResolve = true
                if (tempClassLoader is DecoratingClassLoader) {
                    for (typeToMatch in typesToMatch) {
                        (tempClassLoader as DecoratingClassLoader).excludeClass(typeToMatch.name)
                    }
                }
            }
        }

        val className = mbd.getBeanClassName()
        if (className != null) {
            if (freshResolve) {
                // When resolving against a temporary class loader, exit early in order
                // to avoid storing the resolved Class in the bean definition.
                if (dynamicLoader != null) {
                    try {
                        return dynamicLoader.loadClass(className)
                    } catch (ex: ClassNotFoundException) {
                        if (logger.isTraceEnabled) {
                            logger.trace("Could not load class [$className] from $dynamicLoader: $ex")
                        }
                    }
                }
                return ClassUtil.forName(className, dynamicLoader)
            }
        }

        // Resolve regularly, caching the result in the BeanDefinition...
        return mbd.resolveBeanClass(beanClassLoader)
    }

    open fun predictBeanType(
        beanName: String, mbd: RootBeanDefinition, vararg typesToMatch: Class<*>
    ): Class<*>? {
        val targetType = mbd.getTargetType()
        if (targetType != null) {
            return targetType
        }
        return resolveBeanClass(mbd, beanName, *typesToMatch)
    }

    open fun getObjectForBeanInstance(
        beanInstance: Any, name: String, beanName: String, mbd: RootBeanDefinition?
    ): Any {

        // now don't support FactoryBean
        return beanInstance
    }

    open fun cleanupAfterBeanCreationFailure(beanName: String?) {
        synchronized(mergedBeanDefinitions) { alreadyCreated.remove(beanName) }
    }

    @Suppress("UNCHECKED_CAST")
    open fun beforePrototypeCreation(beanName: String) {
        when (val curVal = this.prototypesCurrentlyInCreation.get()) {
            null -> {
                this.prototypesCurrentlyInCreation.set(beanName)
            }

            is String -> {
                val beanNameSet = HashSet<String>(2)
                beanNameSet.add(curVal)
                beanNameSet.add(beanName)
                this.prototypesCurrentlyInCreation.set(beanNameSet)
            }

            else -> {
                val beanNameSet = curVal as MutableSet<String>
                beanNameSet.add(beanName)
            }
        }
    }

    protected fun afterPrototypeCreation(beanName: String) {
        val curVal = this.prototypesCurrentlyInCreation.get()
        if (curVal is String) {
            this.prototypesCurrentlyInCreation.remove()
        } else if (curVal is MutableSet<*>) {
            curVal.remove(beanName)
            if (curVal.isEmpty()) {
                this.prototypesCurrentlyInCreation.remove()
            }
        }
    }

    open fun removeSingletonIfCreatedForTypeCheckOnly(beanName: String): Boolean {
        return if (!alreadyCreated.contains(beanName)) {
            removeSingleton(beanName)
            true
        } else {
            false
        }
    }

}