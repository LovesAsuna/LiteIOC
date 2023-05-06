package com.hyosakura.liteioc.core.annotation

import com.hyosakura.liteioc.util.ObjectUtil
import com.hyosakura.liteioc.util.ReflectionUtil
import java.lang.reflect.Method
import java.util.*

/**
 * @author LovesAsuna
 **/
class AnnotationTypeMapping {

    companion object {

        private val EMPTY_MIRROR_SETS: Array<MirrorSets.MirrorSet> = emptyArray()

        private fun <T> merge(existing: List<T>?, element: T): List<T> {
            if (existing == null) {
                return listOf(element)
            }
            val merged: MutableList<T> = ArrayList(existing.size + 1)
            merged.addAll(existing)
            merged.add(element)
            return Collections.unmodifiableList(merged)
        }

        private fun filledIntArray(size: Int): IntArray {
            val array = IntArray(size)
            Arrays.fill(array, -1)
            return array
        }

        private fun isEquivalentToDefaultValue(
            attribute: Method, value: Any,
            valueExtractor: ValueExtractor
        ): Boolean {
            return areEquivalent(attribute.defaultValue, value, valueExtractor)
        }

        @Suppress("UNCHECKED_CAST")
        private fun areEquivalent(
            value: Any?, extractedValue: Any?,
            valueExtractor: ValueExtractor
        ): Boolean {
            if (ObjectUtil.nullSafeEquals(value, extractedValue)) {
                return true
            }
            if (value is Class<*> && extractedValue is String) {
                return areEquivalent(value, extractedValue)
            }
            if (value is Array<*> && value.isArrayOf<Class<*>>() && extractedValue is Array<*> && extractedValue.isArrayOf<String>()) {
                return areEquivalent(value as Array<Class<*>>, extractedValue as Array<String>)
            }
            return if (value is Annotation) {
                areEquivalent(value, extractedValue, valueExtractor)
            } else false
        }

        private fun areEquivalent(value: Class<*>, extractedValue: String): Boolean {
            return value.name == extractedValue
        }

        private fun areEquivalent(value: Array<Class<*>>, extractedValue: Array<String>): Boolean {
            if (value.size != extractedValue.size) {
                return false
            }
            for (i in value.indices) {
                if (!areEquivalent(value[i], extractedValue[i])) {
                    return false
                }
            }
            return true
        }

    }

    private val source: AnnotationTypeMapping?

    private val root: AnnotationTypeMapping

    private var distance = 0

    private val annotationType: Class<out Annotation>

    private val metaTypes: List<Class<out Annotation>>

    private val annotation: Annotation?

    private val attributes: AttributeMethods

    private val mirrorSets: MirrorSets

    private val aliasMappings: IntArray

    private val conventionMappings: IntArray

    private val annotationValueMappings: IntArray

    private val annotationValueSource: Array<AnnotationTypeMapping?>

    private var synthesizable = false

    constructor(
        source: AnnotationTypeMapping?, annotationType: Class<out Annotation>,
        annotation: Annotation?, visitedAnnotationTypes: MutableSet<Class<out Annotation>>
    ) {
        this.source = source
        this.root = source?.getRoot() ?: this
        this.distance = if (source == null) 0 else source.getDistance() + 1
        this.annotationType = annotationType
        this.metaTypes = merge(
            source?.getMetaTypes(),
            annotationType
        )
        this.annotation = annotation
        this.attributes = AttributeMethods.forAnnotationType(annotationType)
        this.mirrorSets = MirrorSets()
        this.aliasMappings = filledIntArray(this.attributes.size())
        this.conventionMappings = filledIntArray(this.attributes.size())
        this.annotationValueMappings = filledIntArray(this.attributes.size())
        this.annotationValueSource = arrayOfNulls(this.attributes.size())
        addConventionMappings()
        addConventionAnnotationValues()
        this.synthesizable = computeSynthesizableFlag(visitedAnnotationTypes)
    }

    fun getAnnotationType(): Class<out Annotation> {
        return this.annotationType
    }

    fun getAnnotation(): Annotation? {
        return annotation
    }

    fun getMetaTypes(): List<Class<out Annotation>> {
        return metaTypes
    }

    fun getDistance(): Int {
        return this.distance
    }

    fun getSource(): AnnotationTypeMapping? {
        return source
    }

    fun getRoot(): AnnotationTypeMapping {
        return root
    }

    fun getMirrorSets(): MirrorSets {
        return this.mirrorSets
    }

    fun isSynthesizable(): Boolean {
        return synthesizable
    }

    fun getAttributes(): AttributeMethods {
        return attributes
    }

    fun afterAllMappingsSet() {
        for (i in 0 until mirrorSets.size()) {
            validateMirrorSet(mirrorSets[i])
        }
    }

    private fun validateMirrorSet(mirrorSet: MirrorSets.MirrorSet) {
        val firstAttribute: Method = mirrorSet.get(0)
        val firstDefaultValue = firstAttribute.defaultValue
        for (i in 1 until mirrorSet.size()) {
            val mirrorAttribute = mirrorSet[i]
            val mirrorDefaultValue = mirrorAttribute.defaultValue
            if (firstDefaultValue == null || mirrorDefaultValue == null) {
                throw AnnotationConfigurationException(
                    java.lang.String.format(
                        "Misconfigured aliases: %s and %s must declare default values.",
                        AttributeMethods.describe(firstAttribute), AttributeMethods.describe(mirrorAttribute)
                    )
                )
            }
            if (!ObjectUtil.nullSafeEquals(firstDefaultValue, mirrorDefaultValue)) {
                throw AnnotationConfigurationException(
                    java.lang.String.format(
                        "Misconfigured aliases: %s and %s must declare the same default value.",
                        AttributeMethods.describe(firstAttribute), AttributeMethods.describe(mirrorAttribute)
                    )
                )
            }
        }
    }

    private fun computeSynthesizableFlag(visitedAnnotationTypes: MutableSet<Class<out Annotation>>): Boolean {
        // Track that we have visited the current annotation type.
        visitedAnnotationTypes.add(annotationType)

        // Uses @AliasFor for local aliases?
        for (index in aliasMappings) {
            if (index != -1) {
                return true
            }
        }

        // Uses convention-based attribute overrides in meta-annotations?
        for (index in conventionMappings) {
            if (index != -1) {
                return true
            }
        }

        // Has nested annotations or arrays of annotations that are synthesizable?
        if (getAttributes().hasNestedAnnotation()) {
            val attributeMethods = getAttributes()
            for (i in 0 until attributeMethods.size()) {
                val method = attributeMethods[i]
                val type = method.returnType
                if (type.isAnnotation || type.isArray && type.componentType.isAnnotation) {
                    val annotationType = (if (type.isAnnotation) type else type.componentType) as Class<out Annotation?>
                    // Ensure we have not yet visited the current nested annotation type, in order
                    // to avoid infinite recursion for JVM languages other than Java that support
                    // recursive annotation definitions.
                    if (visitedAnnotationTypes.add(annotationType)) {
                        val mapping =
                            AnnotationTypeMappings.forAnnotationType(annotationType, visitedAnnotationTypes)[0]
                        if (mapping.isSynthesizable()) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    fun isEquivalentToDefaultValue(attributeIndex: Int, value: Any, valueExtractor: ValueExtractor): Boolean {
        val attribute = attributes[attributeIndex]
        return isEquivalentToDefaultValue(attribute, value, valueExtractor)
    }

    private fun addConventionMappings() {
        if (distance == 0) {
            return
        }
        val rootAttributes = this.root.getAttributes()
        val mappings = conventionMappings
        for (i in mappings.indices) {
            val name = attributes[i].name
            val mirrors = getMirrorSets().getAssigned(i)
            val mapped = rootAttributes.indexOf(name)
            if (MergedAnnotation.VALUE != name && mapped != -1) {
                mappings[i] = mapped
                if (mirrors != null) {
                    for (j in 0 until mirrors.size()) {
                        mappings[mirrors.getAttributeIndex(j)] = mapped
                    }
                }
            }
        }
    }

    fun getConventionMapping(attributeIndex: Int): Int {
        return conventionMappings[attributeIndex]
    }

    fun getMappedAnnotationValue(attributeIndex: Int, metaAnnotationsOnly: Boolean): Any? {
        val mappedIndex = annotationValueMappings[attributeIndex]
        if (mappedIndex == -1) {
            return null
        }
        val source = annotationValueSource[attributeIndex]!!
        return if (source == this && metaAnnotationsOnly) {
            null
        } else ReflectionUtil.invokeMethod(source.attributes[mappedIndex], source.annotation)
    }

    private fun addConventionAnnotationValues() {
        for (i in 0 until attributes.size()) {
            val attribute = attributes[i]
            val isValueAttribute = MergedAnnotation.VALUE == attribute.name
            var mapping: AnnotationTypeMapping? = this
            while (mapping != null && mapping.distance > 0) {
                val mapped = mapping.getAttributes().indexOf(attribute.name)
                if (mapped != -1 && isBetterConventionAnnotationValue(i, isValueAttribute, mapping)) {
                    annotationValueMappings[i] = mapped
                    annotationValueSource[i] = mapping
                }
                mapping = mapping.source
            }
        }
    }

    private fun isBetterConventionAnnotationValue(
        index: Int, isValueAttribute: Boolean,
        mapping: AnnotationTypeMapping
    ): Boolean {
        if (annotationValueMappings[index] == -1) {
            return true
        }
        val existingDistance = annotationValueSource[index]!!.distance
        return !isValueAttribute && existingDistance > mapping.distance
    }

    inner class MirrorSets {

        private var mirrorSets: Array<MirrorSet> = EMPTY_MIRROR_SETS

        private val assigned: Array<MirrorSet?> = arrayOfNulls(attributes.size())

        fun updateFrom(aliases: Collection<Method>) {
            var mirrorSet: MirrorSet? = null
            var size = 0
            var last = -1
            for (i in 0 until attributes.size()) {
                val attribute: Method = attributes[i]
                if (aliases.contains(attribute)) {
                    size++
                    if (size > 1) {
                        if (mirrorSet == null) {
                            mirrorSet = MirrorSet()
                            assigned[last] = mirrorSet
                        }
                        assigned[i] = mirrorSet
                    }
                    last = i
                }
            }
            if (mirrorSet != null) {
                mirrorSet.update()
                val unique = LinkedHashSet(listOf(*assigned))
                unique.remove(null)
                mirrorSets = unique.filterNotNull().toTypedArray()
            }
        }

        fun size(): Int {
            return mirrorSets.size
        }

        operator fun get(index: Int): MirrorSet {
            return mirrorSets[index]
        }

        fun getAssigned(attributeIndex: Int): MirrorSet? {
            return assigned[attributeIndex]
        }

        fun resolve(source: Any?, annotation: Any?, valueExtractor: ValueExtractor): IntArray {
            val result = IntArray(attributes.size())
            for (i in result.indices) {
                result[i] = i
            }
            for (i in 0 until size()) {
                val mirrorSet = get(i)
                val resolved = mirrorSet.resolve(source, annotation, valueExtractor)
                for (j in 0 until mirrorSet.size) {
                    result[mirrorSet.indexes[j]] = resolved
                }
            }
            return result
        }

        inner class MirrorSet {

            var size = 0

            val indexes = IntArray(attributes.size())

            fun update() {
                size = 0
                Arrays.fill(indexes, -1)
                for (i in assigned.indices) {
                    if (assigned[i] === this) {
                        indexes[size] = i
                        size++
                    }
                }
            }

            fun <A> resolve(source: Any?, annotation: A?, valueExtractor: ValueExtractor): Int {
                var result = -1
                var lastValue: Any? = null
                for (i in 0 until size) {
                    val attribute: Method = attributes.get(indexes[i])
                    val value = valueExtractor.extract(attribute, annotation)
                    val isDefaultValue = value == null ||
                            isEquivalentToDefaultValue(attribute, value, valueExtractor)
                    if (isDefaultValue || ObjectUtil.nullSafeEquals(lastValue, value)) {
                        if (result == -1) {
                            result = indexes[i]
                        }
                        continue
                    }
                    if (lastValue != null && !ObjectUtil.nullSafeEquals(lastValue, value)) {
                        val on = if (source != null) " declared on $source" else ""
                        throw AnnotationConfigurationException(
                            String.format(
                                "Different @AliasFor mirror values for annotation [%s]%s; attribute '%s' " +
                                        "and its alias '%s' are declared with values of [%s] and [%s].",
                                getAnnotationType().name, on,
                                attributes.get(result).name,
                                attribute.name,
                                ObjectUtil.nullSafeToString(lastValue),
                                ObjectUtil.nullSafeToString(value)
                            )
                        )
                    }
                    result = indexes[i]
                    lastValue = value
                }
                return result
            }

            fun size(): Int {
                return size
            }

            operator fun get(index: Int): Method {
                val attributeIndex = indexes[index]
                return attributes[attributeIndex]
            }

            fun getAttributeIndex(index: Int): Int {
                return indexes[index]
            }
        }
    }

}