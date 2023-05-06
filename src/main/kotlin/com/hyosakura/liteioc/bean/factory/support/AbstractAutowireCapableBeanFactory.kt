package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.aop.autoproxy.AutoProxyCreator
import com.hyosakura.liteioc.bean.*
import com.hyosakura.liteioc.bean.factory.*
import com.hyosakura.liteioc.bean.factory.annotation.AutowiredAnnotationBeanPostProcessor
import com.hyosakura.liteioc.bean.factory.config.AutowireCapableBeanFactory
import com.hyosakura.liteioc.bean.factory.config.AutowireCapableBeanFactory.Companion.AUTOWIRE_BY_NAME
import com.hyosakura.liteioc.bean.factory.config.AutowireCapableBeanFactory.Companion.AUTOWIRE_BY_TYPE
import com.hyosakura.liteioc.bean.factory.config.AutowireCapableBeanFactory.Companion.AUTOWIRE_CONSTRUCTOR
import com.hyosakura.liteioc.bean.factory.config.ConfigurableListableBeanFactory
import com.hyosakura.liteioc.bean.factory.config.ConstructorArgumentValues
import com.hyosakura.liteioc.bean.factory.config.DependencyDescriptor
import com.hyosakura.liteioc.core.*
import com.hyosakura.liteioc.util.BeanUtil
import com.hyosakura.liteioc.util.ClassUtil
import com.hyosakura.liteioc.util.ReflectionUtil
import org.jetbrains.annotations.Nullable
import java.beans.PropertyDescriptor
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.function.Supplier

/**
 * @author LovesAsuna
 **/
abstract class AbstractAutowireCapableBeanFactory : AbstractBeanFactory, AutowireCapableBeanFactory {

    private val factoryBeanInstanceCache: ConcurrentMap<String, BeanWrapper> = ConcurrentHashMap()

    private val filteredPropertyDescriptorsCache: ConcurrentMap<Class<*>, Array<PropertyDescriptor>> =
        ConcurrentHashMap()

    private val currentlyCreatedBean: NamedThreadLocal<String> = NamedThreadLocal("Currently created bean")

    private val factoryMethodCandidateCache: ConcurrentMap<Class<*>, Array<Method>> = ConcurrentHashMap()

    private var instantiationStrategy: InstantiationStrategy

    private var parameterNameDiscoverer: ParameterNameDiscoverer? = DefaultParameterNameDiscoverer()

    private val ignoredDependencyTypes: MutableSet<Class<*>> = HashSet()

    private val ignoredDependencyInterfaces: MutableSet<Class<*>> = HashSet()

    private val autowiredAnnotationBeanPostProcessor =
        AutowiredAnnotationBeanPostProcessor(this as ConfigurableListableBeanFactory)

    private var allowCircularReferences = true

    private var allowRawInjectionDespiteWrapping = false

    private var proxyCreator: AutoProxyCreator? = null

    constructor() {
        this.instantiationStrategy = ByteBuddyInstantiationStrategy()
    }

    constructor(parentBeanFactory: BeanFactory?) : this() {
        setParentBeanFactory(parentBeanFactory)
    }

    fun setInstantiationStrategy(instantiationStrategy: InstantiationStrategy) {
        this.instantiationStrategy = instantiationStrategy
    }

    open fun getInstantiationStrategy(): InstantiationStrategy {
        return instantiationStrategy
    }

    open fun setParameterNameDiscoverer(parameterNameDiscoverer: ParameterNameDiscoverer?) {
        this.parameterNameDiscoverer = parameterNameDiscoverer
    }

    open fun getParameterNameDiscoverer(): ParameterNameDiscoverer? {
        return this.parameterNameDiscoverer
    }

    fun setAllowCircularReferences(allowCircularReferences: Boolean) {
        this.allowCircularReferences = allowCircularReferences
    }

    fun isAllowCircularReferences(): Boolean {
        return this.allowCircularReferences
    }

    fun setAllowRawInjectionDespiteWrapping(allowRawInjectionDespiteWrapping: Boolean) {
        this.allowRawInjectionDespiteWrapping = allowRawInjectionDespiteWrapping
    }

    fun isAllowRawInjectionDespiteWrapping(): Boolean {
        return this.allowRawInjectionDespiteWrapping
    }

    override fun createBean(beanName: String, mbd: RootBeanDefinition, args: Array<Any?>?): Any {
        if (logger.isTraceEnabled) {
            logger.trace("Creating instance of bean '$beanName'")
        }
        var mbdToUse = mbd

        // Make sure bean class is actually resolved at this point, and
        // clone the bean definition in case of a dynamically resolved Class
        // which cannot be stored in the shared merged bean definition.
        val resolvedClass = resolveBeanClass(mbd, beanName)
        if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
            mbdToUse = RootBeanDefinition(mbd)
            mbdToUse.setBeanClass(resolvedClass)
        }

        // Prepare method overrides.
        try {
            mbdToUse.prepareMethodOverrides()
        } catch (ex: BeanDefinitionValidationException) {
            throw BeanDefinitionStoreException(
                beanName, "Validation of method overrides failed", ex
            )
        }

        return try {
            val beanInstance = doCreateBean(beanName, mbdToUse, args)!!
            if (logger.isTraceEnabled) {
                logger.trace("Finished creating instance of bean '$beanName'")
            }
            beanInstance
        } catch (ex: BeanCreationException) {
            // A previously detected exception with proper bean creation context already,
            // or illegal singleton state to be communicated up to DefaultSingletonBeanRegistry.
            throw ex
        } catch (ex: Throwable) {
            throw BeanCreationException(beanName, "Unexpected exception during bean creation", ex)
        }
    }

    @Throws(BeanCreationException::class)
    protected open fun doCreateBean(beanName: String, mbd: RootBeanDefinition, args: Array<Any?>?): Any? {

        // Instantiate the bean.
        var instanceWrapper: BeanWrapper? = null
        if (mbd.isSingleton()) {
            instanceWrapper = this.factoryBeanInstanceCache.remove(beanName)
        }
        if (instanceWrapper == null) {
            instanceWrapper = createBeanInstance(beanName, mbd, args)
        }
        val bean = instanceWrapper!!.getWrappedInstance()
        val beanType = instanceWrapper.getWrappedClass()
        if (beanType != NullBean::class.java) {
            mbd.resolvedTargetType = beanType
        }

        // 寻找自动注入点
        synchronized(mbd.postProcessingLock) {
            if (!mbd.postProcessed) {
                try {
                    autowiredAnnotationBeanPostProcessor.findInjectionMetadata(beanName, beanType, mbd)
                } catch (ex: Throwable) {
                    throw BeanCreationException(beanName, "Post-processing of merged bean definition failed", ex)
                }
                mbd.postProcessed = true
            }
        }
        // Eagerly cache singletons to be able to resolve circular references
        // even when triggered by lifecycle interfaces like BeanFactoryAware.
        val earlySingletonExposure =
            mbd.isSingleton() && this.allowCircularReferences && isSingletonCurrentlyInCreation(beanName)
        if (earlySingletonExposure) {
            if (logger.isTraceEnabled) {
                logger.trace("Eagerly caching bean '$beanName' to allow for resolving potential circular references")
            }
            addSingletonFactory(beanName) { bean }
        }

        // Initialize the bean instance.
        var exposedObject = bean
        try {
            // 设置属性，非常重要
            populateBean(beanName, mbd, instanceWrapper)
            // 执行后置处理器，aop就是在这里完成的处理
            exposedObject = initializeBean(beanName, exposedObject, mbd)
        } catch (ex: Throwable) {
            if (ex is BeanCreationException && beanName == ex.getBeanName()) {
                throw ex
            } else {
                throw BeanCreationException(beanName, "Initialization of bean failed", ex)
            }
        }
        if (earlySingletonExposure) {
            val earlySingletonReference = getSingleton(beanName, false)
            if (earlySingletonReference != null) {
                if (exposedObject === bean) {
                    exposedObject = earlySingletonReference
                } else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
                    val dependentBeans = getDependentBeans(beanName)
                    val actualDependentBeans: MutableSet<String> = LinkedHashSet(dependentBeans.size)
                    for (dependentBean in dependentBeans) {
                        if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
                            actualDependentBeans.add(dependentBean)
                        }
                    }
                    if (actualDependentBeans.isNotEmpty()) {
                        throw BeanCurrentlyInCreationException(
                            beanName,
                            ("Bean with name '$beanName' has been injected into other beans [" + actualDependentBeans.joinToString(
                                ","
                            )) + "] in its raw version as part of a circular reference, but has eventually been " + "wrapped. This means that said other beans do not use the final version of the " + "bean. This is often the result of over-eager type matching - consider using " + "'getBeanNamesForType' with the 'allowEagerInit' flag turned off, for example."
                        )
                    }
                }
            }
        }

        // Register bean as disposable.
        try {
            registerDisposableBeanIfNecessary(beanName, bean, mbd)
        } catch (ex: BeanDefinitionValidationException) {
            throw BeanCreationException(beanName, "Invalid destruction signature", ex)
        }
        return exposedObject
    }

    override fun predictBeanType(
        beanName: String,
        mbd: RootBeanDefinition,
        vararg typesToMatch: Class<*>
    ): Class<*>? {
        return determineTargetType(beanName, mbd, *typesToMatch)
    }

    protected open fun determineTargetType(
        beanName: String,
        mbd: RootBeanDefinition,
        vararg typesToMatch: Class<*>
    ): Class<*>? {
        var targetType = mbd.getTargetType()
        if (targetType == null) {
            targetType = if (mbd.getFactoryMethodName() != null) {
                getTypeForFactoryMethod(beanName, mbd, *typesToMatch)
            } else {
                resolveBeanClass(
                    mbd,
                    beanName, *typesToMatch
                )
            }
            if (typesToMatch.isEmpty() || getTempClassLoader() == null) {
                mbd.resolvedTargetType = targetType
            }
        }
        return targetType
    }

    protected open fun getTypeForFactoryMethod(
        beanName: String,
        mbd: RootBeanDefinition,
        vararg typesToMatch: Class<*>
    ): Class<*>? {
        var cachedReturnType = mbd.factoryMethodReturnType
        if (cachedReturnType != null) {
            return cachedReturnType.resolve()
        }
        var commonType: Class<*>? = null
        var uniqueCandidate = mbd.factoryMethodToIntrospect
        if (uniqueCandidate == null) {
            // Check declared factory method return type on bean class.
            var factoryClass = resolveBeanClass(mbd, beanName, *typesToMatch) ?: return null
            factoryClass = ClassUtil.getUserClass(factoryClass)

            // If all factory methods have the same return type, return that type.
            // Can't clearly figure out exact method due to type converting / autowiring!
            val minNrOfArgs =
                if (mbd.hasConstructorArgumentValues()) mbd.getConstructorArgumentValues().getArgumentCount() else 0
            val candidates = this.factoryMethodCandidateCache.computeIfAbsent(
                factoryClass
            ) { clazz ->
                ReflectionUtil.getUniqueDeclaredMethods(
                    clazz,
                    ReflectionUtil.USER_DECLARED_METHODS
                )
            }
            for (candidate in candidates) {
                if (Modifier.isStatic(candidate.modifiers) && mbd.isFactoryMethod(candidate) && candidate.parameterCount >= minNrOfArgs) {
                    // Declared type variables to inspect?
                    if (candidate.typeParameters.isNotEmpty()) {
                        try {
                            // Fully resolve parameter names and argument values.
                            val paramTypes = candidate.parameterTypes
                            var paramNames: Array<String>? = null
                            val pnd = getParameterNameDiscoverer()
                            if (pnd != null) {
                                paramNames = pnd.getParameterNames(candidate)
                            }
                            val cav = mbd.getConstructorArgumentValues()
                            val usedValueHolders = HashSet<ConstructorArgumentValues.ValueHolder>(paramTypes.size)
                            val args = arrayOfNulls<Any>(paramTypes.size)
                            for (i in 0..args.size) {
                                var valueHolder = cav.getArgumentValue(
                                    i, paramTypes[i], paramNames?.get(i), usedValueHolders
                                )
                                if (valueHolder == null) {
                                    valueHolder = cav.getGenericArgumentValue(null, null, usedValueHolders)
                                }
                                if (valueHolder != null) {
                                    args[i] = valueHolder.value
                                    usedValueHolders.add(valueHolder)
                                }
                            }
                            val returnType = AutowireUtil.resolveReturnTypeForFactoryMethod(
                                candidate, args, getBeanClassLoader()
                            )
                            uniqueCandidate =
                                if (commonType == null && returnType == candidate.returnType) candidate else null
                            commonType = ClassUtil.determineCommonAncestor(returnType, commonType)
                            if (commonType == null) {
                                // Ambiguous return types found: return null to indicate "not determinable".
                                return null
                            }
                        } catch (ex: Throwable) {
                            if (logger.isDebugEnabled) {
                                logger.debug("Failed to resolve generic return type for factory method: $ex")
                            }
                        }
                    } else {
                        uniqueCandidate = if (commonType == null) candidate else null
                        commonType = ClassUtil.determineCommonAncestor(candidate.returnType, commonType)
                        if (commonType == null) {
                            // Ambiguous return types found: return null to indicate "not determinable".
                            return null
                        }
                    }
                }
            }
            mbd.factoryMethodToIntrospect = uniqueCandidate
            if (commonType == null) {
                return null
            }
        }

        // Common return type found: all factory methods return same type. For a non-parameterized
        // unique candidate, cache the full type declaration context of the target factory method.
        cachedReturnType =
            if (uniqueCandidate != null) ResolvableType.forMethodReturnType(uniqueCandidate) else ResolvableType.forClass(
                commonType
            )
        mbd.factoryMethodReturnType = cachedReturnType
        return cachedReturnType.resolve()
    }

    protected open fun createBeanInstance(
        beanName: String, mbd: RootBeanDefinition, args: Array<Any?>?
    ): BeanWrapper? {
        // Make sure bean class is actually resolved at this point.
        val beanClass = resolveBeanClass(mbd, beanName)

        if (beanClass != null && !Modifier.isPublic(beanClass.modifiers)) {
            throw BeanCreationException(
                beanName, "Bean class isn't public, and non-public access not allowed: " + beanClass.name
            )
        }
        val instanceSupplier = mbd.getInstanceSupplier()
        if (instanceSupplier != null) {
            return obtainFromSupplier(instanceSupplier, beanName)
        }


        if (mbd.getFactoryMethodName() != null) {
            return instantiateUsingFactoryMethod(beanName, mbd, args)
        }

        // Shortcut when re-creating the same bean...
        var resolved = false
        var autowireNecessary = false
        if (args == null) {
            synchronized(mbd.constructorArgumentLock) {
                if (mbd.resolvedConstructor != null) {
                    resolved = true
                    autowireNecessary = mbd.constructorArgumentsResolved
                }
            }
        }
        if (resolved) {
            return if (autowireNecessary) {
                autowireConstructor(beanName, mbd, null, null)
            } else {
                instantiateBean(beanName, mbd)
            }
        }

        // Candidate constructors for autowiring?
        var ctors = determineConstructorsFromBeanPostProcessors(beanClass, beanName)
        if (ctors != null || mbd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR || mbd.hasConstructorArgumentValues() || !args.isNullOrEmpty()) {
            return autowireConstructor(beanName, mbd, ctors, args)
        }

        // Preferred constructors for default construction?
        ctors = mbd.getPreferredConstructors()
        if (ctors != null) {
            return autowireConstructor(beanName, mbd, ctors, null)
        }

        // No special handling: simply use no-arg constructor.
        return instantiateBean(beanName, mbd)
    }

    protected open fun obtainFromSupplier(instanceSupplier: Supplier<*>, beanName: String): BeanWrapper? {
        var instance: Any?
        val outerBean: String? = this.currentlyCreatedBean.get()
        this.currentlyCreatedBean.set(beanName)
        instance = try {
            instanceSupplier.get()
        } finally {
            if (outerBean != null) {
                this.currentlyCreatedBean.set(outerBean)
            } else {
                this.currentlyCreatedBean.remove()
            }
        }
        if (instance == null) {
            instance = NullBean()
        }
        return BeanWrapperImpl(instance)
    }

    @Throws(BeansException::class)
    protected open fun determineConstructorsFromBeanPostProcessors(
        beanClass: Class<*>?, beanName: String
    ): Array<Constructor<*>>? {
        if (beanClass == null) {
            return null
        }
        return this.autowiredAnnotationBeanPostProcessor.determineCandidateConstructors(beanClass, beanName)
    }

    open fun instantiateBean(beanName: String, mbd: RootBeanDefinition): BeanWrapper? {
        return try {
            // getInstantiationStrategy()得到类的实例化策略，默认情况下是得到一个反射的实例化策略
            val beanInstance = getInstantiationStrategy().instantiate(mbd, beanName, this)
            BeanWrapperImpl(beanInstance)
        } catch (ex: Throwable) {
            throw BeanCreationException(beanName, "Instantiation of bean failed", ex)
        }
    }

    protected open fun instantiateUsingFactoryMethod(
        beanName: String, mbd: RootBeanDefinition, explicitArgs: Array<Any?>?
    ): BeanWrapper? {
        return ConstructorResolver(this).instantiateUsingFactoryMethod(beanName, mbd, explicitArgs)
    }

    protected open fun populateBean(beanName: String, mbd: RootBeanDefinition, bw: BeanWrapper?) {
        if (bw == null) {
            if (mbd.hasPropertyValues()) {
                throw BeanCreationException(beanName, "Cannot apply property values to null instance")
            } else {
                // Skip property population phase for null instance.
                return
            }
        }

        var pvs: PropertyValues? = if (mbd.hasPropertyValues()) mbd.getPropertyValues() else null

        val resolvedAutowireMode = mbd.getResolvedAutowireMode()
        if (resolvedAutowireMode == AUTOWIRE_BY_NAME || resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
            val newPvs = MutablePropertyValues(pvs)
            // Add property values based on autowire by name if applicable.
            if (resolvedAutowireMode == AUTOWIRE_BY_NAME) {
                autowireByName(beanName, mbd, bw, newPvs)
            }
            // Add property values based on autowire by type if applicable.
            if (resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
                autowireByType(beanName, mbd, bw, newPvs)
            }
            pvs = newPvs
        }

        if (pvs == null) {
            pvs = mbd.getPropertyValues()
        }
        val pvsToUse =
            autowiredAnnotationBeanPostProcessor.postProcessProperties(pvs, bw.getWrappedInstance(), beanName)
        pvs = pvsToUse
        val needsDepCheck = mbd.getDependencyCheck() != AbstractBeanDefinition.DEPENDENCY_CHECK_NONE

        if (needsDepCheck) {
            val filteredPds: Array<PropertyDescriptor> =
                filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching)
            checkDependencies(beanName, mbd, filteredPds, pvs)
        }
        if (pvs != null) {
            applyPropertyValues(beanName, mbd, bw, pvs)
        }
    }

    protected open fun autowireConstructor(
        beanName: String, mbd: RootBeanDefinition, ctors: Array<Constructor<*>>?, explicitArgs: Array<out Any?>?
    ): BeanWrapper? {
        return ConstructorResolver(this).autowireConstructor(beanName, mbd, ctors, explicitArgs)
    }

    protected open fun autowireByName(
        beanName: String, mbd: AbstractBeanDefinition, bw: BeanWrapper, pvs: MutablePropertyValues
    ) {
        val propertyNames = unsatisfiedNonSimpleProperties(mbd, bw)
        for (propertyName in propertyNames) {
            if (containsBean(propertyName)) {
                val bean = getBean(propertyName)
                pvs.add(propertyName, bean)
                registerDependentBean(propertyName, beanName)
                if (logger.isTraceEnabled) {
                    logger.trace("Added autowiring by name from bean name '$beanName' via property '$propertyName' to bean named '$propertyName'")
                }
            } else {
                if (logger.isTraceEnabled) {
                    logger.trace("Not autowiring property '$propertyName' of bean '$beanName' by name: no matching bean found")
                }
            }
        }
    }

    protected open fun autowireByType(
        beanName: String, mbd: AbstractBeanDefinition, bw: BeanWrapper, pvs: MutablePropertyValues
    ) {
        var converter = getCustomTypeConverter()
        if (converter == null) {
            converter = bw
        }
        val autowiredBeanNames = LinkedHashSet<String>(4)
        val propertyNames = unsatisfiedNonSimpleProperties(mbd, bw)
        for (propertyName in propertyNames) {
            try {
                val pd = bw.getPropertyDescriptor(propertyName)
                // Don't try autowiring by type for type Object: never makes sense,
                // even if it technically is a unsatisfied, non-simple property.
                if (Any::class.java != pd.propertyType) {
                    val method = MethodParameter(pd.writeMethod, 0)
                    val desc: DependencyDescriptor = AutowireByTypeDependencyDescriptor(method, true)
                    val autowiredArgument: Any? = resolveDependency(desc, beanName, autowiredBeanNames, converter)
                    if (autowiredArgument != null) {
                        pvs.add(propertyName, autowiredArgument)
                    }
                    for (autowiredBeanName: String in autowiredBeanNames) {
                        registerDependentBean(autowiredBeanName, beanName)
                        if (logger.isTraceEnabled) {
                            logger.trace("Autowiring by type from bean name '$beanName' via property '$propertyName' to bean named '$autowiredBeanName'")
                        }
                    }
                    autowiredBeanNames.clear()
                }
            } catch (ex: BeansException) {
                throw UnsatisfiedDependencyException(beanName, propertyName, ex)
            }
        }
    }

    open fun filterPropertyDescriptorsForDependencyCheck(
        bw: BeanWrapper, cache: Boolean
    ): Array<PropertyDescriptor> {
        var filtered = this.filteredPropertyDescriptorsCache[bw.getWrappedClass()]
        if (filtered == null) {
            filtered = filterPropertyDescriptorsForDependencyCheck(bw)
            if (cache) {
                val existing = this.filteredPropertyDescriptorsCache.putIfAbsent(bw.getWrappedClass(), filtered)
                if (existing != null) {
                    filtered = existing
                }
            }
        }
        return filtered
    }

    open fun filterPropertyDescriptorsForDependencyCheck(bw: BeanWrapper): Array<PropertyDescriptor> {
        val pds = bw.getPropertyDescriptors().toMutableList()
        pds.removeIf { pd -> isExcludedFromDependencyCheck(pd) }
        return pds.toTypedArray()
    }

    open fun unsatisfiedNonSimpleProperties(mbd: AbstractBeanDefinition, bw: BeanWrapper): Array<String> {
        val result = TreeSet<String>()
        val pvs = mbd.getPropertyValues()
        val pds = bw.getPropertyDescriptors()
        for (pd in pds) {
            if (pd.writeMethod != null && !isExcludedFromDependencyCheck(pd) && !pvs.contains(pd.name) && !BeanUtil.isSimpleProperty(
                    pd.propertyType
                )
            ) {
                result.add(pd.name)
            }
        }
        return result.toTypedArray()
    }

    open fun isExcludedFromDependencyCheck(pd: PropertyDescriptor): Boolean {
        return AutowireUtil.isExcludedFromDependencyCheck(pd) || this.ignoredDependencyTypes.contains(pd.propertyType) || AutowireUtil.isSetterDefinedInInterface(
            pd,
            this.ignoredDependencyInterfaces
        )
    }

    open fun initializeBean(beanName: String, bean: Any, mbd: RootBeanDefinition?): Any {
        invokeAwareMethods(beanName, bean)
        var wrappedBean = bean
        try {
            invokeInitMethods(beanName, wrappedBean, mbd)
        } catch (ex: Throwable) {
            throw BeanCreationException(beanName, "Invocation of init method failed", ex)
        }
        wrappedBean = applyAOP(wrappedBean, beanName)
        return wrappedBean
    }

    private fun invokeAwareMethods(beanName: String, bean: Any) {
        if (bean is Aware) {
            if (bean is BeanNameAware) {
                bean.setBeanName(beanName)
            }
            if (bean is BeanClassLoaderAware) {
                val bcl = getBeanClassLoader()
                if (bcl != null) {
                    bean.setBeanClassLoader(bcl)
                }
            }
            if (bean is BeanFactoryAware) {
                bean.setBeanFactory(this)
            }
        }
    }

    @Throws(Throwable::class)
    open fun invokeInitMethods(beanName: String, bean: Any, @Nullable mbd: RootBeanDefinition?) {
        val isInitializingBean = bean is InitializingBean
        if (isInitializingBean) {
            if (logger.isTraceEnabled) {
                logger.trace("Invoking afterPropertiesSet() on bean with name '$beanName'")
            }
            (bean as InitializingBean).afterPropertiesSet()
        }
    }

    @Throws(BeansException::class)
    fun applyAOP(existingBean: Any, beanName: String): Any {
        if (this.proxyCreator == null) {
            this.proxyCreator = AutoProxyCreator(this)
        }
        return this.proxyCreator!!.applyAutoProxy(existingBean, beanName)
    }

    @Throws(UnsatisfiedDependencyException::class)
    open fun checkDependencies(
        beanName: String, mbd: AbstractBeanDefinition, pds: Array<PropertyDescriptor>, pvs: PropertyValues?
    ) {
        val dependencyCheck = mbd.getDependencyCheck()
        for (pd in pds) {
            if (pd.writeMethod != null && (pvs == null || !pvs.contains(pd.name))) {
                val isSimple: Boolean = BeanUtil.isSimpleProperty(pd.propertyType)
                val unsatisfied =
                    (dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_ALL || isSimple) && dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_SIMPLE || !isSimple && dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_OBJECTS
                if (unsatisfied) {
                    throw UnsatisfiedDependencyException(
                        beanName, pd.name, "Set this property value or disable dependency checking for this bean."
                    )
                }
            }
        }
    }

    protected open fun applyPropertyValues(
        beanName: String, mbd: BeanDefinition, bw: BeanWrapper, pvs: PropertyValues
    ) {
        if (pvs.isEmpty()) {
            return
        }
        var mpvs: MutablePropertyValues? = null
        val original: List<PropertyValue>
        if (pvs is MutablePropertyValues) {
            mpvs = pvs
            if (mpvs.isConverted()) {
                // Shortcut: use the pre-converted values as-is.
                try {
                    bw.setPropertyValues(mpvs)
                    return
                } catch (ex: BeansException) {
                    throw BeanCreationException(beanName, "Error setting property values", ex)
                }
            }
            original = mpvs.getPropertyValueList()
        } else {
            original = pvs.getPropertyValues().toList()
        }
        var converter = getCustomTypeConverter()
        if (converter == null) {
            converter = bw
        }

        // Create a deep copy, resolving any references for values.
        val deepCopy = ArrayList<PropertyValue>(original.size)
        var resolveNecessary = false
        for (pv in original) {
            if (pv.isConverted()) {
                deepCopy.add(pv)
            } else {
                val propertyName = pv.name
                var originalValue = pv.value
                var convertedValue = originalValue
                val convertible = bw.isWritableProperty(propertyName)
                if (convertible) {
                    convertedValue = convertForProperty(originalValue, propertyName, bw, converter)
                }
                // Possibly store converted value in merged bean definition,
                // in order to avoid re-conversion for every created bean instance.
                if (convertedValue === originalValue) {
                    if (convertible) {
                        pv.setConvertedValue(convertedValue)
                    }
                    deepCopy.add(pv)
                } else {
                    resolveNecessary = true
                    deepCopy.add(PropertyValue(pv, convertedValue))
                }
            }
        }
        if (mpvs != null && !resolveNecessary) {
            mpvs.setConverted()
        }

        // Set our (possibly massaged) deep copy.
        try {
            bw.setPropertyValues(MutablePropertyValues(deepCopy))
        } catch (ex: BeansException) {
            throw BeanCreationException(beanName, "Error setting property values", ex)
        }
    }

    private fun convertForProperty(value: Any?, propertyName: String, bw: BeanWrapper, converter: TypeConverter): Any? {
        return if (converter is BeanWrapperImpl) {
            converter.convertForProperty(value, propertyName)
        } else {
            val pd = bw.getPropertyDescriptor(propertyName)
            converter.convertIfNecessary(value, pd.propertyType)
        }
    }

    private class AutowireByTypeDependencyDescriptor(methodParameter: MethodParameter, eager: Boolean) :
        DependencyDescriptor(methodParameter, false, eager) {

        override fun getDependencyName(): String? {
            return null
        }

    }

}