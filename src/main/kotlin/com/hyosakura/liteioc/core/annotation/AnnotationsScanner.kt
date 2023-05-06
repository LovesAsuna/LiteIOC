package com.hyosakura.liteioc.core.annotation

import com.hyosakura.liteioc.core.BridgeMethodResolver
import com.hyosakura.liteioc.core.Ordered
import com.hyosakura.liteioc.core.ResolvableType
import com.hyosakura.liteioc.core.annotation.MergedAnnotations.Search
import com.hyosakura.liteioc.core.annotation.MergedAnnotations.SearchStrategy
import com.hyosakura.liteioc.util.ObjectUtil
import com.hyosakura.liteioc.util.ReflectionUtil
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate

object AnnotationsScanner {

    private val NO_ANNOTATIONS = emptyArray<Annotation>()

    private val NO_METHODS = emptyArray<Method>()

    private val declaredAnnotationCache: MutableMap<AnnotatedElement, Array<Annotation>> = ConcurrentHashMap(256)

    private val baseTypeMethodsCache: MutableMap<Class<*>, Array<Method>> = ConcurrentHashMap(256)

    fun <C, R> scan(
        context: C, source: AnnotatedElement, searchStrategy: SearchStrategy,
        searchEnclosingClass: Predicate<Class<*>>, processor: AnnotationsProcessor<C, R>
    ): R? {
        val result = process(context, source, searchStrategy, searchEnclosingClass, processor)
        return processor.finish(result)
    }


    private fun <C, R> process(
        context: C, source: AnnotatedElement,
        searchStrategy: SearchStrategy, searchEnclosingClass: Predicate<Class<*>>,
        processor: AnnotationsProcessor<C, R>
    ): R? {
        if (source is Class<*>) {
            return processClass(context, source, searchStrategy, searchEnclosingClass, processor)
        }
        return if (source is Method) {
            processMethod(context, source, searchStrategy, processor)
        } else processElement(context, source, processor)
    }

    @Suppress("deprecation")
    private fun <C, R> processClass(
        context: C, source: Class<*>, searchStrategy: SearchStrategy,
        searchEnclosingClass: Predicate<Class<*>>, processor: AnnotationsProcessor<C, R>
    ): R? {
        return when (searchStrategy) {
            SearchStrategy.DIRECT -> processElement(context, source, processor)
            SearchStrategy.INHERITED_ANNOTATIONS -> processClassInheritedAnnotations(
                context,
                source,
                searchStrategy,
                processor
            )

            SearchStrategy.SUPERCLASS -> processClassHierarchy(context, source, processor, false, Search.never)
            SearchStrategy.TYPE_HIERARCHY -> processClassHierarchy(
                context,
                source,
                processor,
                true,
                searchEnclosingClass
            )
        }
    }

    private fun <C, R> processClassInheritedAnnotations(
        context: C, source: Class<*>,
        searchStrategy: SearchStrategy, processor: AnnotationsProcessor<C, R>
    ): R? {
        var source = source
        try {
            if (isWithoutHierarchy(source, searchStrategy, Search.never)) {
                return processElement(context, source, processor)
            }
            var relevant: Array<Annotation?>? = null
            var remaining = Int.MAX_VALUE
            var aggregateIndex = 0
            val root = source
            while (source != Any::class.java && remaining > 0 && !hasPlainJavaAnnotationsOnly(source)) {
                var result = processor.doWithAggregate(context, aggregateIndex)
                if (result != null) {
                    return result
                }
                val declaredAnnotations = getDeclaredAnnotations(source, true)
                if (relevant == null && declaredAnnotations.isNotEmpty()) {
                    relevant = root.annotations
                    remaining = relevant.size
                }
                val declaredAnnotationList = mutableListOf<Annotation>()
                for (i in declaredAnnotations.indices) {
                    var isRelevant = false
                    for (relevantIndex in relevant!!.indices) {
                        if (relevant[relevantIndex] != null && declaredAnnotations[i].javaClass == relevant[relevantIndex]!!.javaClass) {
                            isRelevant = true
                            relevant[relevantIndex] = null
                            remaining--
                            break
                        }
                    }
                    if (isRelevant) {
                        declaredAnnotationList.add(declaredAnnotations[i])
                    }
                }
                result =
                    processor.doWithAnnotations(context, aggregateIndex, source, declaredAnnotationList.toTypedArray())
                if (result != null) {
                    return result
                }
                source = source.superclass
                aggregateIndex++
            }
        } catch (ex: Throwable) {
            AnnotationUtil.handleIntrospectionFailure(source, ex)
        }
        return null
    }

    private fun <C, R> processClassHierarchy(
        context: C, source: Class<*>,
        processor: AnnotationsProcessor<C, R>, includeInterfaces: Boolean,
        searchEnclosingClass: Predicate<Class<*>>
    ): R? {
        return processClassHierarchy(
            context, intArrayOf(0), source, processor,
            includeInterfaces, searchEnclosingClass
        )
    }

    private fun <C, R> processClassHierarchy(
        context: C, aggregateIndex: IntArray, source: Class<*>,
        processor: AnnotationsProcessor<C, R>, includeInterfaces: Boolean,
        searchEnclosingClass: Predicate<Class<*>>
    ): R? {
        try {
            var result = processor.doWithAggregate(context, aggregateIndex[0])
            if (result != null) {
                return result
            }
            if (hasPlainJavaAnnotationsOnly(source)) {
                return null
            }
            val annotations = getDeclaredAnnotations(source, false)
            result = processor.doWithAnnotations(context, aggregateIndex[0], source, annotations)
            if (result != null) {
                return result
            }
            aggregateIndex[0]++
            if (includeInterfaces) {
                for (interfaceType in source.interfaces) {
                    val interfacesResult = processClassHierarchy(
                        context, aggregateIndex,
                        interfaceType, processor, true, searchEnclosingClass
                    )
                    if (interfacesResult != null) {
                        return interfacesResult
                    }
                }
            }
            val superclass = source.superclass
            if (superclass != Any::class.java && superclass != null) {
                val superclassResult = processClassHierarchy(
                    context, aggregateIndex,
                    superclass, processor, includeInterfaces, searchEnclosingClass
                )
                if (superclassResult != null) {
                    return superclassResult
                }
            }
            if (searchEnclosingClass.test(source)) {
                // Since merely attempting to load the enclosing class may result in
                // automatic loading of sibling nested classes that in turn results
                // in an exception such as NoClassDefFoundError, we wrap the following
                // in its own dedicated try-catch block in order not to preemptively
                // halt the annotation scanning process.
                try {
                    val enclosingClass = source.enclosingClass
                    if (enclosingClass != null) {
                        val enclosingResult = processClassHierarchy(
                            context, aggregateIndex,
                            enclosingClass, processor, includeInterfaces, searchEnclosingClass
                        )
                        if (enclosingResult != null) {
                            return enclosingResult
                        }
                    }
                } catch (ex: Throwable) {
                    AnnotationUtil.handleIntrospectionFailure(source, ex)
                }
            }
        } catch (ex: Throwable) {
            AnnotationUtil.handleIntrospectionFailure(source, ex)
        }
        return null
    }

    @Suppress("deprecation")
    private fun <C, R> processMethod(
        context: C, source: Method,
        searchStrategy: SearchStrategy, processor: AnnotationsProcessor<C, R>
    ): R? {
        return when (searchStrategy) {
            SearchStrategy.DIRECT, SearchStrategy.INHERITED_ANNOTATIONS -> processMethodInheritedAnnotations(
                context,
                source,
                processor
            )

            SearchStrategy.SUPERCLASS -> processMethodHierarchy(
                context, intArrayOf(0), source.declaringClass,
                processor, source, false
            )

            SearchStrategy.TYPE_HIERARCHY -> processMethodHierarchy(
                context, intArrayOf(0), source.declaringClass,
                processor, source, true
            )
        }
    }

    private fun <C, R> processMethodInheritedAnnotations(
        context: C, source: Method,
        processor: AnnotationsProcessor<C, R>
    ): R? {
        try {
            val result = processor.doWithAggregate(context, 0)
            return result ?: processMethodAnnotations(context, 0, source, processor)
        } catch (ex: Throwable) {
            AnnotationUtil.handleIntrospectionFailure(source, ex)
        }
        return null
    }

    private fun <C, R> processMethodHierarchy(
        context: C, aggregateIndex: IntArray,
        sourceClass: Class<*>, processor: AnnotationsProcessor<C, R>, rootMethod: Method,
        includeInterfaces: Boolean
    ): R? {
        try {
            var result = processor.doWithAggregate(context, aggregateIndex[0])
            if (result != null) {
                return result
            }
            if (hasPlainJavaAnnotationsOnly(sourceClass)) {
                return null
            }
            var calledProcessor = false
            if (sourceClass == rootMethod.declaringClass) {
                result = processMethodAnnotations(
                    context, aggregateIndex[0],
                    rootMethod, processor
                )
                calledProcessor = true
                if (result != null) {
                    return result
                }
            } else {
                for (candidateMethod in getBaseTypeMethods(context, sourceClass)) {
                    if (candidateMethod != null && isOverride(rootMethod, candidateMethod)) {
                        result = processMethodAnnotations(
                            context, aggregateIndex[0],
                            candidateMethod, processor
                        )
                        calledProcessor = true
                        if (result != null) {
                            return result
                        }
                    }
                }
            }
            if (Modifier.isPrivate(rootMethod.modifiers)) {
                return null
            }
            if (calledProcessor) {
                aggregateIndex[0]++
            }
            if (includeInterfaces) {
                for (interfaceType in sourceClass.interfaces) {
                    val interfacesResult = processMethodHierarchy(
                        context, aggregateIndex,
                        interfaceType, processor, rootMethod, true
                    )
                    if (interfacesResult != null) {
                        return interfacesResult
                    }
                }
            }
            val superclass = sourceClass.superclass
            if (superclass != Any::class.java && superclass != null) {
                val superclassResult = processMethodHierarchy(
                    context, aggregateIndex,
                    superclass, processor, rootMethod, includeInterfaces
                )
                if (superclassResult != null) {
                    return superclassResult
                }
            }
        } catch (ex: Throwable) {
            AnnotationUtil.handleIntrospectionFailure(rootMethod, ex)
        }
        return null
    }

    private fun <C> getBaseTypeMethods(context: C, baseType: Class<*>): Array<Method> {
        if (baseType == Any::class.java || hasPlainJavaAnnotationsOnly(baseType)) {
            return NO_METHODS
        }
        var methods = baseTypeMethodsCache[baseType]
        if (methods == null) {
            val isInterface = baseType.isInterface
            methods = if (isInterface) baseType.methods else ReflectionUtil.getDeclaredMethods(baseType)
            var cleared = 0
            val methodList = mutableListOf<Method>()
            for (i in methods!!.indices) {
                if (!isInterface && Modifier.isPrivate(methods[i].modifiers) ||
                    hasPlainJavaAnnotationsOnly(methods[i]) || getDeclaredAnnotations(
                        methods[i], false
                    ).isEmpty()
                ) {
                    cleared++
                } else {
                    methodList.add(methods[i])
                }
            }
            if (cleared == methods.size) {
                methods = NO_METHODS
            }
            methods = methodList.toTypedArray()
            baseTypeMethodsCache[baseType] = methods
        }
        return methods
    }

    private fun isOverride(rootMethod: Method, candidateMethod: Method): Boolean {
        return !Modifier.isPrivate(candidateMethod.modifiers) && candidateMethod.name == rootMethod.name &&
                hasSameParameterTypes(rootMethod, candidateMethod)
    }

    private fun hasSameParameterTypes(rootMethod: Method, candidateMethod: Method): Boolean {
        if (candidateMethod.parameterCount != rootMethod.parameterCount) {
            return false
        }
        val rootParameterTypes = rootMethod.parameterTypes
        val candidateParameterTypes = candidateMethod.parameterTypes
        return if (Arrays.equals(candidateParameterTypes, rootParameterTypes)) {
            true
        } else hasSameGenericTypeParameters(
            rootMethod, candidateMethod,
            rootParameterTypes
        )
    }

    private fun hasSameGenericTypeParameters(
        rootMethod: Method, candidateMethod: Method, rootParameterTypes: Array<Class<*>>
    ): Boolean {
        val sourceDeclaringClass = rootMethod.declaringClass
        val candidateDeclaringClass = candidateMethod.declaringClass
        if (!candidateDeclaringClass.isAssignableFrom(sourceDeclaringClass)) {
            return false
        }
        for (i in rootParameterTypes.indices) {
            val resolvedParameterType = ResolvableType.forMethodParameter(
                candidateMethod, i, sourceDeclaringClass
            ).resolve()
            if (rootParameterTypes[i] != resolvedParameterType) {
                return false
            }
        }
        return true
    }

    private fun <C, R> processMethodAnnotations(
        context: C, aggregateIndex: Int, source: Method,
        processor: AnnotationsProcessor<C, R>
    ): R? {
        val annotations = getDeclaredAnnotations(source, false)
        val result = processor.doWithAnnotations(context, aggregateIndex, source, annotations)
        if (result != null) {
            return result
        }
        val bridgedMethod = BridgeMethodResolver.findBridgedMethod(source)
        if (bridgedMethod != source) {
            val bridgedAnnotations = getDeclaredAnnotations(bridgedMethod, true)
            val bridgedAnnotationList = mutableListOf<Annotation>()
            for (i in bridgedAnnotations.indices) {
                if (!ObjectUtil.containsElement(arrayOf(annotations), bridgedAnnotations[i])) {
                    bridgedAnnotationList.add(bridgedAnnotations[i])
                }
            }
            return processor.doWithAnnotations(context, aggregateIndex, source, bridgedAnnotationList.toTypedArray())
        }
        return null
    }

    private fun <C, R> processElement(
        context: C, source: AnnotatedElement,
        processor: AnnotationsProcessor<C, R>
    ): R? {
        try {
            val result = processor.doWithAggregate(context, 0)
            return result
                ?: processor.doWithAnnotations(
                    context, 0, source, getDeclaredAnnotations(source, false)
                )
        } catch (ex: Throwable) {
            AnnotationUtil.handleIntrospectionFailure(source, ex)
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    fun <A : Annotation> getDeclaredAnnotation(source: AnnotatedElement, annotationType: Class<A>): A? {
        val annotations = getDeclaredAnnotations(source, false)
        for (annotation in annotations) {
            if (annotationType == annotation.javaClass) {
                return annotation as A
            }
        }
        return null
    }

    fun getDeclaredAnnotations(source: AnnotatedElement, defensive: Boolean): Array<Annotation> {
        var cached = false
        var annotations = declaredAnnotationCache[source]
        if (annotations != null) {
            cached = true
        } else {
            annotations = source.declaredAnnotations
            if (annotations.isNotEmpty()) {
                val annotationList = mutableListOf<Annotation>()
                var allIgnored = true
                for (i in annotations.indices) {
                    val annotation = annotations[i]
                    if (!isIgnorable(annotation.annotationClass.java) && AttributeMethods.forAnnotationType(annotation.annotationClass.java)
                            .isValid(annotation)
                    ) {
                        annotationList.add(annotations[i])
                        allIgnored = false
                    }
                }
                annotations = if (allIgnored) NO_ANNOTATIONS else annotationList.toTypedArray()
                if (source is Class<*> || source is Member) {
                    declaredAnnotationCache[source] = annotations
                    cached = true
                }
            }
        }
        return if (!defensive || annotations!!.isEmpty() || !cached) {
            annotations!!
        } else annotations.clone()
    }

    private fun isIgnorable(annotationType: Class<*>): Boolean {
        return AnnotationFilter.PLAIN.matches(annotationType)
    }

    fun isKnownEmpty(
        source: AnnotatedElement, searchStrategy: SearchStrategy,
        searchEnclosingClass: Predicate<Class<*>>
    ): Boolean {
        if (hasPlainJavaAnnotationsOnly(source)) {
            return true
        }
        return if (searchStrategy == SearchStrategy.DIRECT || isWithoutHierarchy(
                source,
                searchStrategy,
                searchEnclosingClass
            )
        ) {
            if (source is Method && source.isBridge) {
                false
            } else getDeclaredAnnotations(source, false).isEmpty()
        } else false
    }

    fun hasPlainJavaAnnotationsOnly(annotatedElement: Any?): Boolean {
        return when (annotatedElement) {
            is Class<*> -> {
                hasPlainJavaAnnotationsOnly(annotatedElement)
            }

            is Member -> {
                hasPlainJavaAnnotationsOnly(annotatedElement.declaringClass)
            }

            else -> {
                false
            }
        }
    }

    fun hasPlainJavaAnnotationsOnly(type: Class<*>): Boolean {
        return type.name.startsWith("java.") || type == Ordered::class.java
    }

    @Suppress("deprecation")
    private fun isWithoutHierarchy(
        source: AnnotatedElement, searchStrategy: SearchStrategy,
        searchEnclosingClass: Predicate<Class<*>>
    ): Boolean {
        if (source == Any::class.java) {
            return true
        }
        if (source is Class<*>) {
            val noSuperTypes = source.superclass == Any::class.java &&
                    source.interfaces.size == 0
            return if (searchEnclosingClass.test(source)) noSuperTypes &&
                    source.enclosingClass == null else noSuperTypes
        }
        return if (source is Method) {
            Modifier.isPrivate(source.modifiers) ||
                    isWithoutHierarchy(source.declaringClass, searchStrategy, searchEnclosingClass)
        } else true
    }

    fun clearCache() {
        declaredAnnotationCache.clear()
        baseTypeMethodsCache.clear()
    }
}