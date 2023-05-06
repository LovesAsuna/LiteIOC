package com.hyosakura.liteioc.bean.factory.annotation

import com.hyosakura.liteioc.bean.BeansException
import com.hyosakura.liteioc.bean.PropertyValues
import com.hyosakura.liteioc.bean.factory.*
import com.hyosakura.liteioc.bean.factory.annotation.InjectionMetadata.InjectedElement
import com.hyosakura.liteioc.bean.factory.config.ConfigurableListableBeanFactory
import com.hyosakura.liteioc.bean.factory.config.DependencyDescriptor
import com.hyosakura.liteioc.bean.factory.support.AbstractAutowireCapableBeanFactory
import com.hyosakura.liteioc.bean.factory.support.LookupOverride
import com.hyosakura.liteioc.bean.factory.support.RootBeanDefinition
import com.hyosakura.liteioc.core.BridgeMethodResolver
import com.hyosakura.liteioc.core.MethodParameter
import com.hyosakura.liteioc.core.annotation.AnnotationUtil
import com.hyosakura.liteioc.util.BeanUtil
import com.hyosakura.liteioc.util.ClassUtil
import com.hyosakura.liteioc.util.ReflectionUtil
import org.jetbrains.annotations.Nullable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.beans.PropertyDescriptor
import java.lang.reflect.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * @author LovesAsuna
 **/
class AutowiredAnnotationBeanPostProcessor(private var beanFactory: ConfigurableListableBeanFactory? = null) {

    protected val logger: Logger = LoggerFactory.getLogger(javaClass)

    private val lookupMethodsChecked = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>(256))

    private val candidateConstructorsCache: MutableMap<Class<*>, Array<Constructor<*>>> = ConcurrentHashMap(256)

    private val autowiredAnnotationTypes: MutableSet<Class<out Annotation?>> = LinkedHashSet(4)

    private val injectionMetadataCache: MutableMap<String, InjectionMetadata> = ConcurrentHashMap(256)

    init {
        autowiredAnnotationTypes.add(Autowired::class.java)
        try {
            @Suppress("UNCHECKED_CAST")
            autowiredAnnotationTypes.add(
                ClassUtil.forName(
                    "jakarta.inject.Inject",
                    AbstractAutowireCapableBeanFactory::class.java.classLoader
                ) as Class<out Annotation>
            )
            logger.trace("'jakarta.inject.Inject' annotation found and supported for autowiring")
        } catch (ex: ClassNotFoundException) {
            // jakarta.inject API not available - simply skip.
        }

        try {
            @Suppress("UNCHECKED_CAST")
            autowiredAnnotationTypes.add(
                ClassUtil.forName(
                    "javax.inject.Inject",
                    AbstractAutowireCapableBeanFactory::class.java.classLoader
                ) as Class<out Annotation>
            )
            logger.trace("'javax.inject.Inject' annotation found and supported for autowiring")
        } catch (ex: ClassNotFoundException) {
            // javax.inject API not available - simply skip.
        }
    }

    fun postProcessMergedBeanDefinition(beanDefinition: RootBeanDefinition, beanType: Class<*>, beanName: String) {
        findInjectionMetadata(beanName, beanType, beanDefinition)
    }

    fun postProcessProperties(pvs: PropertyValues, bean: Any, beanName: String): PropertyValues {
        val metadata = findAutowiringMetadata(beanName, bean.javaClass, pvs)
        try {
            metadata.inject(bean, beanName, pvs)
        } catch (ex: BeanCreationException) {
            throw ex
        } catch (ex: Throwable) {
            throw BeanCreationException(beanName, "Injection of autowired dependencies failed", ex)
        }
        return pvs
    }

    fun findInjectionMetadata(
        beanName: String,
        beanType: Class<*>,
        beanDefinition: RootBeanDefinition
    ): InjectionMetadata {
        val metadata = findAutowiringMetadata(beanName, beanType, null)
        metadata.checkConfigMembers(beanDefinition)
        return metadata
    }

    private fun findAutowiringMetadata(
        beanName: String,
        clazz: Class<*>,
        pvs: PropertyValues?
    ): InjectionMetadata {
        val cacheKey = beanName.ifEmpty { clazz.name }
        // Quick check on the concurrent map first, with minimal locking.
        var metadata = this.injectionMetadataCache[cacheKey]
        if (InjectionMetadata.needsRefresh(metadata, clazz)) {
            synchronized(this.injectionMetadataCache) {
                metadata = this.injectionMetadataCache[cacheKey]
                if (InjectionMetadata.needsRefresh(metadata, clazz)) {
                    if (metadata != null) {
                        metadata!!.clear(pvs)
                    }
                    metadata = buildAutowiringMetadata(clazz)
                    this.injectionMetadataCache[cacheKey] = metadata!!
                }
            }
        }
        return metadata!!
    }

    private fun buildAutowiringMetadata(clazz: Class<*>): InjectionMetadata {
        if (!AnnotationUtil.isCandidateClass(clazz, this.autowiredAnnotationTypes)) {
            return InjectionMetadata.EMPTY
        }
        val elements = ArrayList<InjectedElement>()
        var targetClass: Class<*>? = clazz
        do {
            val currElements = ArrayList<InjectedElement>()
            ReflectionUtil.doWithLocalFields(targetClass!!) { field ->
                val ann = findAutowiredAnnotation(field)
                if (ann != null) {
                    if (Modifier.isStatic(field.modifiers)) {
                        if (logger.isInfoEnabled) {
                            logger.info("Autowired annotation is not supported on static fields: $field")
                        }
                        return@doWithLocalFields
                    }
                    val required = determineRequiredStatus(ann)
                    currElements.add(
                        AutowiredFieldElement(
                            field,
                            required
                        )
                    )
                }
            }
            ReflectionUtil.doWithLocalMethods(targetClass) { method ->
                val bridgedMethod = BridgeMethodResolver.findBridgedMethod(method)
                if (!BridgeMethodResolver.isVisibilityBridgeMethodPair(method, bridgedMethod)) {
                    return@doWithLocalMethods
                }
                val ann = findAutowiredAnnotation(bridgedMethod)
                if (ann != null && method == ClassUtil.getMostSpecificMethod(method, clazz)) {
                    if (Modifier.isStatic(method.modifiers)) {
                        if (logger.isInfoEnabled) {
                            logger.info("Autowired annotation is not supported on static methods: $method")
                        }
                        return@doWithLocalMethods
                    }
                    if (method.parameterCount == 0) {
                        if (logger.isInfoEnabled) {
                            logger.info(
                                "Autowired annotation should only be used on methods with parameters: " +
                                        method
                            )
                        }
                    }
                    val required = determineRequiredStatus(ann)
                    val pd = BeanUtil.findPropertyForMethod(bridgedMethod, clazz)
                    currElements.add(
                        AutowiredMethodElement(
                            method,
                            required,
                            pd
                        )
                    )
                }
            }
            elements.addAll(0, currElements)
            targetClass = targetClass.superclass
        } while (targetClass != null && targetClass != Any::class.java)
        return InjectionMetadata.forElements(elements, clazz)
    }

    fun determineCandidateConstructors(beanClass: Class<*>, beanName: String): Array<Constructor<*>>? {
        // Let's check for lookup methods here...
        // 检查lookup-method
        if (!lookupMethodsChecked.contains(beanName)) {
            if (AnnotationUtil.isCandidateClass(beanClass, Lookup::class.java)) {
                try {
                    var targetClass = beanClass
                    do {
                        ReflectionUtil.doWithLocalMethods(targetClass) { method ->
                            val lookup = method.getAnnotation(Lookup::class.java)
                            if (lookup != null) {
                                val `override` = LookupOverride(method, lookup.value)
                                try {
                                    val mbd = this.beanFactory!!.getMergedBeanDefinition(beanName) as RootBeanDefinition
                                    mbd.getMethodOverrides().addOverride(override)
                                } catch (ex: NoSuchBeanDefinitionException) {
                                    throw BeanCreationException(
                                        beanName,
                                        "Cannot apply @Lookup to beans without corresponding bean definition"
                                    )
                                }
                            }
                        }
                        targetClass = targetClass.superclass
                    } while (targetClass != null && targetClass != Any::class.java)
                } catch (ex: IllegalStateException) {
                    throw BeanCreationException(beanName, "Lookup method resolution failed", ex)
                }
            }
            lookupMethodsChecked.add(beanName)
        }
        // Quick check on the concurrent map first, with minimal locking.
        var candidateConstructors = this.candidateConstructorsCache[beanClass]
        if (candidateConstructors == null) {
            // Fully synchronized resolution now...
            synchronized(this.candidateConstructorsCache) {
                candidateConstructors = this.candidateConstructorsCache[beanClass]
                if (candidateConstructors == null) {
                    val rawCandidates: Array<Constructor<*>> = try {
                        // 拿到bean的所有构造方法
                        beanClass.declaredConstructors
                    } catch (ex: Throwable) {
                        throw BeanCreationException(
                            beanName,
                            "Resolution of declared constructors on bean Class [" + beanClass.name + "] from ClassLoader [" + beanClass.classLoader + "] failed",
                            ex
                        )
                    }
                    val candidates = ArrayList<Constructor<*>>(rawCandidates.size)
                    var requiredConstructor: Constructor<*>? = null
                    var defaultConstructor: Constructor<*>? = null
                    val primaryConstructor = BeanUtil.findPrimaryConstructor(beanClass)
                    var nonSyntheticConstructors = 0
                    for (candidate in rawCandidates) {
                        if (!candidate.isSynthetic) {
                            nonSyntheticConstructors++
                        } else if (primaryConstructor != null) {
                            continue
                        }
                        var ann = findAutowiredAnnotation(candidate)
                        if (ann == null) {
                            val userClass = ClassUtil.getUserClass(beanClass)
                            if (userClass != beanClass) {
                                try {
                                    val superCtor = userClass.getDeclaredConstructor(*candidate.parameterTypes)
                                    ann = findAutowiredAnnotation(superCtor)
                                } catch (ex: NoSuchMethodException) {
                                    // Simply proceed, no equivalent superclass constructor found...
                                }
                            }
                        }
                        if (ann != null) {
                            if (requiredConstructor != null) {
                                throw BeanCreationException(
                                    beanName,
                                    "Invalid autowire-marked constructor: " + candidate +
                                            ". Found constructor with 'required' Autowired annotation already: " +
                                            requiredConstructor
                                )
                            }
                            val required: Boolean = determineRequiredStatus(ann)
                            if (required) {
                                if (candidates.isNotEmpty()) {
                                    throw BeanCreationException(
                                        beanName,
                                        "Invalid autowire-marked constructors: " + candidates +
                                                ". Found constructor with 'required' Autowired annotation: " +
                                                candidate
                                    )
                                }
                                requiredConstructor = candidate
                            }
                            candidates.add(candidate)
                        } else if (candidate.parameterCount == 0) {
                            defaultConstructor = candidate
                        }
                    }
                    if (candidates.isNotEmpty()) {
                        // Add default constructor to list of optional constructors, as fallback.
                        if (requiredConstructor == null) {
                            if (defaultConstructor != null) {
                                candidates.add(defaultConstructor)
                            } else if (candidates.size == 1 && logger.isInfoEnabled) {
                                logger.info(
                                    "Inconsistent constructor declaration on bean with name '" + beanName +
                                            "': single autowire-marked constructor flagged as optional - " +
                                            "this constructor is effectively required since there is no " +
                                            "default constructor to fall back to: " + candidates[0]
                                )
                            }
                        }
                        candidateConstructors = candidates.toTypedArray()
                    } else if (rawCandidates.size == 1 && rawCandidates[0].parameterCount > 0) {
                        candidateConstructors = arrayOf(rawCandidates[0])
                    } else if (nonSyntheticConstructors == 2 && primaryConstructor != null && defaultConstructor != null && primaryConstructor != defaultConstructor
                    ) {
                        candidateConstructors = arrayOf(primaryConstructor, defaultConstructor)
                    } else if (nonSyntheticConstructors == 1 && primaryConstructor != null) {
                        candidateConstructors = arrayOf(primaryConstructor)
                    } else {
                        candidateConstructors = emptyArray()
                    }
                    this.candidateConstructorsCache[beanClass] = candidateConstructors!!
                }
            }
        }
        return if (candidateConstructors!!.isNotEmpty()) candidateConstructors else null
    }

    private fun findAutowiredAnnotation(ao: AccessibleObject): Annotation? {
        val annotations = ao.annotations
        for (type in this.autowiredAnnotationTypes) {
            for (annotation in annotations) {
                if (type.isInstance(annotation)) {
                    return annotation
                }
            }
        }
        return null
    }

    protected fun determineRequiredStatus(ann: Annotation): Boolean {
        return try {
            val required = ann::class.java.getDeclaredMethod("required")
            required.invoke(ann) as Boolean
        } catch (ignore: Exception) {
            false
        }
    }

    private inner class AutowiredFieldElement(field: Field, private val required: Boolean) :
        InjectedElement(field, null) {

        @Volatile
        private var cached = false

        @Volatile
        private var cachedFieldValue: Any? = null

        @Throws(Throwable::class)
        override fun inject(bean: Any, beanName: String?, pvs: PropertyValues?) {
            val field = member as Field
            val value: Any? = if (cached) {
                try {
                    resolvedCachedArgument(beanName, cachedFieldValue)
                } catch (ex: NoSuchBeanDefinitionException) {
                    // Unexpected removal of target bean for cached argument -> re-resolve
                    resolveFieldValue(field, bean, beanName)
                }
            } else {
                resolveFieldValue(field, bean, beanName)
            }
            if (value != null) {
                ReflectionUtil.makeAccessible(field)
                field[bean] = value
            }
        }

        @Nullable
        private fun resolveFieldValue(field: Field, bean: Any, beanName: String?): Any? {
            val desc = DependencyDescriptor(field, required)
            desc.setContainingClass(bean.javaClass)
            val autowiredBeanNames = LinkedHashSet<String>(1)
            requireNotNull(beanFactory) { "No BeanFactory available" }
            val typeConverter = beanFactory!!.getTypeConverter()
            val value: Any? = try {
                beanFactory!!.resolveDependency(desc, beanName, autowiredBeanNames, typeConverter)
            } catch (ex: BeansException) {
                throw UnsatisfiedDependencyException(beanName, InjectionPoint(field), ex)
            }
            synchronized(this) {
                if (!cached) {
                    var cachedFieldValue: Any? = null
                    if (value != null || required) {
                        cachedFieldValue = desc
                        registerDependentBeans(beanName, autowiredBeanNames)
                        if (autowiredBeanNames.size == 1) {
                            val autowiredBeanName = autowiredBeanNames.iterator().next()
                            if (beanFactory!!.containsBean(autowiredBeanName) &&
                                beanFactory!!.isTypeMatch(autowiredBeanName, field.type)
                            ) {
                                cachedFieldValue =
                                    ShortcutDependencyDescriptor(
                                        desc, autowiredBeanName, field.type
                                    )
                            }
                        }
                    }
                    this.cachedFieldValue = cachedFieldValue
                    cached = true
                }
            }
            return value
        }
    }

    private inner class AutowiredMethodElement(
        method: Method,
        private val required: Boolean,
        pd: PropertyDescriptor?
    ) : InjectedElement(method, pd) {

        @Volatile
        private var cached = false

        @Volatile
        private var cachedMethodArguments: Array<Any>? = null

        @Throws(Throwable::class)
        override fun inject(bean: Any, beanName: String?, pvs: PropertyValues?) {
            if (checkPropertySkipping(pvs)) {
                return
            }
            val method = member as Method
            val arguments: Array<Any?>? = if (cached) {
                try {
                    resolveCachedArguments(beanName)
                } catch (ex: NoSuchBeanDefinitionException) {
                    // Unexpected removal of target bean for cached argument -> re-resolve
                    resolveMethodArguments(method, bean, beanName)
                }
            } else {
                resolveMethodArguments(method, bean, beanName)
            }
            if (arguments != null) {
                try {
                    ReflectionUtil.makeAccessible(method)
                    method.invoke(bean, *arguments)
                } catch (ex: InvocationTargetException) {
                    throw ex.targetException
                }
            }
        }

        private fun resolveCachedArguments(beanName: String?): Array<Any?>? {
            val cachedMethodArguments = cachedMethodArguments ?: return null
            val arguments = arrayOfNulls<Any>(cachedMethodArguments.size)
            for (i in arguments.indices) {
                arguments[i] = resolvedCachedArgument(beanName, cachedMethodArguments[i])
            }
            return arguments
        }

        private fun resolveMethodArguments(method: Method, bean: Any, beanName: String?): Array<Any?> {
            val argumentCount = method.parameterCount
            var arguments: Array<Any?>? = arrayOfNulls<Any?>(argumentCount)
            val descriptors = arrayOfNulls<DependencyDescriptor>(argumentCount)
            val autowiredBeans = LinkedHashSet<String>(argumentCount)
            requireNotNull(beanFactory) { "No BeanFactory available" }
            val typeConverter = beanFactory!!.getTypeConverter()
            for (i in arguments!!.indices) {
                val methodParam = MethodParameter(method, i)
                val currDesc = DependencyDescriptor(methodParam, required)
                currDesc.setContainingClass(bean.javaClass)
                descriptors[i] = currDesc
                try {
                    val arg = beanFactory!!.resolveDependency(currDesc, beanName, autowiredBeans, typeConverter)
                    if (arg == null && !required) {
                        arguments = null
                        break
                    }
                    arguments[i] = arg
                } catch (ex: BeansException) {
                    throw UnsatisfiedDependencyException(beanName, InjectionPoint(methodParam), ex)
                }
            }
            synchronized(this) {
                if (!cached) {
                    if (arguments != null) {
                        val cachedMethodArguments = descriptors.copyOf(arguments.size)
                        registerDependentBeans(beanName, autowiredBeans)
                        if (autowiredBeans.size == argumentCount) {
                            val it = autowiredBeans.iterator()
                            val paramTypes = method.parameterTypes
                            for (i in paramTypes.indices) {
                                val autowiredBeanName = it.next()
                                if (beanFactory!!.containsBean(autowiredBeanName) &&
                                    beanFactory!!.isTypeMatch(autowiredBeanName, paramTypes[i])
                                ) {
                                    cachedMethodArguments[i] =
                                        ShortcutDependencyDescriptor(
                                            descriptors[i]!!, autowiredBeanName, paramTypes[i]
                                        )
                                }
                            }
                        }
                        this.cachedMethodArguments = cachedMethodArguments.filterNotNull().toTypedArray()
                    } else {
                        cachedMethodArguments = null
                    }
                    cached = true
                }
            }
            return arguments
        }
    }

    private fun registerDependentBeans(beanName: String?, autowiredBeanNames: Set<String>) {
        if (beanName != null) {
            for (autowiredBeanName: String in autowiredBeanNames) {
                if (beanFactory != null && beanFactory!!.containsBean(autowiredBeanName)) {
                    beanFactory!!.registerDependentBean(autowiredBeanName, beanName)
                }
                if (logger.isTraceEnabled) {
                    logger.trace(
                        "Autowiring by type from bean name '" + beanName +
                                "' to bean named '" + autowiredBeanName + "'"
                    )
                }
            }
        }
    }

    private fun resolvedCachedArgument(beanName: String?, cachedArgument: Any?): Any? {
        return if (cachedArgument is DependencyDescriptor) {
            requireNotNull(beanFactory) { "No BeanFactory available" }
            beanFactory!!.resolveDependency(cachedArgument, beanName, null, null)
        } else {
            cachedArgument
        }
    }

    private class ShortcutDependencyDescriptor(
        original: DependencyDescriptor,
        private val shortcut: String,
        private val requiredType: Class<*>
    ) : DependencyDescriptor(original) {

        override fun resolveShortcut(beanFactory: BeanFactory): Any {
            return beanFactory.getBean(shortcut, requiredType)
        }

    }

}
