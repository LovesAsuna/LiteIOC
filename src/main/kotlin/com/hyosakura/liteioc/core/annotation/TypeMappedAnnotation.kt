package com.hyosakura.liteioc.core.annotation

import com.hyosakura.liteioc.core.annotation.MergedAnnotation.Companion.VALUE
import com.hyosakura.liteioc.util.ClassUtil
import com.hyosakura.liteioc.util.ReflectionUtil
import org.slf4j.Logger
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.util.*
import java.util.function.Function
import java.util.function.Predicate

/**
 * @author LovesAsuna
 **/
class TypeMappedAnnotation<A : Annotation> : AbstractMergedAnnotation<A> {

    companion object {

        private val EMPTY_ARRAYS = mapOf<Class<*>, Any>(
            Boolean::class.javaPrimitiveType!! to BooleanArray(0),
            Byte::class.javaPrimitiveType!! to ByteArray(0),
            Char::class.javaPrimitiveType!! to CharArray(0),
            Double::class.javaPrimitiveType!! to DoubleArray(0),
            Float::class.javaPrimitiveType!! to FloatArray(0),
            Int::class.javaPrimitiveType!! to IntArray(0),
            Long::class.javaPrimitiveType!! to LongArray(0),
            Short::class.javaPrimitiveType!! to ShortArray(0),
            String::class.java to emptyArray<String>()
        )

        fun <A : Annotation> createIfPossible(
            mapping: AnnotationTypeMapping?, annotation: MergedAnnotation<*>, logger: Logger
        ): TypeMappedAnnotation<A>? {
            return if (annotation is TypeMappedAnnotation<*>) {
                createIfPossible(
                    mapping!!, annotation.source,
                    annotation.rootAttributes,
                    annotation.valueExtractor,
                    annotation.aggregateIndex, logger
                )
            } else createIfPossible(
                mapping!!, annotation.getSource(), annotation.synthesize(),
                annotation.getAggregateIndex(), logger
            )
        }


        fun <A : Annotation> createIfPossible(
            mapping: AnnotationTypeMapping, source: Any?, annotation: Annotation, aggregateIndex: Int, logger: Logger
        ): TypeMappedAnnotation<A>? {
            return createIfPossible(
                mapping, source, annotation, ReflectionUtil::invokeMethod, aggregateIndex, logger
            )
        }

        private fun <A : Annotation> createIfPossible(
            mapping: AnnotationTypeMapping,
            source: Any?,
            rootAttribute: Any?,
            valueExtractor: ValueExtractor,
            aggregateIndex: Int,
            logger: Logger
        ): TypeMappedAnnotation<A>? {
            return try {
                TypeMappedAnnotation(
                    mapping, null, source, rootAttribute, valueExtractor, aggregateIndex
                )
            } catch (ex: Exception) {
                AnnotationUtil.rethrowAnnotationConfigurationException(ex)
                if (logger.isInfoEnabled) {
                    val type = mapping.getAnnotationType().name
                    val item =
                        if (mapping.getDistance() == 0) "annotation $type" else "meta-annotation $type from " + mapping.getRoot()
                            .getAnnotationType().name
                    logger.info("Failed to introspect $item", source, ex)
                }
                null
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun extractFromMap(attribute: Method, map: Any?): Any? {
            return if (map != null) (map as Map<String, *>)[attribute.name] else null
        }

    }

    private constructor(
        mapping: AnnotationTypeMapping,
        classLoader: ClassLoader?,
        source: Any?,
        rootAttributes: Any?,
        valueExtractor: ValueExtractor,
        aggregateIndex: Int
    ) : this(mapping, classLoader, source, rootAttributes, valueExtractor, aggregateIndex, null)

    private val mapping: AnnotationTypeMapping

    private val classLoader: ClassLoader?

    private val source: Any?

    private var rootAttributes: Any?

    private val valueExtractor: ValueExtractor

    private var aggregateIndex = 0

    private var useMergedValues = false

    private var attributeFilter: Predicate<String>?

    private val resolvedRootMirrors: IntArray

    private val resolvedMirrors: IntArray

    private constructor(
        mapping: AnnotationTypeMapping,
        classLoader: ClassLoader?,
        source: Any?,
        rootAnnotation: Any?,
        valueExtractor: ValueExtractor,
        aggregateIndex: Int,
        useMergedValues: Boolean,
        attributeFilter: Predicate<String>?,
        resolvedRootMirrors: IntArray,
        resolvedMirrors: IntArray
    ) {
        this.classLoader = classLoader
        this.source = source
        this.rootAttributes = rootAnnotation
        this.valueExtractor = valueExtractor
        this.mapping = mapping
        this.aggregateIndex = aggregateIndex
        this.useMergedValues = useMergedValues
        this.attributeFilter = attributeFilter
        this.resolvedRootMirrors = resolvedRootMirrors
        this.resolvedMirrors = resolvedMirrors
    }

    private constructor(
        mapping: AnnotationTypeMapping,
        classLoader: ClassLoader?,
        source: Any?,
        rootAttributes: Any?,
        valueExtractor: ValueExtractor,
        aggregateIndex: Int,
        resolvedRootMirrors: IntArray?
    ) {
        this.mapping = mapping
        this.classLoader = classLoader
        this.source = source
        this.rootAttributes = rootAttributes
        this.valueExtractor = valueExtractor
        this.aggregateIndex = aggregateIndex
        this.useMergedValues = true
        this.attributeFilter = null
        this.resolvedRootMirrors = resolvedRootMirrors ?: mapping.getRoot().getMirrorSets().resolve(
            source, rootAttributes, valueExtractor
        )
        this.resolvedMirrors = if (getDistance() == 0) this.resolvedRootMirrors else mapping.getMirrorSets()
            .resolve(source, this) { attribute, annotation ->
                this.getValueForMirrorResolution(
                    attribute, annotation!!
                )
            }
    }

    private fun getClassLoader(): ClassLoader? {
        if (this.classLoader != null) {
            return this.classLoader
        }
        if (this.source != null) {
            if (this.source is Class<*>) {
                return this.source.classLoader
            }
            if (source is Member) {
                source.declaringClass.classLoader
            }
        }
        return null
    }

    private fun <T> getValue(attributeIndex: Int, type: Class<T>): T? {
        val attribute = mapping.getAttributes()[attributeIndex]
        var value = getValue(attributeIndex, true, false)
        if (value == null) {
            value = attribute.defaultValue
        }
        return adapt(attribute, value, type)
    }

    private fun getValue(attributeIndex: Int, useConventionMapping: Boolean, forMirrorResolution: Boolean): Any? {
        var attributeIndex = attributeIndex
        var mapping = this.mapping
        if (useMergedValues) {
            var mappedIndex: Int = -1
            if (useConventionMapping) {
                mappedIndex = this.mapping.getConventionMapping(attributeIndex)
            }
            if (mappedIndex != -1) {
                mapping = mapping.getRoot()
                attributeIndex = mappedIndex
            }
        }
        if (!forMirrorResolution) {
            attributeIndex = (if (mapping.getDistance() != 0) resolvedMirrors else resolvedRootMirrors)[attributeIndex]
        }
        if (attributeIndex == -1) {
            return null
        }
        if (mapping.getDistance() == 0) {
            val attribute = mapping.getAttributes()[attributeIndex]
            val result = valueExtractor.extract(attribute, rootAttributes)
            return result ?: attribute.defaultValue
        }
        return getValueFromMetaAnnotation(attributeIndex, forMirrorResolution)
    }

    private fun getValueFromMetaAnnotation(attributeIndex: Int, forMirrorResolution: Boolean): Any? {
        var value: Any? = null
        if (useMergedValues || forMirrorResolution) {
            value = this.mapping.getMappedAnnotationValue(attributeIndex, forMirrorResolution)
        }
        if (value == null) {
            val attribute = this.mapping.getAttributes()[attributeIndex]
            value = ReflectionUtil.invokeMethod(attribute, mapping.getAnnotation())
        }
        return value
    }

    private fun getValueForMirrorResolution(attribute: Method, annotation: Any): Any? {
        val attributeIndex = mapping.getAttributes().indexOf(attribute)
        val valueAttribute = VALUE == attribute.name
        return getValue(attributeIndex, !valueAttribute, true)
    }

    override fun <T> getDefaultValue(attributeName: String, type: Class<T>): T? {
        val attributeIndex: Int = getAttributeIndex(attributeName, false)
        if (attributeIndex == -1) {
            return null
        }
        val attribute = mapping.getAttributes()[attributeIndex]
        return adapt(attribute, attribute.defaultValue, type)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> adapt(attribute: Method, value: Any?, type: Class<T>): T? {
        var value = value
        var type = type
        if (value == null) {
            return null
        }
        value = adaptForAttribute(attribute, value)
        type = getAdaptType(attribute, type)
        if (value is Class<*> && type == String::class.java) {
            value = value.name
        } else if (value is String && type == Class::class.java) {
            value = ClassUtil.resolveClassName(value, getClassLoader())
        } else if (value is Array<*> && value.isArrayOf<Class<*>>() && type == Array<String>::class.java) {
            val classes = value as Array<Class<*>>
            val names = Array(value.size) { i ->
                classes[i].name
            }
            value = names
        } else if (value is Array<*> && value.isArrayOf<String>() && type == emptyArray<Class<*>>()::class.java) {
            val names = value as Array<String>
            val classes = Array(value.size) { i ->
                ClassUtil.resolveClassName(names[i], getClassLoader())
            }
            value = classes
        } else if (value is MergedAnnotation<*> && type.isAnnotation) {
            value = value.synthesize()
        } else if (value is Array<*> && value.isArrayOf<MergedAnnotation<*>>() && type.isArray && type.componentType.isAnnotation) {
            val annotations = value as Array<MergedAnnotation<*>>
            val array = java.lang.reflect.Array.newInstance(type.componentType, annotations.size)
            for (i in value.indices) {
                java.lang.reflect.Array.set(array, i, annotations[i].synthesize())
            }
            value = array
        }
        require(type.isInstance(value)) {
            "Unable to adapt value of type " + value!!.javaClass.name + " to " + type.name
        }
        return value as T
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getAdaptType(attribute: Method, type: Class<T>): Class<T> {
        if (type != Any::class.java) {
            return type
        }
        val attributeType = attribute.returnType
        if (attributeType.isAnnotation) {
            return MergedAnnotation::class.java as Class<T>
        }
        return if (attributeType.isArray && attributeType.componentType.isAnnotation) {
            emptyArray<MergedAnnotation<Annotation>>()::class.java as Class<T>
        } else ClassUtil.resolvePrimitiveIfNecessary(attributeType) as Class<T>
    }

    private fun adaptForAttribute(attribute: Method, value: Any): Any {
        val attributeType = ClassUtil.resolvePrimitiveIfNecessary(attribute.returnType)
        if (attributeType.isArray && !value.javaClass.isArray) {
            val array = java.lang.reflect.Array.newInstance(value.javaClass, 1)
            java.lang.reflect.Array.set(array, 0, value)
            return adaptForAttribute(attribute, array)
        }
        if (attributeType.isAnnotation) {
            return adaptToMergedAnnotation(value, attributeType as Class<out Annotation>)
        }
        if (attributeType.isArray && attributeType.componentType.isAnnotation) {
            val result = Array(java.lang.reflect.Array.getLength(value)) { i ->
                adaptToMergedAnnotation(
                    java.lang.reflect.Array.get(value, i), attributeType.componentType as Class<out Annotation?>
                )
            }
            return result
        }
        if (attributeType == Class::class.java && value is String || attributeType == emptyArray<Class<*>>()::class.java && value is Array<*> && value.isArrayOf<String>() || attributeType == String::class.java && value is Class<*> || attributeType == Array<String>::class.java && value is Array<*> && value.isArrayOf<Class<*>>()) {
            return value
        }
        if (attributeType.isArray && isEmptyObjectArray(value)) {
            return emptyArray(attributeType.componentType)
        }
        check(attributeType.isInstance(value)) {
            "Attribute '" + attribute.name + "' in annotation " + getType().name + " should be compatible with " + attributeType.name + " but a " + value.javaClass.name + " value was returned"
        }
        return value
    }

    private fun isEmptyObjectArray(value: Any): Boolean {
        return value is Array<*> && value.isArrayOf<Any>() && value.size == 0
    }

    private fun emptyArray(componentType: Class<*>): Any {
        var result = EMPTY_ARRAYS[componentType]
        if (result == null) {
            result = java.lang.reflect.Array.newInstance(componentType, 0)
        }
        return result!!
    }

    private fun adaptToMergedAnnotation(value: Any, annotationType: Class<out Annotation>): MergedAnnotation<*> {
        if (value is MergedAnnotation<*>) {
            return value
        }
        val mapping = AnnotationTypeMappings.forAnnotationType(annotationType)[0]
        return TypeMappedAnnotation<Annotation>(
            mapping, null, source, value, getValueExtractor(value), aggregateIndex
        )
    }

    private fun getValueExtractor(value: Any): ValueExtractor {
        if (value is Annotation) {
            return ValueExtractor { method, arg ->
                ReflectionUtil.invokeMethod(method, arg)
            }
        }
        return if (value is Map<*, *>) {
            ValueExtractor { attribute, map ->
                extractFromMap(
                    attribute, map
                )
            }
        } else valueExtractor
    }

    private fun getAttributeIndex(attributeName: String, required: Boolean): Int {
        val attributeIndex = if (isFiltered(attributeName)) -1 else mapping.getAttributes().indexOf(attributeName)
        if (attributeIndex == -1 && required) {
            throw NoSuchElementException(
                "No attribute named '" + attributeName + "' present in merged annotation " + getType().name
            )
        }
        return attributeIndex
    }

    private fun isFiltered(attributeName: String): Boolean {
        return if (attributeFilter != null) {
            !attributeFilter!!.test(attributeName)
        } else false
    }

    override fun <T> getAttributeValue(attributeName: String, type: Class<T>): T? {
        val attributeIndex = getAttributeIndex(attributeName, false)
        return if (attributeIndex != -1) getValue(attributeIndex, type) else null
    }

    override fun createSynthesized(): A {
        TODO("Not yet implemented")
    }

    @Suppress("UNCHECKED_CAST")
    override fun getType(): Class<A> {
        return (mapping.getAnnotationType() as Class<A>)
    }

    override fun isPresent(): Boolean {
        return true
    }

    override fun getDistance(): Int {
        return mapping.getDistance()
    }

    override fun getAggregateIndex(): Int {
        return aggregateIndex
    }

    override fun getSource(): Any? {
        return source
    }

    override fun getMetaSource(): MergedAnnotation<*>? {
        val metaSourceMapping = mapping.getSource() ?: return null
        return TypeMappedAnnotation<Annotation>(
            metaSourceMapping, classLoader, source, rootAttributes, valueExtractor, aggregateIndex, resolvedRootMirrors
        )
    }

    override fun getRoot(): MergedAnnotation<*> {
        if (getDistance() == 0) {
            return this
        }
        val rootMapping = mapping.getRoot()
        return TypeMappedAnnotation<Annotation>(
            rootMapping, classLoader, source, rootAttributes, valueExtractor, aggregateIndex, resolvedRootMirrors
        )
    }

    override fun getMetaTypes(): List<Class<out Annotation>> {
        TODO("Not yet implemented")
    }

    override fun hasDefaultValue(attributeName: String): Boolean {
        val attributeIndex = getAttributeIndex(attributeName, true)
        val value = getValue(attributeIndex, true, false)
        return value == null || mapping.isEquivalentToDefaultValue(attributeIndex, value, valueExtractor)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Annotation> getAnnotation(attributeName: String, type: Class<T>): MergedAnnotation<T> {
        val attributeIndex = getAttributeIndex(attributeName, true)
        val attribute = mapping.getAttributes()[attributeIndex]
        require(type.isAssignableFrom(attribute.returnType)) { "Attribute $attributeName type mismatch:" }
        return (getRequiredValue(attributeIndex, attributeName) as MergedAnnotation<T>)
    }

    private fun getRequiredValue(attributeIndex: Int, attributeName: String): Any {
        return getValue(attributeIndex, Any::class.java) ?: throw NoSuchElementException(
            "No element at attribute index " + attributeIndex + " for name " + attributeName
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Annotation> getAnnotationArray(
        attributeName: String, type: Class<T>
    ): Array<MergedAnnotation<T>> {
        val attributeIndex = getAttributeIndex(attributeName, true)
        val attribute = mapping.getAttributes()[attributeIndex]
        val componentType = attribute.returnType.componentType
        require(type.isAssignableFrom(componentType)) { "Attribute $attributeName component type mismatch:" }
        return getRequiredValue(attributeIndex, attributeName) as Array<MergedAnnotation<T>>
    }

    override fun filterAttributes(predicate: Predicate<String>): MergedAnnotation<A> {
        var predicate = predicate
        if (attributeFilter != null) {
            predicate = attributeFilter!!.and(predicate)
        }
        return TypeMappedAnnotation(
            mapping,
            classLoader,
            source,
            rootAttributes,
            valueExtractor,
            aggregateIndex,
            useMergedValues,
            predicate,
            resolvedRootMirrors,
            resolvedMirrors
        )
    }

    override fun withNonMergedAttributes(): MergedAnnotation<A> {
        return TypeMappedAnnotation(
            mapping, classLoader, source, rootAttributes,
            valueExtractor, aggregateIndex, false, attributeFilter,
            resolvedRootMirrors, resolvedMirrors
        )
    }

    override fun asMap(vararg adaptations: MergedAnnotation.Adapt): Map<String, Any> {
        return Collections.unmodifiableMap(
            asMap(
                { LinkedHashMap() }, *adaptations
            )
        )
    }

    override fun <T : MutableMap<String, Any>> asMap(
        factory: Function<MergedAnnotation<*>, T>, vararg adaptations: MergedAnnotation.Adapt
    ): T {
        val map = factory.apply(this)
        val attributes = mapping.getAttributes()
        for (i in 0 until attributes.size()) {
            val attribute = attributes[i]
            val value =
                if (isFiltered(attribute.name)) null else getValue(i, getTypeForMapOptions(attribute, adaptations))
            if (value != null) {
                map.put(
                    attribute.name,
                    adaptValueForMapOptions(attribute, value, map.javaClass, factory, adaptations)
                )
            }
        }
        return map
    }

    private fun <T : MutableMap<String, Any>> adaptValueForMapOptions(
        attribute: Method, value: Any,
        mapType: Class<*>, factory: Function<MergedAnnotation<*>, T>, adaptations: Array<out MergedAnnotation.Adapt>
    ): Any {
        if (value is MergedAnnotation<*>) {
            return if (MergedAnnotation.Adapt.ANNOTATION_TO_MAP.isIn(*adaptations)) value.asMap(
                factory,
                *adaptations
            ) else value.synthesize()
        }
        if (value is Array<*> && value.isArrayOf<MergedAnnotation<*>>()) {
            @Suppress("UNCHECKED_CAST") val annotations = value as Array<MergedAnnotation<*>>
            if (MergedAnnotation.Adapt.ANNOTATION_TO_MAP.isIn(*adaptations)) {
                val result = java.lang.reflect.Array.newInstance(mapType, value.size)
                for (i in value.indices) {
                    java.lang.reflect.Array.set(result, i, annotations[i].asMap(factory, *adaptations))
                }
                return result
            }
            val result = java.lang.reflect.Array.newInstance(
                attribute.returnType.componentType, value.size
            )
            for (i in value.indices) {
                java.lang.reflect.Array.set(result, i, value[i].synthesize())
            }
            return result
        }
        return value
    }

    private fun getTypeForMapOptions(attribute: Method, adaptations: Array<out MergedAnnotation.Adapt>): Class<*> {
        val attributeType = attribute.returnType
        val componentType = if (attributeType.isArray) attributeType.componentType else attributeType
        return if (MergedAnnotation.Adapt.CLASS_TO_STRING.isIn(*adaptations) && componentType == Class::class.java) {
            if (attributeType.isArray) Array<String>::class.java else String::class.java
        } else Any::class.java
    }

}