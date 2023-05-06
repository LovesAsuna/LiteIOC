package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.BeanDefinition
import com.hyosakura.liteioc.bean.BeansException
import com.hyosakura.liteioc.bean.TypeConverter
import com.hyosakura.liteioc.bean.factory.*
import com.hyosakura.liteioc.bean.factory.config.BeanDefinitionHolder
import com.hyosakura.liteioc.bean.factory.config.ConfigurableListableBeanFactory
import com.hyosakura.liteioc.bean.factory.config.DependencyDescriptor
import com.hyosakura.liteioc.bean.factory.config.NamedBeanHolder
import com.hyosakura.liteioc.core.ResolvableType
import com.hyosakura.liteioc.util.ClassUtil
import com.hyosakura.liteioc.util.ObjectUtil
import org.jetbrains.annotations.Nullable
import java.io.Serializable
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.stream.Stream

/**
 * @author LovesAsuna
 **/
class DefaultListableBeanFactory : AbstractAutowireCapableBeanFactory, ConfigurableListableBeanFactory,
    BeanDefinitionRegistry {

    private val beanDefinitionMap: MutableMap<String, BeanDefinition> = ConcurrentHashMap(256)

    private val mergedBeanDefinitionHolders: MutableMap<String, BeanDefinitionHolder> = ConcurrentHashMap(256)

    @Volatile
    private var beanDefinitionNames = ArrayList<String>(256)

    @Volatile
    private var manualSingletonNames = LinkedHashSet<String>(16)

    private var allowBeanDefinitionOverriding = true

    private val allBeanNamesByType: MutableMap<Class<*>, Array<String>> = ConcurrentHashMap(64)

    private val singletonBeanNamesByType: MutableMap<Class<*>, Array<String>> = ConcurrentHashMap(64)

    private val autowireCandidateResolver: AutowireCandidateResolver = SimpleAutowireCandidateResolver.INSTANCE

    private val resolvableDependencies: MutableMap<Class<*>, Any> = ConcurrentHashMap(16)

    private var dependencyComparator: Comparator<Any?>? = null

    constructor() : super()

    constructor(parentBeanFactory: BeanFactory?) : super(parentBeanFactory)

    fun setAllowBeanDefinitionOverriding(allowBeanDefinitionOverriding: Boolean) {
        this.allowBeanDefinitionOverriding = allowBeanDefinitionOverriding
    }

    fun isAllowBeanDefinitionOverriding(): Boolean {
        return allowBeanDefinitionOverriding
    }

    override fun <T> getBean(requiredType: Class<T>, vararg args: Any?): T {
        return resolveBean(ResolvableType.forRawClass(requiredType), if (args.isEmpty()) null else args, false)
            ?: throw NoSuchBeanDefinitionException(
                requiredType
            )
    }

    private fun <T> resolveBean(requiredType: Class<T>, args: Array<out Any?>?): T? {
        val parent = getParentBeanFactory()
        if (parent is DefaultListableBeanFactory) {
            return parent.resolveBean(requiredType, args)
        } else if (parent != null) {
            val parentProvider = parent.getBeanProvider(requiredType)
            return if (args != null) {
                parentProvider.getObject(args)
            } else {
                parentProvider.getIfAvailable()
            }
        }
        return null
    }

    private fun <T> resolveBean(
        requiredType: ResolvableType, args: Array<out Any?>?, nonUniqueAsNull: Boolean
    ): T? {
        val namedBean = resolveNamedBean<T>(requiredType, args, nonUniqueAsNull)
        if (namedBean != null) {
            return namedBean.getBeanInstance()
        }
        val parent = getParentBeanFactory()
        if (parent is DefaultListableBeanFactory) {
            return parent.resolveBean<T>(requiredType, args, nonUniqueAsNull)
        } else if (parent != null) {
            val parentProvider: ObjectProvider<T> = parent.getBeanProvider(requiredType)
            return if (args != null) {
                parentProvider.getObject(args)
            } else {
                if (nonUniqueAsNull) parentProvider.getIfUnique() else parentProvider.getIfAvailable()
            }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(BeansException::class)
    private fun <T> resolveNamedBean(
        requiredType: ResolvableType, args: Array<out Any?>?, nonUniqueAsNull: Boolean
    ): NamedBeanHolder<T>? {
        var candidateNames = getBeanNamesForType(requiredType)
        if (candidateNames.size > 1) {
            val autowireCandidates = ArrayList<String>(candidateNames.size)
            for (beanName in candidateNames) {
                if (!containsBeanDefinition(beanName) || getBeanDefinition(beanName).isAutowireCandidate()) {
                    autowireCandidates.add(beanName)
                }
            }
            if (autowireCandidates.isNotEmpty()) {
                candidateNames = autowireCandidates.toTypedArray()
            }
        }
        if (candidateNames.size == 1) {
            return resolveNamedBean(candidateNames[0], requiredType, args)
        } else if (candidateNames.size > 1) {
            val candidates: MutableMap<String, Any?> = LinkedHashMap(candidateNames.size)
            for (beanName in candidateNames) {
                if (containsSingleton(beanName) && args == null) {
                    val beanInstance = getBean(beanName)
                    candidates[beanName] = if (beanInstance is NullBean) null else beanInstance
                } else {
                    candidates[beanName] = getType(beanName)
                }
            }
            val candidateName = determinePrimaryCandidate(candidates, requiredType.toClass())
            if (candidateName != null) {
                val beanInstance = candidates[candidateName] ?: return null
                return if (beanInstance is Class<*>) {
                    resolveNamedBean(candidateName, requiredType, args)
                } else NamedBeanHolder(candidateName, beanInstance as T)
            }
            if (!nonUniqueAsNull) {
                throw NoUniqueBeanDefinitionException(requiredType, candidates.keys)
            }
        }
        return null
    }

    @Throws(BeansException::class)
    private fun <T> resolveNamedBean(
        beanName: String, requiredType: ResolvableType, args: Array<out Any?>?
    ): NamedBeanHolder<T>? {
        val bean = getBean<Any>(beanName, null, *(args ?: emptyArray()))
        return if (bean is NullBean) {
            null
        } else NamedBeanHolder(beanName, adaptBeanInstance(beanName, bean, requiredType.toClass()))
    }

    override fun <T> getBeanProvider(requiredType: Class<T>): ObjectProvider<T> {
        return getBeanProvider(ResolvableType.forRawClass(requiredType), true)
    }

    override fun <T> getBeanProvider(requiredType: ResolvableType): ObjectProvider<T> {
        return getBeanProvider(requiredType, true)
    }

    fun determinePrimaryCandidate(candidates: Map<String, Any?>, requiredType: Class<*>): String? {
        var primaryBeanName: String? = null
        for ((candidateBeanName, beanInstance) in candidates) {
            if (isPrimary(candidateBeanName, beanInstance!!)) {
                if (primaryBeanName != null) {
                    val candidateLocal = containsBeanDefinition(candidateBeanName)
                    val primaryLocal = containsBeanDefinition(primaryBeanName)
                    if (candidateLocal && primaryLocal) {
                        throw NoUniqueBeanDefinitionException(
                            requiredType,
                            candidates.size,
                            "more than one 'primary' bean found among candidates: " + candidates.keys
                        )
                    } else if (candidateLocal) {
                        primaryBeanName = candidateBeanName
                    }
                } else {
                    primaryBeanName = candidateBeanName
                }
            }
        }
        return primaryBeanName
    }

    fun isPrimary(beanName: String, beanInstance: Any): Boolean {
        return if (containsBeanDefinition(beanName)) {
            getMergedLocalBeanDefinition(beanName).isPrimary()
        } else {
            val parent = getParentBeanFactory()
            parent is DefaultListableBeanFactory && parent.isPrimary(beanName, beanInstance)
        }
    }

    @Throws(BeansException::class)
    override fun resolveDependency(
        descriptor: DependencyDescriptor,
        requestingBeanName: String?,
        autowiredBeanNames: MutableSet<String>?,
        typeConverter: TypeConverter?
    ): Any? {
        descriptor.initParameterNameDiscovery(getParameterNameDiscoverer())

        return if (Optional::class.java == descriptor.getDependencyType()) {
            createOptionalDependency(descriptor, requestingBeanName)
        } else if (ObjectFactory::class.java == descriptor.getDependencyType() ||
            ObjectProvider::class.java == descriptor.getDependencyType()
        ) {
            DependencyObjectProvider(descriptor, requestingBeanName)
        } else {
            var result = this.autowireCandidateResolver.getLazyResolutionProxyIfNecessary(
                descriptor, requestingBeanName
            )
            if (result == null) {
                result = doResolveDependency(descriptor, requestingBeanName, autowiredBeanNames, typeConverter)
            }
            result
        }
    }

    private fun createOptionalDependency(
        descriptor: DependencyDescriptor, beanName: String?, vararg args: Any?
    ): Any {
        val descriptorToUse: DependencyDescriptor = object : NestedDependencyDescriptor(descriptor) {

            override fun isRequired(): Boolean {
                return false
            }

            override fun resolveCandidate(beanName: String, requiredType: Class<*>, beanFactory: BeanFactory): Any {
                return if (args.isNotEmpty()) beanFactory.getBean(
                    beanName,
                    *args
                ) else super.resolveCandidate(beanName, requiredType, beanFactory)
            }

        }
        val result = doResolveDependency(descriptorToUse, beanName, null, null)
        return result as? Optional<*> ?: Optional.ofNullable(result)
    }

    override fun toString(): String {
        val sb: StringBuilder = StringBuilder(ObjectUtil.identityToString(this))
        sb.append(": defining beans [")
        sb.append(beanDefinitionNames.joinToString(","))
        sb.append("]; ")
        val parent = getParentBeanFactory()
        if (parent == null) {
            sb.append("root of factory hierarchy")
        } else {
            sb.append("parent: ").append(ObjectUtil.identityToString(parent))
        }
        return sb.toString()
    }

    @Throws(BeansException::class)
    fun doResolveDependency(
        descriptor: DependencyDescriptor, beanName: String?,
        autowiredBeanNames: MutableSet<String>?, typeConverter: TypeConverter?
    ): Any? {
        val previousInjectionPoint = ConstructorResolver.setCurrentInjectionPoint(descriptor)
        return try {
            val shortcut = descriptor.resolveShortcut(this)
            if (shortcut != null) {
                return shortcut
            }
            val type = descriptor.getDependencyType()
            val value = this.autowireCandidateResolver.getSuggestedValue(descriptor)
            if (value != null) {
                val converter = typeConverter ?: getTypeConverter()
                return converter.convertIfNecessary(value, type)
            }
            val multipleBeans = resolveMultipleBeans(descriptor, beanName, autowiredBeanNames, typeConverter)
            if (multipleBeans != null) {
                return multipleBeans
            }
            val matchingBeans = findAutowireCandidates(beanName, type, descriptor)
            if (matchingBeans.isEmpty()) {
                if (isRequired(descriptor)) {
                    raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor)
                }
                return null
            }
            val autowiredBeanName: String?
            var instanceCandidate: Any?
            if (matchingBeans.size > 1) {
                autowiredBeanName = determineAutowireCandidate(matchingBeans, descriptor)
                if (autowiredBeanName == null) {
                    return if (isRequired(descriptor) || !indicatesMultipleBeans(type)) {
                        descriptor.resolveNotUnique(descriptor.getResolvableType(), matchingBeans)
                    } else {
                        // In case of an optional Collection/Map, silently ignore a non-unique case:
                        // possibly it was meant to be an empty collection of multiple regular beans
                        // (before 4.3 in particular when we didn't even look for collection beans).
                        null
                    }
                }
                instanceCandidate = matchingBeans[autowiredBeanName]
            } else {
                // We have exactly one match.
                val (key, value1) = matchingBeans.entries.iterator().next()
                autowiredBeanName = key
                instanceCandidate = value1
            }
            autowiredBeanNames?.add(autowiredBeanName)
            if (instanceCandidate is Class<*>) {
                instanceCandidate = descriptor.resolveCandidate(autowiredBeanName, type, this)
            }
            var result = instanceCandidate
            if (result is NullBean) {
                if (isRequired(descriptor)) {
                    raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor)
                }
                result = null
            }
            if (!ClassUtil.isAssignableValue(type, result)) {
                throw BeanNotOfRequiredTypeException(autowiredBeanName, type, instanceCandidate!!.javaClass)
            }
            result
        } finally {
            ConstructorResolver.setCurrentInjectionPoint(previousInjectionPoint)
        }
    }

    private fun resolveMultipleBeans(
        descriptor: DependencyDescriptor, beanName: String?,
        autowiredBeanNames: MutableSet<String>?, typeConverter: TypeConverter?
    ): Any? {
        val type = descriptor.getDependencyType()
        return if (descriptor is StreamDependencyDescriptor) {
            val matchingBeans = findAutowireCandidates(
                beanName,
                type, descriptor
            )
            autowiredBeanNames?.addAll(matchingBeans.keys)
            val stream = matchingBeans.keys.stream()
                .map { name: String ->
                    descriptor.resolveCandidate(
                        name, type, this
                    )
                }
                .filter { bean: Any -> bean !is NullBean }
            stream
        } else if (type.isArray) {
            var componentType = type.componentType
            val resolvableType = descriptor.getResolvableType()
            val resolvedArrayType = resolvableType.resolve(type)
            if (resolvedArrayType != type) {
                componentType = resolvableType.getComponentType().resolve()
            }
            if (componentType == null) {
                return null
            }
            val matchingBeans = findAutowireCandidates(
                beanName, componentType,
                MultiElementDescriptor(descriptor)
            )
            if (matchingBeans.isEmpty()) {
                return null
            }
            autowiredBeanNames?.addAll(matchingBeans.keys)
            val converter = typeConverter ?: getTypeConverter()
            val result = converter.convertIfNecessary(matchingBeans.values, resolvedArrayType)
            if (result is Array<*> && result.isArrayOf<Any>()) {
                val comparator = adaptDependencyComparator(matchingBeans)
                if (comparator != null) {
                    Arrays.sort(result, comparator)
                }
            }
            result
        } else if (MutableCollection::class.java.isAssignableFrom(type) && type.isInterface) {
            val elementType: Class<*> = descriptor.getResolvableType().asCollection().resolveGeneric() ?: return null
            val matchingBeans = findAutowireCandidates(beanName, elementType, MultiElementDescriptor(descriptor))
            if (matchingBeans.isEmpty()) {
                return null
            }
            autowiredBeanNames?.addAll(matchingBeans.keys)
            val converter = typeConverter ?: getTypeConverter()
            val result = converter.convertIfNecessary(matchingBeans.values, type)
            if (result is List<*> && result.size > 1) {
                val comparator = adaptDependencyComparator(matchingBeans)
                if (comparator != null) {
                    result.sortedWith(comparator)
                }
            }
            result
        } else if (MutableMap::class.java == type) {
            val mapType = descriptor.getResolvableType().asMap()
            val keyType = mapType.resolveGeneric(0)
            if (String::class.java != keyType) {
                return null
            }
            val valueType: Class<*> = mapType.resolveGeneric(1) ?: return null
            val matchingBeans = findAutowireCandidates(
                beanName, valueType,
                MultiElementDescriptor(descriptor)
            )
            if (matchingBeans.isEmpty()) {
                return null
            }
            autowiredBeanNames?.addAll(matchingBeans.keys)
            matchingBeans
        } else {
            null
        }
    }

    private fun findAutowireCandidates(
        beanName: String?, requiredType: Class<*>, descriptor: DependencyDescriptor
    ): Map<String, Any?> {
        val candidateNames = this.getBeanNamesForType(requiredType, true, descriptor.isEager())
        val result = LinkedHashMap<String, Any?>(candidateNames.size)
        for (classObjectEntry in this.resolvableDependencies.entries) {
            val autowiringType = classObjectEntry.key
            if (autowiringType.isAssignableFrom(requiredType)) {
                var autowiringValue = classObjectEntry.value
                autowiringValue = AutowireUtil.resolveAutowiringValue(autowiringValue, requiredType)
                if (requiredType.isInstance(autowiringValue)) {
                    result[ObjectUtil.identityToString(autowiringValue)] = autowiringValue
                    break
                }
            }
        }
        for (candidate in candidateNames) {
            if (!isSelfReference(beanName, candidate) && isAutowireCandidate(candidate, descriptor)) {
                addCandidateEntry(result, candidate, descriptor, requiredType)
            }
        }
        if (result.isEmpty()) {
            val multiple = indicatesMultipleBeans(requiredType)
            // Consider fallback matches if the first pass failed to find anything...
            val fallbackDescriptor = descriptor.forFallbackMatch()
            for (candidate in candidateNames) {
                if (!isSelfReference(beanName, candidate) && isAutowireCandidate(candidate, fallbackDescriptor) &&
                    (!multiple || this.autowireCandidateResolver.hasQualifier(descriptor))
                ) {
                    addCandidateEntry(result, candidate, descriptor, requiredType)
                }
            }
            if (result.isEmpty() && !multiple) {
                // Consider self references as a final pass...
                // but in the case of a dependency collection, not the very same bean itself.
                for (candidate in candidateNames) {
                    if (isSelfReference(beanName, candidate) &&
                        (descriptor !is MultiElementDescriptor || beanName != candidate) &&
                        isAutowireCandidate(candidate, fallbackDescriptor)
                    ) {
                        addCandidateEntry(result, candidate, descriptor, requiredType)
                    }
                }
            }
        }
        return result
    }

    private fun addCandidateEntry(
        candidates: MutableMap<String, Any?>, candidateName: String,
        descriptor: DependencyDescriptor, requiredType: Class<*>
    ) {
        if (descriptor is MultiElementDescriptor) {
            val beanInstance = descriptor.resolveCandidate(candidateName, requiredType, this)
            if (beanInstance !is NullBean) {
                candidates[candidateName] = beanInstance
            }
        } else if (containsSingleton(candidateName) || descriptor is StreamDependencyDescriptor) {
            val beanInstance = descriptor.resolveCandidate(candidateName, requiredType, this)
            candidates[candidateName] = if (beanInstance is NullBean) null else beanInstance
        } else {
            candidates[candidateName] = getType(candidateName)
        }
    }

    private fun determineAutowireCandidate(candidates: Map<String, Any?>, descriptor: DependencyDescriptor): String? {
        val requiredType = descriptor.getDependencyType()
        val primaryCandidate = determinePrimaryCandidate(candidates, requiredType)
        if (primaryCandidate != null) {
            return primaryCandidate
        }

        // Fallback
        for ((candidateName, beanInstance) in candidates) {
            if (beanInstance != null && resolvableDependencies.containsValue(beanInstance) ||
                matchesBeanName(candidateName, descriptor.getDependencyName())
            ) {
                return candidateName
            }
        }
        return null
    }

    private fun isRequired(descriptor: DependencyDescriptor): Boolean {
        return this.autowireCandidateResolver.isRequired(descriptor)
    }

    private fun indicatesMultipleBeans(type: Class<*>): Boolean {
        return type.isArray || type.isInterface && (MutableCollection::class.java.isAssignableFrom(type) || MutableMap::class.java.isAssignableFrom(
            type
        ))
    }

    private fun adaptDependencyComparator(matchingBeans: Map<String, *>): Comparator<Any?>? {
        val comparator = this.dependencyComparator
        return comparator
    }

    private fun matchesBeanName(beanName: String, candidateName: String?): Boolean {
        return candidateName != null && candidateName == beanName
    }

    private fun isSelfReference(beanName: String?, candidateName: String?): Boolean {
        return beanName != null && candidateName != null && beanName == candidateName
    }

    @Throws(BeansException::class)
    private fun raiseNoMatchingBeanFound(
        type: Class<*>, resolvableType: ResolvableType, descriptor: DependencyDescriptor
    ) {
        checkBeanNotOfRequiredType(type, descriptor)
        throw NoSuchBeanDefinitionException(
            resolvableType,
            "expected at least 1 bean which qualifies as autowire candidate. " +
                    "Dependency annotations: " + ObjectUtil.nullSafeToString(descriptor.getAnnotations())
        )
    }

    private fun checkBeanNotOfRequiredType(type: Class<*>, descriptor: DependencyDescriptor) {
        for (beanName in beanDefinitionNames) {
            try {
                val mbd = getMergedLocalBeanDefinition(beanName)
                val targetType = mbd.getTargetType()
                if (targetType != null && type.isAssignableFrom(targetType) &&
                    isAutowireCandidate(beanName, mbd, descriptor, this.autowireCandidateResolver)
                ) {
                    // Probably a proxy interfering with target type match -> throw meaningful exception.
                    val beanInstance = getSingleton(beanName, false)
                    val beanType =
                        if (beanInstance != null && beanInstance.javaClass != NullBean::class.java) beanInstance.javaClass else predictBeanType(
                            beanName,
                            mbd
                        )
                    if (beanType != null && !type.isAssignableFrom(beanType)) {
                        throw BeanNotOfRequiredTypeException(beanName, type, beanType)
                    }
                }
            } catch (ex: NoSuchBeanDefinitionException) {
                // Bean definition got removed while we were iterating -> ignore.
            }
        }
        val parent = getParentBeanFactory()
        if (parent is DefaultListableBeanFactory) {
            parent.checkBeanNotOfRequiredType(type, descriptor)
        }
    }

    override fun preInstantiateSingletons() {
        // 拿到所有bean的名字
        val beanNames = ArrayList(this.beanDefinitionNames)
        // 触发所有非lazy单例bean的实例化，主要操作为调用getBean方法
        for (beanName in beanNames) {
            val bd = getMergedLocalBeanDefinition(beanName)
            if (!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()) {
                getBean(beanName)
            }
        }
    }


    private interface BeanObjectFactory<T> : ObjectProvider<T>, Serializable

    override fun registerBeanDefinition(beanName: String, beanDefinition: BeanDefinition) {
        if (beanDefinition is AbstractBeanDefinition) {
            try {
                beanDefinition.validate()
            } catch (ex: BeanDefinitionValidationException) {
                throw BeanDefinitionStoreException(beanName, "Validation of bean definition failed", ex)
            }
        }

        val existingDefinition = this.beanDefinitionMap[beanName]
        // beanDefinition已存在
        if (existingDefinition != null) {
            if (!this.allowBeanDefinitionOverriding) {
                throw BeanDefinitionOverrideException(beanName, beanDefinition, existingDefinition)
            } else if (beanDefinition != existingDefinition) {
                if (logger.isDebugEnabled) {
                    logger.debug("Overriding bean definition for bean '$beanName' with a different definition: replacing [$existingDefinition] with [$beanDefinition]")
                }
            } else {
                if (logger.isTraceEnabled) {
                    logger.trace("Overriding bean definition for bean '$beanName' with an equivalent definition: replacing [$existingDefinition] with [$beanDefinition]")
                }
            }
            this.beanDefinitionMap[beanName] = beanDefinition
        } else {
            if (hasBeanCreationStarted()) {
                // Cannot modify startup-time collection elements anymore (for stable iteration)
                synchronized(this.beanDefinitionMap) {
                    this.beanDefinitionMap[beanName] = beanDefinition
                    val updatedDefinitions = ArrayList<String>(this.beanDefinitionNames.size + 1)
                    updatedDefinitions.addAll(this.beanDefinitionNames)
                    updatedDefinitions.add(beanName)
                    this.beanDefinitionNames = updatedDefinitions
                    removeManualSingletonName(beanName)
                }
            } else {
                // 仍处于启动注册阶段
                this.beanDefinitionMap[beanName] = beanDefinition
                this.beanDefinitionNames.add(beanName)
                removeManualSingletonName(beanName)
            }
        }

        if (existingDefinition != null || containsSingleton(beanName)) {
            resetBeanDefinition(beanName)
        }
    }

    override fun registerResolvableDependency(dependencyType: Class<*>, autowiredValue: Any?) {
        if (autowiredValue != null) {
            require(autowiredValue is ObjectFactory<*> || dependencyType.isInstance(autowiredValue)) {
                "Value [" + autowiredValue +
                        "] does not implement specified dependency type [" + dependencyType.name + "]"
            }
            resolvableDependencies[dependencyType] = autowiredValue
        }
    }

    @Throws(NoSuchBeanDefinitionException::class)
    override fun isAutowireCandidate(beanName: String, descriptor: DependencyDescriptor): Boolean {
        return isAutowireCandidate(beanName, descriptor, this.autowireCandidateResolver)
    }

    @Throws(NoSuchBeanDefinitionException::class)
    private fun isAutowireCandidate(
        beanName: String, descriptor: DependencyDescriptor, resolver: AutowireCandidateResolver
    ): Boolean {
        val bdName: String = beanName
        if (containsBeanDefinition(bdName)) {
            return isAutowireCandidate(beanName, getMergedLocalBeanDefinition(bdName), descriptor, resolver)
        } else if (containsSingleton(beanName)) {
            return isAutowireCandidate(beanName, RootBeanDefinition(getType(beanName)), descriptor, resolver)
        }
        val parent = getParentBeanFactory()
        return (parent as? DefaultListableBeanFactory)?.isAutowireCandidate(beanName, descriptor, resolver)
            ?: ((parent as? ConfigurableListableBeanFactory)?.isAutowireCandidate(beanName, descriptor) ?: true)
    }

    private fun isAutowireCandidate(
        beanName: String, mbd: RootBeanDefinition,
        descriptor: DependencyDescriptor, resolver: AutowireCandidateResolver
    ): Boolean {
        val bdName: String = beanName
        resolveBeanClass(mbd, bdName)
        val holder = this.mergedBeanDefinitionHolders.computeIfAbsent(
            beanName
        ) { _: String ->
            BeanDefinitionHolder(
                mbd,
                beanName
            )
        }
        return resolver.isAutowireCandidate(holder, descriptor)
    }

    private fun removeManualSingletonName(beanName: String) {
        updateManualSingletonNames({ set ->
            set.remove(
                beanName
            )
        }, { set ->
            set.contains(
                beanName
            )
        })
    }

    override fun <T> getBeanProvider(requiredType: Class<T>, allowEagerInit: Boolean): ObjectProvider<T> {
        return getBeanProvider(ResolvableType.forRawClass(requiredType), allowEagerInit)
    }

    override fun <T> getBeanProvider(requiredType: ResolvableType, allowEagerInit: Boolean): ObjectProvider<T> {
        return object : BeanObjectProvider<T> {

            @Throws(BeansException::class)
            override fun getObject(): T {
                return resolveBean<T>(requiredType, null, false) ?: throw NoSuchBeanDefinitionException(requiredType)
            }

            @Throws(BeansException::class)
            override fun getObject(vararg args: Any?): T {
                return resolveBean<T>(requiredType, args, false) ?: throw NoSuchBeanDefinitionException(requiredType)
            }

            @Nullable
            @Throws(BeansException::class)
            override fun getIfAvailable(): T? {
                return try {
                    resolveBean<T>(requiredType, null, false)
                } catch (ex: ScopeNotActiveException) {
                    // Ignore resolved bean in non-active scope
                    null
                }
            }

            @Throws(BeansException::class)
            override fun getIfUnique(): T? {
                return try {
                    resolveBean<T>(requiredType, null, true)
                } catch (ex: ScopeNotActiveException) {
                    // Ignore resolved bean in non-active scope
                    null
                }
            }

        }
    }

    private class MultiElementDescriptor(original: DependencyDescriptor) : NestedDependencyDescriptor(original)

    private class StreamDependencyDescriptor(original: DependencyDescriptor) : DependencyDescriptor(original)

    private interface BeanObjectProvider<T> : ObjectProvider<T>, Serializable

    private inner class DependencyObjectProvider(descriptor: DependencyDescriptor, beanName: String?) :
        BeanObjectProvider<Any> {

        private val descriptor: DependencyDescriptor

        private val optional: Boolean

        private var beanName: String?

        init {
            this.descriptor = NestedDependencyDescriptor(descriptor)
            this.optional = this.descriptor.getDependencyType() == Optional::class.java
            this.beanName = beanName
        }

        @Throws(BeansException::class)
        override fun getObject(): Any {
            return if (optional) {
                createOptionalDependency(descriptor, beanName)
            } else {
                val result = doResolveDependency(descriptor, beanName, null, null)
                    ?: throw NoSuchBeanDefinitionException(descriptor.getResolvableType())
                result
            }
        }

        @Throws(BeansException::class)
        override fun getObject(vararg args: Any?): Any {
            return if (optional) {
                createOptionalDependency(descriptor, beanName, *args)
            } else {
                val descriptorToUse = object : DependencyDescriptor(descriptor) {

                    override fun resolveCandidate(
                        beanName: String,
                        requiredType: Class<*>,
                        beanFactory: BeanFactory
                    ): Any {
                        return beanFactory.getBean(beanName, args)
                    }

                }
                val result: Any = doResolveDependency(descriptorToUse, beanName, null, null)
                    ?: throw NoSuchBeanDefinitionException(descriptor.getResolvableType())
                result
            }
        }

        @Nullable
        @Throws(BeansException::class)
        override fun getIfAvailable(): Any? {
            return try {
                if (optional) {
                    createOptionalDependency(descriptor, beanName)
                } else {
                    val descriptorToUse: DependencyDescriptor = object : DependencyDescriptor(descriptor) {
                        override fun isRequired(): Boolean {
                            return false
                        }
                    }
                    doResolveDependency(descriptorToUse, beanName, null, null)
                }
            } catch (ex: ScopeNotActiveException) {
                // Ignore resolved bean in non-active scope
                null
            }
        }

        @Throws(BeansException::class)
        override fun ifAvailable(dependencyConsumer: Consumer<Any>) {
            val dependency = getIfAvailable()
            if (dependency != null) {
                try {
                    dependencyConsumer.accept(dependency)
                } catch (ex: ScopeNotActiveException) {
                    // Ignore resolved bean in non-active scope, even on scoped proxy invocation
                }
            }
        }

        @Nullable
        @Throws(BeansException::class)
        override fun getIfUnique(): Any? {
            val descriptorToUse: DependencyDescriptor = object : DependencyDescriptor(descriptor) {

                override fun isRequired(): Boolean {
                    return false
                }

                override fun resolveNotUnique(type: ResolvableType, matchingBeans: Map<String, Any?>): Any? {
                    return null
                }

            }
            return try {
                if (optional) {
                    createOptionalDependency(descriptorToUse, beanName)
                } else {
                    doResolveDependency(descriptorToUse, beanName, null, null)
                }
            } catch (ex: ScopeNotActiveException) {
                // Ignore resolved bean in non-active scope
                null
            }
        }

        @Throws(BeansException::class)
        override fun ifUnique(dependencyConsumer: Consumer<Any>) {
            val dependency = getIfUnique()
            if (dependency != null) {
                try {
                    dependencyConsumer.accept(dependency)
                } catch (ex: ScopeNotActiveException) {
                    // Ignore resolved bean in non-active scope, even on scoped proxy invocation
                }
            }
        }

        @Throws(BeansException::class)
        protected fun getValue(): Any? {
            return if (this.optional) {
                createOptionalDependency(this.descriptor, this.beanName)
            } else {
                doResolveDependency(this.descriptor, this.beanName, null, null)
            }
        }

        fun stream(): Stream<Any> {
            return resolveStream(false)
        }

        fun orderedStream(): Stream<Any> {
            return resolveStream(true)
        }

        private fun resolveStream(ordered: Boolean): Stream<Any> {
            val descriptorToUse: DependencyDescriptor = StreamDependencyDescriptor(descriptor)
            val result = doResolveDependency(descriptorToUse, beanName, null, null)
            return if (result is Stream<*>) result as Stream<Any> else Stream.of(result)
        }

    }

    override fun getBeanNamesForType(type: ResolvableType): Array<String> {
        return getBeanNamesForType(type, true, true)
    }

    fun getBeanNamesForType(
        type: ResolvableType, includeNonSingletons: Boolean, allowEagerInit: Boolean
    ): Array<String> {
        val resolved = type.resolve()
        return if (resolved != null && !type.hasGenerics()) {
            getBeanNamesForType(resolved, includeNonSingletons, allowEagerInit)
        } else {
            doGetBeanNamesForType(type, includeNonSingletons, allowEagerInit)
        }
    }

    override fun getBeanNamesForType(
        type: Class<*>?, includeNonSingletons: Boolean, allowEagerInit: Boolean
    ): Array<String> {
        if (type == null || !allowEagerInit) {
            return doGetBeanNamesForType(ResolvableType.forRawClass(type), includeNonSingletons, allowEagerInit)
        }
        val cache = if (includeNonSingletons) allBeanNamesByType else singletonBeanNamesByType
        var resolvedBeanNames = cache[type]
        if (resolvedBeanNames != null) {
            return resolvedBeanNames
        }
        resolvedBeanNames = doGetBeanNamesForType(ResolvableType.forRawClass(type), includeNonSingletons, true)
        if (ClassUtil.isCacheSafe(type, getBeanClassLoader())) {
            cache[type] = resolvedBeanNames
        }
        return resolvedBeanNames
    }

    private fun doGetBeanNamesForType(
        type: ResolvableType, includeNonSingletons: Boolean, allowEagerInit: Boolean
    ): Array<String> {
        val result = ArrayList<String>()

        // Check all bean definitions.
        for (beanName in this.beanDefinitionNames) {
            // Only consider bean as eligible if the bean name is not defined as alias for some other bean.
            try {
                val mbd = getMergedLocalBeanDefinition(beanName)
                // Only check bean definition if it is complete.
                if (!mbd.isAbstract() && (allowEagerInit || (mbd.hasBeanClass() || !mbd.isLazyInit()))) {
                    determineTargetType(beanName, mbd)
                    val dbd = mbd.getDecoratedDefinition()
                    var matchFound = false
                    val allowFactoryBeanInit = allowEagerInit || containsSingleton(beanName)
                    if (includeNonSingletons || isSingleton(beanName, mbd, dbd)) {
                        matchFound = isTypeMatch(beanName, type, allowFactoryBeanInit)
                    }
                    if (matchFound) {
                        result.add(beanName)
                    }
                }
            } catch (ex: Exception) {
                when (ex) {
                    is CannotLoadBeanClassException, is BeanDefinitionStoreException -> {
                        if (allowEagerInit) {
                            throw ex
                        }
                        val message =
                            if (ex is CannotLoadBeanClassException) "Ignoring bean class loading failure for bean '$beanName'" else "Ignoring unresolvable metadata in bean definition '$beanName'"
                        logger.trace(message, ex)
                        onSuppressedException(ex)
                    }

                    is NoSuchBeanDefinitionException -> {
                        // Bean definition got removed while we were iterating -> ignore.
                    }
                }
            }
        }

        // Check manually registered singletons too.
        for (beanName in this.manualSingletonNames) {
            try {
                // Match raw bean instance (might be raw FactoryBean).
                if (isTypeMatch(beanName, type)) {
                    result.add(beanName)
                }
            } catch (ex: NoSuchBeanDefinitionException) {
                // Shouldn't happen - probably a result of circular reference resolution...
                logger.trace("Failed to check manually registered singleton with name '$beanName'", ex)
            }
        }
        return result.toTypedArray()
    }

    private fun isSingleton(beanName: String, mbd: RootBeanDefinition, dbd: BeanDefinitionHolder?): Boolean {
        return if (dbd != null) mbd.isSingleton() else isSingleton(beanName)
    }

    private fun clearByTypeCache() {
        this.allBeanNamesByType.clear()
        this.singletonBeanNamesByType.clear()
    }

    @Throws(IllegalStateException::class)
    override fun registerSingleton(beanName: String, singletonObject: Any) {
        super.registerSingleton(beanName, singletonObject)
        updateManualSingletonNames({ set ->
            set.add(
                beanName
            )
        }, {
            !beanDefinitionMap.containsKey(
                beanName
            )
        })
        clearByTypeCache()
    }

    override fun destroySingletons() {
        super.destroySingletons()
        updateManualSingletonNames({ obj -> obj.clear() }, { set -> set.isNotEmpty() })
        clearByTypeCache()
    }

    private fun updateManualSingletonNames(
        action: Consumer<MutableSet<String>>, condition: Predicate<MutableSet<String>>
    ) {
        if (hasBeanCreationStarted()) {
            // Cannot modify startup-time collection elements anymore (for stable iteration)
            synchronized(beanDefinitionMap) {
                if (condition.test(this.manualSingletonNames)) {
                    val updatedSingletons = LinkedHashSet(this.manualSingletonNames)
                    action.accept(updatedSingletons)
                    this.manualSingletonNames = updatedSingletons
                }
            }
        } else {
            // Still in startup registration phase
            if (condition.test(this.manualSingletonNames)) {
                action.accept(this.manualSingletonNames)
            }
        }
    }

    override fun removeBeanDefinition(beanName: String) {
        val bd = beanDefinitionMap.remove(beanName)
        if (bd == null) {
            if (logger.isTraceEnabled) {
                logger.trace("No bean named '$beanName' found in $this")
            }
            throw NoSuchBeanDefinitionException(beanName)
        }

        if (hasBeanCreationStarted()) {
            // Cannot modify startup-time collection elements anymore (for stable iteration)
            synchronized(beanDefinitionMap) {
                val updatedDefinitions = ArrayList<String>(beanDefinitionNames)
                updatedDefinitions.remove(beanName)
                beanDefinitionNames = updatedDefinitions
            }
        } else {
            // Still in startup registration phase
            beanDefinitionNames.remove(beanName)
        }

        resetBeanDefinition(beanName)
    }

    open fun resetBeanDefinition(beanName: String) {
        // Remove the merged bean definition for the given bean, if already created.
        clearMergedBeanDefinition(beanName)

        // Remove corresponding bean from singleton cache, if any. Shouldn't usually
        // be necessary, rather just meant for overriding a context's default beans
        // (e.g. the default StaticMessageSource in a StaticApplicationContext).
        destroySingleton(beanName)

        // Reset all bean definitions that have the given bean as parent (recursively).
        for (bdName in beanDefinitionNames) {
            if (beanName != bdName) {
                val bd = beanDefinitionMap[bdName]
                // Ensure bd is non-null due to potential concurrent modification of beanDefinitionMap.
                if (bd != null && beanName == bd.getParentName()) {
                    resetBeanDefinition(bdName)
                }
            }
        }
    }

    override fun getBeanDefinition(beanName: String): BeanDefinition {
        val bd = beanDefinitionMap[beanName]
        if (bd == null) {
            if (logger.isTraceEnabled) {
                logger.trace("No bean named '$beanName' found in $this")
            }
            throw NoSuchBeanDefinitionException(beanName)
        }
        return bd
    }

    override fun containsBeanDefinition(beanName: String): Boolean {
        return beanDefinitionMap.containsKey(beanName)
    }

    override fun getBeanDefinitionCount(): Int = this.beanDefinitionMap.size

    override fun getBeanDefinitionNames(): Array<String> = this.beanDefinitionNames.toTypedArray()

    private open class NestedDependencyDescriptor(original: DependencyDescriptor) : DependencyDescriptor(
        original
    ) {
        init {
            increaseNestingLevel()
        }
    }

}